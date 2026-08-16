package eu.kodanetwork.mchost.util;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class M3AnimationHelper {

    /**
     * Applies a modern Material 3 Spring/Scale animation on touch.
     * MUST ONLY BE CALLED WHEN M3 MODE IS ACTIVE!
     */
    public static void applySpringTouch(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .translationZ(-8f) // 3D push-in effect
                            .setDuration(150)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationZ(0f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(1.5f))
                            .start();
                    break;
            }
            return false; // let click events propagate
        });
    }
}
