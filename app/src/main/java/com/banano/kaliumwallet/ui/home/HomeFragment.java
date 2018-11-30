package com.banano.kaliumwallet.ui.home;

import android.app.Activity;
import android.content.Context;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;
import androidx.databinding.DataBindingUtil;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.customview.widget.ViewDragHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.bus.ContactAdded;
import com.banano.kaliumwallet.bus.ContactRemoved;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.bus.SocketError;
import com.banano.kaliumwallet.bus.TransactionItemClicked;
import com.banano.kaliumwallet.bus.WalletHistoryUpdate;
import com.banano.kaliumwallet.bus.WalletPriceUpdate;
import com.banano.kaliumwallet.bus.WalletSubscribeUpdate;
import com.banano.kaliumwallet.databinding.FragmentHomeBinding;
import com.banano.kaliumwallet.model.Address;
import com.banano.kaliumwallet.model.Contact;
import com.banano.kaliumwallet.model.Credentials;
import com.banano.kaliumwallet.model.KaliumWallet;
import com.banano.kaliumwallet.model.PriceConversion;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.network.model.response.AccountCheckResponse;
import com.banano.kaliumwallet.network.model.response.AccountHistoryResponseItem;
import com.banano.kaliumwallet.task.DownloadOrRetrieveFileTask;
import com.banano.kaliumwallet.ui.common.ActivityFragmentBackButtonInterface;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseFragment;
import com.banano.kaliumwallet.ui.common.FragmentOnBackListener;
import com.banano.kaliumwallet.ui.common.FragmentUtility;
import com.banano.kaliumwallet.ui.common.KeyboardUtil;
import com.banano.kaliumwallet.ui.common.UIUtil;
import com.banano.kaliumwallet.ui.common.WindowControl;
import com.banano.kaliumwallet.ui.contact.ContactOverviewFragment;
import com.banano.kaliumwallet.ui.receive.ReceiveDialogFragment;
import com.banano.kaliumwallet.ui.send.SendDialogFragment;
import com.banano.kaliumwallet.ui.settings.SettingsFragment;
import com.banano.kaliumwallet.util.SharedPreferencesUtil;
import com.banano.kaliumwallet.util.svg.SvgSoftwareLayerSetter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.hwangjr.rxbus.annotation.Subscribe;

import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import io.realm.Realm;
import io.realm.RealmQuery;
import timber.log.Timber;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Home Wallet Screen
 */

@BindingMethods({
        @BindingMethod(type = androidx.appcompat.widget.AppCompatImageView.class,
                attribute = "srcCompat",
                method = "setImageDrawable")
})
public class HomeFragment extends BaseFragment implements FragmentOnBackListener {
    public static String TAG = HomeFragment.class.getSimpleName();

    public boolean retrying = false;

    @Inject
    AccountService accountService;
    @Inject
    KaliumWallet wallet;
    @Inject
    Realm realm;
    @Inject
    SharedPreferencesUtil sharedPreferencesUtil;

    private ActivityFragmentBackButtonInterface backButtonHandler;
    private FragmentHomeBinding binding;
    private DownloadOrRetrieveFileTask downloadMonkeyTask;
    private Handler mHandler;
    private Runnable mRunnable;
    private HashMap<String, String> mContactCache = new HashMap<>();
    private AccountHistoryAdapter mAdapter;

