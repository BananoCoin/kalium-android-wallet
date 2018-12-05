package com.banano.kaliumwallet.ui.transfer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.banano.kaliumwallet.KaliumUtil;
import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.databinding.FragmentTransferBinding;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.network.model.response.AccountBalanceItem;
import com.banano.kaliumwallet.network.model.response.AccountsBalancesResponse;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.banano.kaliumwallet.ui.common.UIUtil;
import com.banano.kaliumwallet.ui.scan.ScanActivity;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.rotilho.jnano.commons.NanoAmount;
import com.rotilho.jnano.commons.NanoHelper;
import com.rotilho.jnano.commons.NanoSeeds;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import static android.app.Activity.RESULT_OK;

/**
 * Initial Transfer Screen
 */
public class TransferIntroDialogFragment extends BaseDialogFragment {
    public static String TAG = TransferIntroDialogFragment.class.getSimpleName();
    private static final int NUM_SWEEP = 15; // Number of accounts to derive/sweep from a seed

    @Inject
    AccountService accountService;

    private FragmentTransferBinding binding;

    private HashMap<String, String> accountPrivkeyMap = new HashMap<>();

    /**
     * Create new instance of the dialog fragment (handy pattern if any data needs to be passed to it)
     *
     * @return New instance of ChangeRepDialogFragment
     */
    public static TransferIntroDialogFragment newInstance() {
        Bundle args = new Bundle();
        TransferIntroDialogFragment fragment = new TransferIntroDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.AppTheme_Modal_Window);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // inject
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_transfer, container, false);
        view = binding.getRoot();


        // Restrict height
        Window window = getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, UIUtil.getDialogHeight(false, getContext()));
        window.setGravity(Gravity.BOTTOM);

        // Shadow
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.dimAmount = 0.60f;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(windowParams);

        // Swipe down to dismiss
        getDialog().getWindow().getDecorView().setOnTouchListener(new SwipeDismissTouchListener(getDialog().getWindow().getDecorView(),
                null, new SwipeDismissTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss(Object token) {
                return true;
            }

            @Override
            public void onDismiss(View view, Object token) {
                dismiss();
            }

            @Override
            public void onTap(View v) {
            }
        }, SwipeDismissTouchListener.TOP_TO_BOTTOM));

        // Set values
        binding.setHandlers(new ClickHandlers());

        binding.transferDescription.setText(getString(R.string.transfer_intro, getString(R.string.send_scan_qr)));

        // subscribe to bus
        RxBus.get().register(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // unregister from bus
        RxBus.get().unregister(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_RESULT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Bundle res = data.getExtras();
                if (res != null) {
                    String result = res.getString(ScanActivity.QR_CODE_RESULT);

                    String privKey;
                    String account;
                    if (NanoSeeds.isValid(NanoHelper.toByteArray(result))) {
                        for (int i = 0; i < NUM_SWEEP; i++) {
                            privKey = KaliumUtil.seedToPrivate(result, i);
                            account = KaliumUtil.publicToAddress(KaliumUtil.privateToPublic(privKey));
                            accountPrivkeyMap.put(account, privKey);
                        }
                        // Also put the seed itself as a private key, in case thats the intention
                        accountPrivkeyMap.put(result, KaliumUtil.publicToAddress(KaliumUtil.privateToPublic(result)));
                        // Make account balances request
                        List<String> accountsToRequest = new ArrayList<>();
                        accountService.requestAccountsBalances(accountsToRequest);
                    }
                }
            }
        }
    }

    @Subscribe
    public void onAccountBalancesResponse(AccountsBalancesResponse accountsBalancesResponse) {
        HashMap<String, AccountBalanceItem> accountBalances = accountsBalancesResponse.getBalances();
        for (Map.Entry<String, AccountBalanceItem> item : accountBalances.entrySet()) {
            AccountBalanceItem balances = item.getValue();
            String account = item.getKey();
            BigInteger balance = new BigInteger(balances.getBalance());
            BigInteger pending = new BigInteger(balances.getPending());
            if (balance.add(pending).equals(BigInteger.ZERO)) {
                accountPrivkeyMap.remove(account);
            }
        }
    }

    public class ClickHandlers {
        public void onClickClose(View view) {
            dismiss();
        }

        public void onClickScan(View view) {
            startScanActivity(getString(R.string.transfer_qr_scan_hint), true);
        }
    }
}
