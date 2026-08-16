package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;

public class HapticUtil {

    public static void forceVibrate(Context context, int duration) {
        if (context == null) return;
        boolean enabled = eu.kodanetwork.mchost.App.getPrefs(context).getBoolean("haptics_enabled", true);
        if (!enabled) return;
        try {
            Vibrator v = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    v = vm.getDefaultVibrator();
                }
            }
            if (v == null) {
                v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(duration, 200));
                } else {
                    v.vibrate(duration);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void applyHaptics(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView().getRootView();
        applyHapticsToView(root, activity);
    }

    public static void applyHapticsToView(View root, Context context) {
        if (root == null) return;

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyHapticsToView(vg.getChildAt(i), context);
            }
        }

        // Skip SeekBars because they have custom fine-grained touch gestures
        if (root instanceof android.widget.SeekBar) {
            return;
        }

        // All clickable elements (buttons, cards, textviews with click listeners)
        if (root.isClickable() || root.hasOnClickListeners() || root instanceof Button || root instanceof ImageButton || root.getClass().getName().contains("Button")) {
            root.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    // Make the click vibration shorter and softer
                    forceVibrate(context, 40);
                }
                return false; 
            });
        }
        
        // Removed scroll vibrations because they are too annoying
        // NumberPicker scroll feedback
        if (root instanceof android.widget.NumberPicker) {
            android.widget.NumberPicker np = (android.widget.NumberPicker) root;
            np.setOnScrollListener((view, scrollState) -> {
                // NumberPicker.OnScrollListener.SCROLL_STATE_FLING or SCROLL_STATE_TOUCH_SCROLL
                // Actually, just vibrate a bit on state change, or maybe just when it settles
                // A better approach is OnValueChangedListener, but CreateServerActivity already uses it
                // Wait, if CreateServerActivity uses OnValueChangedListener, we shouldn't overwrite it here
                // We'll just hook into scroll state
                if (scrollState == android.widget.NumberPicker.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL || scrollState == android.widget.NumberPicker.OnScrollListener.SCROLL_STATE_FLING) {
                    forceVibrate(context, 40);
                }
            });
            // We also need to vibrate when it settles, but value change listener might be overridden
            // We can inject a formatter to trigger on every wheel change since formatter is called every step
            np.setFormatter(value -> {
                forceVibrate(context, 40);
                return String.valueOf(value);
            });
        }
    }
}
