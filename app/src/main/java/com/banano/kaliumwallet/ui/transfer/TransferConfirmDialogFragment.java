package com.banano.kaliumwallet.ui.transfer;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.bus.TransferHistoryResponse;
import com.banano.kaliumwallet.databinding.FragmentTransferConfirmBinding;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.network.model.response.AccountBalanceItem;
import com.banano.kaliumwallet.network.model.response.AccountHistoryResponse;
import com.banano.kaliumwallet.network.model.response.PendingTransactionResponse;
import com.banano.kaliumwallet.network.model.response.PendingTransactionResponseItem;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.banano.kaliumwallet.ui.common.UIUtil;
import com.banano.kaliumwallet.util.NumberUtil;
import com.hwangjr.rxbus.annotation.Subscribe;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

/**
 * Initial Transfer Screen
 */
public class TransferConfirmDialogFragment extends BaseDialogFragment {
    public static String TAG = TransferConfirmDialogFragment.class.getSimpleName();

    private FragmentTransferConfirmBinding binding;

    @Inject
    AccountService accountService;

    HashMap<String, AccountBalanceItem> rawInMap;
    HashMap<String, AccountBalanceItem> readyToSendMap;

    /**
     * Create new instance of the dialog fragment (handy pattern if any data needs to be passed to it)
     *
     * @return New instance of ChangeRepDialogFragment
     */
    public static TransferConfirmDialogFragment newInstance(HashMap<String, AccountBalanceItem> privKeyMap) {
        Bundle args = new Bundle();
        args.putSerializable("PRIVKEYMAP", privKeyMap);
        TransferConfirmDialogFragment fragment = new TransferConfirmDialogFragment();
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
                inflater, R.layout.fragment_transfer_confirm, container, false);
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

        // subscribe to bus
        RxBus.get().register(this);

        // Determine how much is here and sum it up
        // TODO avoid NPE and other exceptions by showing error messages
        readyToSendMap = new HashMap<>();
        if (getArguments().getSerializable("PRIVKEYMAP") != null) {
            rawInMap = (HashMap<String, AccountBalanceItem>) getArguments().getSerializable("PRIVKEYMAP");
        }
        BigInteger totalSum = new BigInteger("0");
        for (Map.Entry<String, AccountBalanceItem> item : rawInMap.entrySet()) {
            AccountBalanceItem balances = item.getValue();
            BigInteger balance = new BigInteger(balances.getBalance());
            BigInteger pending = new BigInteger(balances.getPending());
            totalSum = totalSum.add(balance).add(pending);
            // If there's no pending here then we don't need to run a pocket/open routine
            if (pending.equals(BigInteger.ZERO) && balance.compareTo(BigInteger.ZERO) > 0) {
                readyToSendMap.put(item.getKey(), balances);
                rawInMap.remove(item.getKey());
            } else if (pending.equals(BigInteger.ZERO) && balance.equals(BigInteger.ZERO)) {
                rawInMap.remove(item.getKey());
            }
        }
        String totalAsReadable = NumberUtil.getRawAsUsableString(totalSum.toString());

        binding.transferConfirmOne.setText(getString(R.string.transfer_confirm_info_first, totalAsReadable));

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // unregister from bus
        RxBus.get().unregister(this);
    }

    private void showLoadingOverlay() {
        if (binding != null && binding.progressOverlay != null) {
            binding.transferConfirm.setEnabled(false);
            binding.transferCancel.setEnabled(false);
            animateView(binding.progressOverlay, View.VISIBLE, 1, 200);
            // Darken window further
            Window window = getDialog().getWindow();
            WindowManager.LayoutParams windowParams = window.getAttributes();
            windowParams.dimAmount = 0.90f;
            window.setAttributes(windowParams);
        }
    }

    private void hideLoadingOverlay() {
        if (binding != null && binding.progressOverlay != null) {
            animateView(binding.progressOverlay, View.GONE, 0, 200);
        }
    }

    @Subscribe
    public void onAccountHistoryResponse(TransferHistoryResponse transferHistoryResponse) {
        // Part 1 - account_history to determine frontier
        if (transferHistoryResponse == null) {
            return;
        }
        String account = transferHistoryResponse.getAccount();
        AccountHistoryResponse accountHistoryResponse = transferHistoryResponse.getAccountHistoryResponse();
        AccountBalanceItem accountBalanceItem = rawInMap.get(account);
        if (accountBalanceItem == null) {
            return;
        }
        if (accountHistoryResponse.getHistory().size() > 0) {
            accountBalanceItem.setFrontier(accountHistoryResponse.getHistory().get(0).getHash());
            rawInMap.put(account, accountBalanceItem);
        }
        accountService.requestPending(account);
    }

    @Subscribe
    public void onPendingResponse(PendingTransactionResponse pendingTransactionResponse) {
        // Part 2 - pending to begin pocketing pending blocks
        // Store response for this account
        AccountBalanceItem balanceItem = rawInMap.get(pendingTransactionResponse.getAccount());
        balanceItem.setPendingTransactions(pendingTransactionResponse);
        rawInMap.put(pendingTransactionResponse.getAccount(), balanceItem);
        // Iterate pending and request receive/open as many times as it takes.
        for (Map.Entry<String, PendingTransactionResponseItem> itemEntry : pendingTransactionResponse.getBlocks().entrySet()) {
            PendingTransactionResponseItem pendingTransactionResponseItem = itemEntry.getValue();
            pendingTransactionResponseItem.setHash(itemEntry.getKey());
            if (balanceItem.getFrontier() != null) {
                // Request receive
            } else {
                // request open
            }
        }
    }

    private void startProcessing() {
        if (rawInMap.size() > 0) {
            Map.Entry<String, AccountBalanceItem> item = rawInMap.entrySet().iterator().next();
            String account = item.getKey();
            // Kick off account_history request
            accountService.requestAccountHistory(account);
        }
    }

    public class ClickHandlers {
        public void onClickClose(View view) {
            dismiss();
        }

        public void onClickConfirm(View view) {
            showLoadingOverlay();
            startProcessing();
        }
    }
}
