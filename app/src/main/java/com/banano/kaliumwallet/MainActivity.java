package com.banano.kaliumwallet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import com.banano.kaliumwallet.bus.Logout;
import com.banano.kaliumwallet.bus.OpenWebView;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.bus.SeedCreatedWithAnotherWallet;
import com.banano.kaliumwallet.di.activity.ActivityComponent;
import com.banano.kaliumwallet.di.activity.ActivityModule;
import com.banano.kaliumwallet.di.activity.DaggerActivityComponent;
import com.banano.kaliumwallet.di.application.ApplicationComponent;
import com.banano.kaliumwallet.model.AvailableLanguage;
import com.banano.kaliumwallet.model.Contact;
import com.banano.kaliumwallet.model.Credentials;
import com.banano.kaliumwallet.model.KaliumWallet;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.ui.common.ActivityFragmentBackButtonInterface;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.FragmentOnBackListener;
import com.banano.kaliumwallet.ui.common.FragmentUtility;
import com.banano.kaliumwallet.ui.common.WindowControl;
import com.banano.kaliumwallet.ui.home.HomeFragment;
import com.banano.kaliumwallet.ui.intro.IntroNewWalletFragment;
import com.banano.kaliumwallet.ui.intro.IntroWelcomeFragment;
import com.banano.kaliumwallet.ui.webview.WebViewDialogFragment;
import com.banano.kaliumwallet.util.SharedPreferencesUtil;
import com.hwangjr.rxbus.annotation.Subscribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements WindowControl, ActivityWithComponent, ActivityFragmentBackButtonInterface {
    protected ActivityComponent mActivityComponent;

    private ArrayList<WeakReference<FragmentOnBackListener>> backClickListenersList = new ArrayList<>();

    @Inject
    Realm realm;
    @Inject
    AccountService accountService;
    @Inject
    KaliumWallet nanoWallet;
    @Inject
    SharedPreferencesUtil sharedPreferencesUtil;
    private FragmentUtility mFragmentUtility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        clearNotificationPrefCache();

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // build the activity component
        mActivityComponent = DaggerActivityComponent
                .builder()
                .applicationComponent(KaliumApplication.getApplication(this).getApplicationComponent())
                .activityModule(new ActivityModule(this))
                .build();

        // perform dagger injections
        mActivityComponent.inject(this);

        // subscribe to bus
        RxBus.get().register(this);

        // set unique uuid (per app install)
        if (!sharedPreferencesUtil.hasAppInstallUuid()) {
            sharedPreferencesUtil.setAppInstallUuid(UUID.randomUUID().toString());
        }

        // Set default system locale to shared prefs
        sharedPreferencesUtil.setDefaultLocale(Locale.getDefault());

        // Set default language
        if (sharedPreferencesUtil.getLanguage() != AvailableLanguage.DEFAULT) {
            Locale locale = new Locale(sharedPreferencesUtil.getLanguage().getLocaleString());
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext().getResources().updateConfiguration(config,
                    getBaseContext().getResources().getDisplayMetrics());
        }

        if (!sharedPreferencesUtil.isDefaultContactAdded()) {
            realm.executeTransaction(realm -> {
                Contact newContact = realm.createObject(Contact.class, "ban_1ka1ium4pfue3uxtntqsrib8mumxgazsjf58gidh1xeo5te3whsq8z476goo");
                newContact.setName("@KaliumDonations");
                try {
                    File heisenberg = moveHeisenbergFromAssets();
                    if (heisenberg != null && heisenberg.exists()) {
                        newContact.setMonkeyPath(heisenberg.getAbsolutePath());
                    }
                } catch (IOException e) {
                    Timber.e(e);
                }
            });
            sharedPreferencesUtil.setDefaultContactAdded();
        }

        // Set app in foreground
        sharedPreferencesUtil.setAppBackgrounded(false);

        // Send URI data to fragment
        initUi(getIntent().getData() == null ? null : getIntent().getData().toString());
    }

    private File moveHeisenbergFromAssets() throws IOException {
        File heisenberg = new File(getFilesDir(), "ban_1ka1ium4pfue3uxtntqsrib8mumxgazsjf58gidh1xeo5te3whsq8z476goo.svg");
        try (InputStream inputStream = getAssets().open("heisenberg.svg");
             FileOutputStream outputStream = new FileOutputStream(heisenberg)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
        } catch (IOException e) {
            throw new IOException("Could not open heisenbergsvg", e);
        }
        return heisenberg;
    }

    private void clearNotificationPrefCache() {
        SharedPreferences sharedPreferences = getSharedPreferences("NotificationData", 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Set app in foreground
        sharedPreferencesUtil.setAppBackgrounded(true);
        // stop websocket on pause
        if (accountService != null) {
            accountService.close();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set app in foreground
        sharedPreferencesUtil.setAppBackgrounded(false);
        clearNotificationPrefCache();
        // start websocket on resume
        if (accountService != null && realm != null && !realm.isClosed() && realm.where(Credentials.class).findFirst() != null) {
            accountService.open();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Set app in foreground
        sharedPreferencesUtil.setAppBackgrounded(true);

        // unregister from bus
        RxBus.get().unregister(this);

        // close realm connection
        if (realm != null) {
            realm.close();
            realm = null;
        }

        // close wallet so app can clean up
        if (nanoWallet != null) {
            nanoWallet.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void initUi(String uri) {
        // set main content view
        setContentView(R.layout.activity_main);

        // create fragment utility instance
        mFragmentUtility = new FragmentUtility(getSupportFragmentManager());
        mFragmentUtility.setContainerViewId(R.id.container);

        // get wallet seed if it exists
        Credentials credentials = realm.where(Credentials.class).findFirst();

        if (credentials == null) {
            // if we don't have a wallet, start the intro
            mFragmentUtility.clearStack();
            mFragmentUtility.replace(new IntroWelcomeFragment());
        } else {
            mFragmentUtility.clearStack();
            if (sharedPreferencesUtil.getConfirmedSeedBackedUp()) {
                // go to home screen
                mFragmentUtility.replace(HomeFragment.newInstance(uri));
            } else {
                // go to intro new wallet
                mFragmentUtility.replace(IntroNewWalletFragment.newInstance(true));
            }
        }
    }

    @Subscribe
    public void logOut(Logout logout) {
        // delete user seed data before logging out
        final RealmResults<Credentials> results = realm.where(Credentials.class).findAll();
        realm.executeTransaction(realm1 -> results.deleteAllFromRealm());

        // stop the websocket
        accountService.close();

        // clear wallet
        nanoWallet.clear();

        // null out component
        mActivityComponent = null;

        sharedPreferencesUtil.setConfirmedSeedBackedUp(false);
        sharedPreferencesUtil.setFromNewWallet(false);

        // go to the welcome fragment
        getFragmentUtility().clearStack();
        getFragmentUtility().replace(new IntroWelcomeFragment(), FragmentUtility.Animation.CROSSFADE);
    }

    @Subscribe
    public void openWebView(OpenWebView openWebView) {
        WebViewDialogFragment
                .newInstance(openWebView.getUrl(), openWebView.getTitle() != null ? openWebView.getTitle() : "")
                .show(getFragmentUtility().getFragmentManager(), WebViewDialogFragment.TAG);
    }

    @Subscribe
    public void seedCreatedWithAnotherWallet(SeedCreatedWithAnotherWallet seedCreatedWithAnotherWallet) {
        realm.executeTransaction(realm -> {
            Credentials credentials = realm.where(Credentials.class).findFirst();
            if (credentials != null) {
                credentials.setSeedIsSecure(true);
            }
        });
    }

    @Override
    public FragmentUtility getFragmentUtility() {
        return mFragmentUtility;
    }


    /**
     * Set the status bar to a particular color
     *
     * @param color color resource id
     */
    @Override
    public void setStatusBarColor(int color) {
        // we can only set it 5.x and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, color));
        }
    }

    @Override
    public ActivityComponent getActivityComponent() {
        if (mActivityComponent == null) {
            // build the activity component
            mActivityComponent = DaggerActivityComponent
                    .builder()
                    .applicationComponent(KaliumApplication.getApplication(this).getApplicationComponent())
                    .activityModule(new ActivityModule(this))
                    .build();
        }
        return mActivityComponent;
    }

    @Override
    public ApplicationComponent getApplicationComponent() {
        return KaliumApplication.getApplication(this).getApplicationComponent();
    }

    // Handle fragments overriding back button presses
    @Override
    public void addBackClickListener(FragmentOnBackListener onBackClickListener) {
        backClickListenersList.add(new WeakReference<>(onBackClickListener));
    }

    @Override
    public void removeBackClickListener(FragmentOnBackListener onBackClickListener) {
        for (Iterator<WeakReference<FragmentOnBackListener>> iterator = backClickListenersList.iterator();
             iterator.hasNext();){
            WeakReference<FragmentOnBackListener> weakRef = iterator.next();
            if (weakRef.get() == onBackClickListener){
                iterator.remove();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if(!fragmentsBackKeyIntercept()){
            super.onBackPressed();
        }
    }

    private boolean fragmentsBackKeyIntercept() {
        boolean isIntercept = false;
        for (WeakReference<FragmentOnBackListener> weakRef : backClickListenersList) {
            FragmentOnBackListener onBackClickListener = weakRef.get();
            if (onBackClickListener != null) {
                boolean isFragmIntercept = onBackClickListener.onBackClick();
                if (!isIntercept) isIntercept = isFragmIntercept;
            }
        }
        return isIntercept;
    }
}