    /**
     * Create new instance of the fragment (handy pattern if any data needs to be passed to it)
     *
     * @return HomeFragment
     */
    public static HomeFragment newInstance() {
        Bundle args = new Bundle();
        HomeFragment fragment = new HomeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity;
        if (context instanceof Activity){
            activity = (Activity) context;
            backButtonHandler = (ActivityFragmentBackButtonInterface) activity;
            backButtonHandler.addBackClickListener(this);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        backButtonHandler.removeBackClickListener(this);
        backButtonHandler = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //inflater.inflate(R.menu.menu_home, menu);
        //super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onDestroyView() {
        // Prevent leak after fragment destroy
        if (downloadMonkeyTask != null) {
            downloadMonkeyTask.setListener(null);
        }
        super.onDestroyView();
        // unregister from bus
        RxBus.get().unregister(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // init dependency injection
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }
        retrying = false;

        // subscribe to bus
        RxBus.get().register(this);

        // set status bar color
        setStatusBarGray();

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_home, container, false);
        view = binding.getRoot();

        // Currency bindings
        binding.setWallet(wallet);
        binding.setLocalCurrency(wallet.getLocalCurrency());
        binding.setHandlers(new ClickHandlers());

        // hide keyboard
        KeyboardUtil.hideKeyboard(getActivity());

        // initialize recyclerview (list of wallet transactions)
        binding.homeRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new AccountHistoryAdapter(new ArrayList<>());
        binding.homeRecyclerview.setAdapter(mAdapter);
        mRunnable = () -> {
            retrying = false;
            binding.homeSwiperefresh.setRefreshing(false);
        };
        mHandler = new Handler();
        binding.homeSwiperefresh.setOnRefreshListener(() -> {
            if (!retrying) {
                accountService.requestUpdate();
                if (mHandler != null && mRunnable != null) {
                    mHandler.removeCallbacks(mRunnable);
                    mHandler.postDelayed(mRunnable, 5000);
                }
            }
        });

        // Initialize transaction history
        if (wallet != null && wallet.getAccountHistory() != null) {
            if (wallet.getAccountHistory().size() > 0) {
                binding.loadingAnimation.setVisibility(View.GONE);
            }
            updateAccountHistory();
        }

        Credentials credentials = realm.where(Credentials.class).findFirst();

        // Retrieve/populate monKey
        if (credentials != null) {
            // Get monKey
            if (credentials.getAddressString() != null) {
                // Download monKey if doesn't exist
                String url = getString(R.string.monkey_api_url, credentials.getAddressString());
                downloadMonkeyTask = new DownloadOrRetrieveFileTask(getContext().getFilesDir());
                downloadMonkeyTask.setListener((List<File> monkeys) -> {
                    if (monkeys == null || monkeys.isEmpty()) {
                        return;
                    }
                    RequestBuilder<PictureDrawable> requestBuilder;
                    requestBuilder = Glide.with(getContext())
                            .as(PictureDrawable.class)
                            .transition(withCrossFade())
                            .listener(new SvgSoftwareLayerSetter());
                    for (File f: monkeys) {
                        try {
                            Uri svgUri = Uri.fromFile(f);
                            binding.homeMonkey.setVisibility(View.VISIBLE);
                            requestBuilder.load(svgUri).into(binding.homeMonkey);
                            requestBuilder.load(svgUri).into(binding.monkeyOverlayImg);
                            break;
                        } catch (Exception e) {
                            Timber.e("Failed to load monKey file");
                            e.printStackTrace();
                            if (f.exists()) {
                                f.delete();
                            }
                        }
                    }
                });
                downloadMonkeyTask.execute(url);
            }
        }

        // Hack for easier settings access https://stackoverflow.com/questions/17942223/drawerlayout-modify-sensitivity
        // assuming mDrawerLayout is an instance of android.support.v4.widget.DrawerLayout
        try {

            // get dragger responsible for the dragging of the left drawer
            Field draggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
            draggerField.setAccessible(true);
            ViewDragHelper vdh = (ViewDragHelper) draggerField.get(binding.drawerLayout);

            // get access to the private field which defines
            // how far from the edge dragging can start
            Field edgeSizeField = ViewDragHelper.class.getDeclaredField("mEdgeSize");
            edgeSizeField.setAccessible(true);

            // increase the edge size - while x2 should be good enough,
            // try bigger values to easily see the difference
            int origEdgeSize = (int) edgeSizeField.get(vdh);
            int newEdgeSize = origEdgeSize * 5;
            edgeSizeField.setInt(vdh, newEdgeSize);

        } catch (Exception e) {
            // we unexpectedly failed - e.g. if internal implementation of
            // either ViewDragHelper or DrawerLayout changed
        }

        // Change status bar color when drawer open
        binding.drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View view, float v) {
                if (v > 0.5) {
                    setStatusBarDarkGray();
                } else {
                    setStatusBarGray();
                }
            }

            @Override
            public void onDrawerOpened(@NonNull View view) {
                setStatusBarDarkGray();
            }

            @Override
            public void onDrawerClosed(@NonNull View view) {
                setStatusBarGray();
                // Close contacts if open
                Fragment contactOverviewFragment = ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().findFragmentByTag(ContactOverviewFragment.TAG);
                if (contactOverviewFragment != null) {
                    FragmentUtility.disableFragmentAnimations = true;
                    ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().popBackStackImmediate();
                    ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
                    FragmentUtility.disableFragmentAnimations = false;
                }
            }

