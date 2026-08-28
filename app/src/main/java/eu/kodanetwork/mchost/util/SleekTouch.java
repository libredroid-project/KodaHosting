package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Custom touch feedback for the Sleek design system — NO Android ripple.
 * On press: scales to 0.90 and dims slightly, then springs back with overshoot.
 */
public class SleekTouch {

    public static void apply(final View v, final Runnable onClick) {
        apply(v, onClick, 50);
    }

    public static void apply(final View v, final Runnable onClick, final int hapticMs) {
        // Mark as SleekTouch-owned: HapticUtil.applyHaptics would otherwise overwrite
        // this listener with a vibration-only one (breaking the click entirely)
        v.setTag(eu.kodanetwork.mchost.R.id.sleek_touch_tag, Boolean.TRUE);
        v.setClickable(true);
        v.setOnTouchListener(new View.OnTouchListener() {
            private boolean pressed = false;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pressed = true;
                        view.animate().cancel();
                        view.animate()
                                .scaleX(0.90f).scaleY(0.90f)
                                .alpha(0.7f)
                                .setDuration(70)
                                .start();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (pressed) {
                            pressed = false;
                            // Check if finger is still on the view
                            boolean inside = e.getX() >= 0 && e.getX() <= view.getWidth()
                                    && e.getY() >= 0 && e.getY() <= view.getHeight();
                            view.animate().cancel();
                            view.animate()
                                    .scaleX(1f).scaleY(1f)
                                    .alpha(1f)
                                    .setDuration(200)
                                    .setInterpolator(new OvershootInterpolator(3f))
                                    .start();
                            if (inside) {
                                if (hapticMs > 0) {
                                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(
                                            (android.content.Context) view.getContext(), hapticMs);
                                }
                                if (onClick != null) onClick.run();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        pressed = false;
                        view.animate().cancel();
                        view.animate()
                                .scaleX(1f).scaleY(1f)
                                .alpha(1f)
                                .setDuration(150)
                                .start();
                        return true;
                }
                return false;
            }
        });
    }

    /** Convenience: wire with haptics and scale-bounce */
    public static void button(View v, Runnable onClick) {
        apply(v, onClick, 50);
    }

    /** No haptics, just the visual press effect */
    public static void passive(View v) {
        apply(v, null, 0);
    }
}
