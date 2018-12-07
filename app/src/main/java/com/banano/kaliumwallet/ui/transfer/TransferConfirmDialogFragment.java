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
import com.banano.kaliumwallet.databinding.FragmentTransferConfirmBinding;
import com.banano.kaliumwallet.network.model.response.AccountBalanceItem;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.banano.kaliumwallet.ui.common.UIUtil;
import com.banano.kaliumwallet.util.NumberUtil;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

/**
 * Initial Transfer Screen
 */
public class TransferConfirmDialogFragment extends BaseDialogFragment {
    public static String TAG = TransferConfirmDialogFragment.class.getSimpleName();

    private FragmentTransferConfirmBinding binding;

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
        HashMap<String, AccountBalanceItem> privKeyMap = new HashMap<>();
        if (getArguments().getSerializable("PRIVKEYMAP") != null) {
            privKeyMap = (HashMap<String, AccountBalanceItem>) getArguments().getSerializable("PRIVKEYMAP");
        }
        BigInteger totalSum = new BigInteger("0");
        for (Map.Entry<String, AccountBalanceItem> item : privKeyMap.entrySet()) {
            AccountBalanceItem balances = item.getValue();
            BigInteger balance = new BigInteger(balances.getBalance());
            BigInteger pending = new BigInteger(balances.getPending());
            totalSum = totalSum.add(balance).add(pending);
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

    public class ClickHandlers {
        public void onClickClose(View view) {
            dismiss();
        }

        public void onClickConfirm(View view) {
            showLoadingOverlay();
        }
    }
}