            @Override
            public void onDrawerStateChanged(int i) {

            }
        });

        // Add sttings fragment to drawer container
        FragmentTransaction ft = ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().beginTransaction();
        ft.replace(R.id.settings_frag_container, SettingsFragment.newInstance()).commit();

        return view;
    }

    @Override
    public boolean onBackClick() {
        if (binding.monkeyOverlay.getVisibility() == View.VISIBLE) {
            // Close monKey if open
            hideMonkeyOverlay();
            return true;
        }
        return false;
    }
    private String getContactName(String address) {
        if (mContactCache.containsKey(address)) {
            return mContactCache.get(address);
        }
        RealmQuery realmQuery = realm.where(Contact.class);
        realmQuery.equalTo("address", address);
        if (realmQuery.count() > 0) {
            Contact c = (Contact) realmQuery.findFirst();
            mContactCache.put(address, c.getDisplayName());
            return c.getDisplayName();
        }
        return null;
    }

    private void updateAccountHistory() {
        List<AccountHistoryResponseItem> historyList = wallet.getAccountHistory();
        for (AccountHistoryResponseItem item : historyList) {
            String name = getContactName(item.getAccount());
            if (name != null) {
                item.setContactName(name);
            } else {
                item.setContactName(null);
            }
        }
        mAdapter.updateList(historyList);
        binding.homeRecyclerview.getLayoutManager().scrollToPosition(0);
    }

    @Subscribe
    public void receiveContactAdded(ContactAdded contactAdded) {
        if (mAdapter == null || getContext() == null) {
            return;
        }
        updateAccountHistory();
        getActivity().runOnUiThread(() -> {
            mAdapter.notifyDataSetChanged();
        });
        UIUtil.showToast(getString(R.string.contact_added, contactAdded.getName()), getContext());
        // Download monKey in background, try to have it available for later
        if (contactAdded.getAddress() != null) {
            String url = getString(R.string.monkey_api_url, contactAdded.getAddress());
            downloadMonkeyTask = new DownloadOrRetrieveFileTask(getContext().getFilesDir());
            downloadMonkeyTask.setListener((List<File> monkeys) -> {
                if (monkeys == null || monkeys.isEmpty()) {
                    return;
                }
                for (File f: monkeys) {
                    if (f.exists()) {
                        String address = Address.findAddress(f.getAbsolutePath());
                        if (address != null && !address.isEmpty()) {
                            realm.executeTransaction(realm -> {
                                Contact c = realm.where(Contact.class).equalTo("address", address).findFirst();
                                c.setMonkeyPath(f.getAbsolutePath());
                            });
                        }
                    }
                }
            });
            downloadMonkeyTask.execute(url);
        }
    }

    @Subscribe
    public void receiveContactRemoved(ContactRemoved contactRemoved) {
        if (mContactCache == null || mAdapter == null || getContext() == null) {
            return;
        }
        if (mContactCache.containsValue(contactRemoved.getName())) {
            mContactCache.remove(contactRemoved.getAddress());
        }
        updateAccountHistory();
        getActivity().runOnUiThread(() -> {
            mAdapter.notifyDataSetChanged();
        });
        UIUtil.showToast(getString(R.string.contact_removed, contactRemoved.getName()), getContext());
    }

    @Subscribe
    public void receiveHistory(WalletHistoryUpdate walletHistoryUpdate) {
        if (wallet == null || binding == null || mAdapter == null || getContext() == null) {
            return;
        }
        binding.loadingAnimation.setVisibility(View.GONE);
        if (wallet.getAccountHistory().size() > 0) {
            binding.exampleCards.setVisibility(View.GONE);
        }
        updateAccountHistory();
        binding.homeSwiperefresh.setRefreshing(false);
    }

    @Subscribe
    public void receivePrice(WalletPriceUpdate walletPriceUpdate) {
        updateAmounts();
    }

    @Subscribe
    public void receiveSubscribe(WalletSubscribeUpdate walletSubscribeUpdate) {
        updateAmounts();
        if (wallet.getOpenBlock() == null) {
            binding.introText.exampleIntroText.setText(UIUtil.colorizeBanano(binding.introText.exampleIntroText.getText().toString(), getContext()));
            binding.loadingAnimation.setVisibility(View.GONE);
            binding.exampleCards.setVisibility(View.VISIBLE);
        }
    }

    @Subscribe
    public void receiveAccountCheck(AccountCheckResponse accountCheckResponse) {
        if (accountCheckResponse.getReady()) {
            // account is on the network, so send a pending request
            accountService.requestPending();
        }
    }

    @Subscribe
    public void receiveError(SocketError error) {
        binding.loadingAnimation.setVisibility(View.GONE);
        binding.homeSwiperefresh.setRefreshing(false);
        // Retry refresh
        if (!retrying) {
            retrying = true;
            accountService.requestUpdate();
            if (mHandler != null && mRunnable != null) {
                mHandler.removeCallbacks(mRunnable);
                mHandler.postDelayed(mRunnable, 5000);
            }
        } else {
            retrying = false;
        }
    }

    @Subscribe
    public void receiveTranItemClick(TransactionItemClicked tran) {
        // show details dialog
        if (getActivity() instanceof WindowControl) {
            TranDetailsFragment dialog = TranDetailsFragment.newInstance(tran.getHash(), tran.getAccount());
            dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                    TranDetailsFragment.TAG);

            ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
        }
    }

    private void updateAmounts() {
        if (wallet != null) {
            binding.setWallet(wallet);
            if (wallet.getAccountBalanceBananoRaw() != null &&
                    wallet.getAccountBalanceBananoRaw().compareTo(new BigDecimal(0)) == 1) {
                // if balance > 0, enable send button
                binding.homeSendButton.setEnabled(true);
                binding.homeSendButton.setBackground(getResources().getDrawable(R.drawable.bg_solid_button));
                // Hide placeholder
                binding.bananoPlaceholder.setVisibility(View.GONE);
                binding.amountBananoSymbol.setVisibility(View.VISIBLE);
                binding.amountBananoTitle.setVisibility(View.VISIBLE);
                // Set default price
                switch (sharedPreferencesUtil.getPriceConversion()) {
                    case BTC:
                        binding.btcPrice.setVisibility(View.VISIBLE);
                        binding.amountLocalCurrencyTitle.setVisibility(View.VISIBLE);
                        binding.nanoPrice.setVisibility(View.GONE);
                        break;
                    case NANO:
                        binding.nanoPrice.setVisibility(View.VISIBLE);
                        binding.amountLocalCurrencyTitle.setVisibility(View.VISIBLE);
                        binding.btcPrice.setVisibility(View.GONE);
                        break;
                    default:
                        binding.nanoPrice.setVisibility(View.GONE);
                        binding.btcPrice.setVisibility(View.GONE);
                        binding.amountLocalCurrencyTitle.setVisibility(View.GONE);
                        break;
                }
                // Tweak sizing based on how big amount is
                String balBanano = wallet.getAccountBalanceBanano();
                binding.amountBananoTitle.setText(balBanano);
                if (balBanano.length() >= 9 && balBanano.length() < 12) {
                    binding.amountBananoTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.main_balance_md_text));
                } else if (balBanano.length() >= 12) {
                    binding.amountBananoTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.main_balance_sm_text));
                } else {
                    binding.amountBananoTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.main_balance_lg_text));
                }
            } else {
                binding.homeSendButton.setEnabled(false);
                binding.homeSendButton.setBackground(getResources().getDrawable(R.drawable.bg_solid_button_disabled));
            }
        }
    }

    private void showMonkeyOverlay() {
        animateView(binding.homeMonkey, View.GONE, 0, 200);
        animateView(binding.monkeyOverlay, View.VISIBLE, 1.0f, 200);
    }

    private void hideMonkeyOverlay() {
        animateView(binding.monkeyOverlay, View.GONE, 0, 200);
        animateView(binding.homeMonkey, View.VISIBLE, 1.0f, 200);
    }

    public class ClickHandlers {
        public void onClickReceive(View view) {
            if (getActivity() instanceof WindowControl) {
                // show receive dialog
                ReceiveDialogFragment dialog = ReceiveDialogFragment.newInstance();
                dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                        ReceiveDialogFragment.TAG);
                executePendingTransactions();
            }
        }

        public void onClickSend(View view) {
            if (getActivity() instanceof WindowControl) {
                // show send dialog
                SendDialogFragment dialog = SendDialogFragment.newInstance(null);
                dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                        SendDialogFragment.TAG);

                executePendingTransactions();
            }
        }

        public void onClickSettings(View view) {
            binding.drawerLayout.openDrawer(Gravity.START);
        }

        public void onClickMonkey(View view) {
            showMonkeyOverlay();
        }

        public void onClickMonkeyFrame(View view) {
            hideMonkeyOverlay();
        }

        public void onClickPrice(View view) {
            if (sharedPreferencesUtil.getPriceConversion() == PriceConversion.BTC) {
                // Switch to NANO
                binding.nanoPrice.setVisibility(View.VISIBLE);
                binding.btcPrice.setVisibility(View.GONE);
                binding.amountLocalCurrencyTitle.setVisibility(View.VISIBLE);
                sharedPreferencesUtil.setPriceConversion(PriceConversion.NANO);
            } else if (sharedPreferencesUtil.getPriceConversion() == PriceConversion.NANO) {
                // Switch to NONE
                binding.nanoPrice.setVisibility(View.GONE);
                binding.btcPrice.setVisibility(View.GONE);
                binding.amountLocalCurrencyTitle.setVisibility(View.GONE);
                sharedPreferencesUtil.setPriceConversion(PriceConversion.NONE);
            } else {
                // Switch to BTC
                binding.nanoPrice.setVisibility(View.GONE);
                binding.btcPrice.setVisibility(View.VISIBLE);
                binding.amountLocalCurrencyTitle.setVisibility(View.VISIBLE);
                sharedPreferencesUtil.setPriceConversion(PriceConversion.BTC);
            }
        }

        /**
         * Execute all pending transactions
         */
        private void executePendingTransactions() {
            ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
        }
    }
}
