package com.banano.kaliumwallet.ui.common;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.broadcastreceiver.ClipboardAlarmReceiver;
import com.banano.kaliumwallet.ui.pin.CreatePinDialogFragment;
import com.banano.kaliumwallet.ui.pin.PinDialogFragment;
import com.banano.kaliumwallet.util.ExceptionHandler;

/**
 * Helper methods used by all fragments
 */

public class BaseFragment extends Fragment {

    protected View view;

    /**
     * Set status bar color to gray
     */
    protected void setStatusBarGray() {
        setStatusBarColor(R.color.gray);
    }

    private void setStatusBarColor(int color) {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setStatusBarColor(color);
        }
    }

    private void setIconsDark(View view) {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setDarkIcons(view);
        }
    }

    /**
     * Hide the app toolbar
     */
    protected void hideToolbar() {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setToolbarVisible(false);
        }
    }

    /**
     * Show the app toolbar
     */
    protected void showToolbar() {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setToolbarVisible(true);
        }
    }

    /**
     * Set the title of the toolbar
     */
    protected void setTitle(String title) {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setTitle(title);
        }
    }

    /**
     * Set drawable on the toolbar
     *
     * @param drawable Drawable reference
     */
    protected void setTitleDrawable(int drawable) {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setTitleDrawable(drawable);
        }
    }

    /**
     * Enable or disable back button
     *
     * @param enabled Is enabled or not
     */
    protected void setBackEnabled(boolean enabled) {
        if (getActivity() instanceof WindowControl) {
            ((WindowControl) getActivity()).setBackEnabled(enabled);
        }
    }

    /**
     * Go back action
     */
    protected void goBack() {
        if (getActivity() instanceof WindowControl) {
            try {
                ((WindowControl) getActivity()).getFragmentUtility().pop();
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        }
    }

    /**
     * Set alarm for 2 minutes to clear the clipboard
     */
    protected void setClearClipboardAlarm() {
        // create pending intent
        AlarmManager alarmMgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getContext(), ClipboardAlarmReceiver.class);
        intent.setAction("com.banano.kaliumwallet.alarm");
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getContext(), 0, intent, 0);

        // set a two minute alarm to start the pending intent
        if (alarmMgr != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmMgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 120 * 1000, alarmIntent);
            } else {
                alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 120 * 1000, alarmIntent);
            }
        }
    }

    protected void showCreatePinScreen() {
        if (getActivity() instanceof WindowControl) {
            CreatePinDialogFragment dialog = CreatePinDialogFragment.newInstance();
            dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                    CreatePinDialogFragment.TAG);

            // make sure that dialog is not null
            ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();

            if (dialog.getDialog() != null) {
                dialog.getDialog().setOnDismissListener(dialogInterface -> {
                    KeyboardUtil.hideKeyboard(getActivity());
                });
            }
        }
    }

    protected void showPinScreen(String subtitle) {
        if (getActivity() instanceof WindowControl) {
            PinDialogFragment dialog = PinDialogFragment.newInstance(subtitle);
            dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                    PinDialogFragment.TAG);

            // make sure that dialog is not null
            ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
        }
    }

    /**
     * Create a link from the text on the view
     *
     * @param v    TextView
     * @param text id of text to add to the field
     */
    protected void createLink(TextView v, int text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            v.setText(Html.fromHtml(getString(text), Html.FROM_HTML_MODE_LEGACY));
        } else {
            v.setText(Html.fromHtml(getString(text)));
        }
        v.setTransformationMethod(new LinkTransformationMethod());
        v.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Animate appearance of a view
     * @param view         View to animate
     * @param toVisibility Visibility at the end of animation
     * @param toAlpha      Alpha at the end of animation
     * @param duration     Animation duration in ms
     */
    public static void animateView(final View view, final int toVisibility, float toAlpha, int duration) {
        boolean show = toVisibility == View.VISIBLE;
        if (show) {
            view.setAlpha(0);
        }
        view.setVisibility(View.VISIBLE);
        view.animate()
                .setDuration(duration)
                .alpha(show ? toAlpha : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(toVisibility);
                    }
                });
    }
}
