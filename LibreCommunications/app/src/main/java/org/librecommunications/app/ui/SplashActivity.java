package org.librecommunications.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.librecommunications.app.R;

/**
 * Splash screen with anti-EU-Chat-Control animation sequence + haptic feedback.
 */
public class SplashActivity extends AppCompatActivity {

    private ImageView starCenter;
    private ImageView[] stars = new ImageView[12];
    private ImageView blockSymbol;
    private ImageView padlock;
    private FrameLayout chatControlWrapper;
    private TextView tvChatControl;
    private View strikethrough;
    private LinearLayout brandingLayout;
    private Vibrator vibrator;

    private static final float CIRCLE_RADIUS_DP = 110f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(0xFF001B54);
        getWindow().setNavigationBarColor(0xFF001B54);

        // Get vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        starCenter = findViewById(R.id.starCenter);
        blockSymbol = findViewById(R.id.blockSymbol);
        padlock = findViewById(R.id.padlock);
        chatControlWrapper = findViewById(R.id.chatControlWrapper);
        tvChatControl = findViewById(R.id.tvChatControl);
        strikethrough = findViewById(R.id.strikethrough);
        brandingLayout = findViewById(R.id.brandingLayout);

        stars[0] = findViewById(R.id.star0);
        stars[1] = findViewById(R.id.star1);
        stars[2] = findViewById(R.id.star2);
        stars[3] = findViewById(R.id.star3);
        stars[4] = findViewById(R.id.star4);
        stars[5] = findViewById(R.id.star5);
        stars[6] = findViewById(R.id.star6);
        stars[7] = findViewById(R.id.star7);
        stars[8] = findViewById(R.id.star8);
        stars[9] = findViewById(R.id.star9);
        stars[10] = findViewById(R.id.star10);
        stars[11] = findViewById(R.id.star11);

        startAnimationSequence();
    }

    private void vibrate(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void vibratePattern(long[] pattern) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    private void vibrateHeavy(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, 255));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void startAnimationSequence() {
        float density = getResources().getDisplayMetrics().density;
        float radiusPx = CIRCLE_RADIUS_DP * density;

        // ════════════════════════════════════════
        // PHASE 1: Central star fades in and pulses (0 - 1200ms)
        // ════════════════════════════════════════
        ObjectAnimator centerFadeIn = ObjectAnimator.ofFloat(starCenter, "alpha", 0f, 1f);
        centerFadeIn.setDuration(600);

        ObjectAnimator centerScaleX = ObjectAnimator.ofFloat(starCenter, "scaleX", 0.2f, 1.2f, 1f);
        centerScaleX.setDuration(600);
        ObjectAnimator centerScaleY = ObjectAnimator.ofFloat(starCenter, "scaleY", 0.2f, 1.2f, 1f);
        centerScaleY.setDuration(600);

        centerFadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vibrate(30); // subtle tick when star appears
            }
        });

        ObjectAnimator pulseX = ObjectAnimator.ofFloat(starCenter, "scaleX", 1f, 1.3f, 1f);
        pulseX.setDuration(500);
        pulseX.setRepeatCount(1);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(starCenter, "scaleY", 1f, 1.3f, 1f);
        pulseY.setDuration(500);
        pulseY.setRepeatCount(1);

        pulseX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vibratePattern(new long[]{0, 20, 100, 20}); // double tick for pulse
            }
        });

        AnimatorSet phase1 = new AnimatorSet();
        phase1.play(centerFadeIn).with(centerScaleX).with(centerScaleY);
        AnimatorSet phase1Pulse = new AnimatorSet();
        phase1Pulse.play(pulseX).with(pulseY);
        phase1Pulse.setStartDelay(100);

        // ════════════════════════════════════════
        // PHASE 2: Stars burst outward (1200ms - 2800ms)
        // ════════════════════════════════════════
        AnimatorSet phase2 = new AnimatorSet();
        java.util.List<Animator> starAnimators = new java.util.ArrayList<>();

        for (int i = 0; i < 12; i++) {
            double angleDeg = -90 + (i * 30.0);
            double angleRad = Math.toRadians(angleDeg);
            float targetX = (float) (Math.cos(angleRad) * radiusPx);
            float targetY = (float) (Math.sin(angleRad) * radiusPx);

            ObjectAnimator moveX = ObjectAnimator.ofFloat(stars[i], "translationX", 0f, targetX);
            moveX.setDuration(800);
            moveX.setInterpolator(new OvershootInterpolator(1.2f));

            ObjectAnimator moveY = ObjectAnimator.ofFloat(stars[i], "translationY", 0f, targetY);
            moveY.setDuration(800);
            moveY.setInterpolator(new OvershootInterpolator(1.2f));

            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(stars[i], "alpha", 0f, 1f);
            fadeIn.setDuration(300);

            ObjectAnimator rotStar = ObjectAnimator.ofFloat(stars[i], "rotation", 0f, 360f);
            rotStar.setDuration(800);

            AnimatorSet singleStar = new AnimatorSet();
            singleStar.playTogether(moveX, moveY, fadeIn, rotStar);
            singleStar.setStartDelay(i * 60L);

            // Vibrate for first star burst
            if (i == 0) {
                singleStar.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        vibratePattern(new long[]{0, 15, 30, 15, 30, 15, 30, 15, 30, 15, 30, 15}); // rapid burst for 12 stars
                    }
                });
            }

            starAnimators.add(singleStar);
        }

        ObjectAnimator centerFadeOut = ObjectAnimator.ofFloat(starCenter, "alpha", 1f, 0f);
        centerFadeOut.setDuration(400);
        starAnimators.add(centerFadeOut);

        phase2.playTogether(starAnimators);

        // ════════════════════════════════════════
        // PHASE 3: Block symbol SLAMS in (2800ms - 3600ms)
        // ════════════════════════════════════════
        ObjectAnimator blockFadeIn = ObjectAnimator.ofFloat(blockSymbol, "alpha", 0f, 1f);
        blockFadeIn.setDuration(200);

        ObjectAnimator blockScaleX = ObjectAnimator.ofFloat(blockSymbol, "scaleX", 0.3f, 1.1f, 1f);
        blockScaleX.setDuration(500);
        blockScaleX.setInterpolator(new AnticipateOvershootInterpolator());

        ObjectAnimator blockScaleY = ObjectAnimator.ofFloat(blockSymbol, "scaleY", 0.3f, 1.1f, 1f);
        blockScaleY.setDuration(500);
        blockScaleY.setInterpolator(new AnticipateOvershootInterpolator());

        ObjectAnimator blockRotate = ObjectAnimator.ofFloat(blockSymbol, "rotation", -45f, 0f);
        blockRotate.setDuration(500);

        blockFadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vibrateHeavy(100); // HEAVY slam vibration
            }
        });

        AnimatorSet phase3 = new AnimatorSet();
        phase3.playTogether(blockFadeIn, blockScaleX, blockScaleY, blockRotate);

        // Dim the stars
        AnimatorSet dimStars = new AnimatorSet();
        java.util.List<Animator> dimAnims = new java.util.ArrayList<>();
        for (ImageView star : stars) {
            dimAnims.add(ObjectAnimator.ofFloat(star, "alpha", 1f, 0.3f).setDuration(400));
        }
        dimStars.playTogether(dimAnims);

        // ════════════════════════════════════════
        // PHASE 4: Padlock rises + locks (3600ms - 4400ms)
        // ════════════════════════════════════════
        ObjectAnimator padlockRise = ObjectAnimator.ofFloat(padlock, "translationY", 200f, 0f);
        padlockRise.setDuration(600);
        padlockRise.setInterpolator(new DecelerateInterpolator(2f));

        ObjectAnimator padlockFade = ObjectAnimator.ofFloat(padlock, "alpha", 0f, 1f);
        padlockFade.setDuration(400);

        // Lock slam shake
        ObjectAnimator padlockShakeX = ObjectAnimator.ofFloat(padlock, "translationX", 0f, -10f, 10f, -6f, 6f, -3f, 0f);
        padlockShakeX.setDuration(400);

        padlockShakeX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vibrateHeavy(150); // HEAVY lock-slam vibration
            }
        });

        AnimatorSet phase4 = new AnimatorSet();
        phase4.playTogether(padlockRise, padlockFade);

        // ════════════════════════════════════════
        // PHASE 5: "CHAT CONTROL 2.0" + strikethrough (4400ms - 5400ms)
        // ════════════════════════════════════════
        ObjectAnimator chatControlFade = ObjectAnimator.ofFloat(chatControlWrapper, "alpha", 0f, 1f);
        chatControlFade.setDuration(400);

        chatControlFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Animate strikethrough from left edge to full width of the text
                int textWidth = tvChatControl.getWidth();
                if (textWidth <= 0) textWidth = 600; // fallback

                ValueAnimator strikeAnim = ValueAnimator.ofInt(0, textWidth);
                strikeAnim.setDuration(500);
                strikeAnim.setStartDelay(200);
                strikeAnim.addUpdateListener(anim -> {
                    android.view.ViewGroup.LayoutParams lp = strikethrough.getLayoutParams();
                    lp.width = (int) anim.getAnimatedValue();
                    strikethrough.setLayoutParams(lp);
                });
                strikeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        vibratePattern(new long[]{0, 30, 50, 60}); // scratch vibration for strikethrough
                    }
                });
                strikeAnim.start();
            }
        });

        AnimatorSet phase5 = new AnimatorSet();
        phase5.play(chatControlFade);

        // ════════════════════════════════════════
        // PHASE 6: Branding fades in (5400ms - 6000ms)
        // ════════════════════════════════════════
        ObjectAnimator brandFade = ObjectAnimator.ofFloat(brandingLayout, "alpha", 0f, 1f);
        brandFade.setDuration(600);

        ObjectAnimator brandSlide = ObjectAnimator.ofFloat(brandingLayout, "translationY", 40f, 0f);
        brandSlide.setDuration(600);
        brandSlide.setInterpolator(new DecelerateInterpolator());

        brandFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vibrate(40); // gentle confirmation vibration
            }
        });

        AnimatorSet phase6 = new AnimatorSet();
        phase6.playTogether(brandFade, brandSlide);

        // ════════════════════════════════════════
        // CHAIN ALL PHASES
        // ════════════════════════════════════════
        AnimatorSet fullSequence = new AnimatorSet();
        fullSequence.play(phase1);
        fullSequence.play(phase1Pulse).after(phase1);
        fullSequence.play(phase2).after(phase1Pulse);
        fullSequence.play(phase3).after(phase2);
        fullSequence.play(dimStars).with(phase3);
        fullSequence.play(phase4).after(phase3);
        fullSequence.play(padlockShakeX).after(phase4);
        fullSequence.play(phase5).after(padlockShakeX);
        fullSequence.play(phase6).after(phase5);

        fullSequence.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                brandingLayout.postDelayed(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }, 1500);
            }
        });

        starCenter.postDelayed(fullSequence::start, 400);
    }
}
