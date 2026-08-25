package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.HapticUtil;

/**
 * Cinematic first-open tutorial:
 * humming vibrations -> spinning block flies in -> opens as a box -> welcome
 * message -> box closes and falls -> elevator raises a second box -> ToS
 * confirmation -> mouse-cursor finale -> hands over to the in-app coach.
 */
public class TutorialActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private FrameLayout centerStage;
    private LottieAnimationView actLottie;
    private LinearLayout cardHost;
    private TextView cursor;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen like the screensaver (theme stays MaterialComponents
        // so MaterialButton works)
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0807);
        setContentView(root);

        // The real AFK screensaver background the app uses (floating Koda squares)
        eu.kodanetwork.mchost.ui.FloatingSquaresView bg =
                new eu.kodanetwork.mchost.ui.FloatingSquaresView(this);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        centerStage = new FrameLayout(this);
        root.addView(centerStage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        actLottie = new LottieAnimationView(this);
        actLottie.setScaleType(LottieAnimationView.ScaleType.CENTER_CROP);
        centerStage.addView(actLottie, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cardHost = new LinearLayout(this);
        cardHost.setOrientation(LinearLayout.VERTICAL);
        cardHost.setGravity(Gravity.CENTER);
        centerStage.addView(cardHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Skip button — always available
        TextView btnSkip = new TextView(this);
        btnSkip.setText(getString(R.string.tutorial_skip));
        btnSkip.setTextColor(0xFF8A8A9A);
        btnSkip.setTextSize(12);
        btnSkip.setPadding(24, 16, 24, 16);
        btnSkip.setOnClickListener(v -> finishTutorial(true));
        FrameLayout.LayoutParams skipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        skipLp.topMargin = 48;
        root.addView(btnSkip, skipLp);

        // Mouse cursor for the finale (hidden until needed)
        cursor = new TextView(this);
        cursor.setText("➢");
        cursor.setTextSize(28);
        cursor.setTextColor(0xFFFF6B00);
        cursor.setAlpha(0f);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        startHum();
    }

    // ── Act 1: accelerating hum ────────────────────────────────────────────

    private void startHum() {
        int[] delays = {700, 600, 500, 420, 340, 270, 210, 160, 120, 90, 70, 60};
        final int[] i = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (finished) return;
            if (i[0] < delays.length) {
                HapticUtil.forceVibrate(this, 40);
                handler.postDelayed(tick[0], delays[i[0]++]);
            } else {
                startBlockIn();
            }
        };
        handler.post(tick[0]);
    }

    // ── Act 2: spinning block lands ────────────────────────────────────────

    private void startBlockIn() {
        playOnce(R.raw.tut_block, () -> {
            HapticUtil.forceVibrate(this, 250); // the thud
            handler.postDelayed(this::startWelcome, 250);
        });
    }

    // ── Act 3: welcome message out of the box ──────────────────────────────

    private void startWelcome() {
        final LinearLayout[] cardRef = new LinearLayout[1];
        LinearLayout card = makeCard(
                getString(R.string.tutorial_welcome_title),
                getString(R.string.tutorial_welcome_body),
                getString(R.string.tutorial_ack),
                () -> {
                    flyOut(cardRef[0], () -> {
                        HapticUtil.forceVibrate(this, 80);
                        startBoxFall();
                    });
                });
        cardRef[0] = card;
        flyIn(card);
    }

    // ── Act 4: box falls, elevator rises the ToS box ───────────────────────

    private void startBoxFall() {
        playOnce(R.raw.tut_box_fall, this::startElevator);
    }

    private void startElevator() {
        int[] delays = {500, 400, 320, 250, 190, 140, 100, 80, 60, 50};
        final int[] i = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (finished) return;
            if (i[0] < delays.length) {
                HapticUtil.forceVibrate(this, 30);
                handler.postDelayed(tick[0], delays[i[0]++]);
            }
        };
        handler.post(tick[0]);

        actLottie.setAnimation(R.raw.tut_elevator);
        actLottie.setProgress(0f);
        actLottie.addAnimatorUpdateListener(a -> {
            if (a.getAnimatedFraction() >= 0.62f && cardHost.getChildCount() == 0) {
                HapticUtil.forceVibrate(this, 120);
                startTos();
            }
        });
        actLottie.playAnimation();
    }

    // ── Act 5: ToS confirmation on the box ─────────────────────────────────

    private void startTos() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xF20A0807);
        card.setPadding(40, 32, 40, 32);
        GradientDrawableCard(card);

        TextView title = new TextView(this);
        title.setText(getString(R.string.tutorial_tos_title));
        title.setTextColor(0xFFFF6B00);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 16);
        card.addView(title);

        ScrollView scroll = new ScrollView(this);
        TextView tosText = new TextView(this);
        tosText.setText(readAssetText("licenses/tos.txt"));
        tosText.setTextColor(0xFF8A8A9A);
        tosText.setTextSize(11);
        tosText.setLineSpacing(4, 1f);
        scroll.addView(tosText);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(scroll, scLp);

        CheckBox cb = new CheckBox(this);
        cb.setText(getString(R.string.tutorial_tos_confirm));
        cb.setTextColor(0xFFF0F0F0);
        cb.setTextSize(13);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B00));
        card.addView(cb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button confirm = eu.kodanetwork.mchost.util.KodaButtons.primary(this, getString(R.string.tutorial_tos_accept));
        confirm.setEnabled(false);
        confirm.setAlpha(0.4f);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(52 * getResources().getDisplayMetrics().density));
        cLp.topMargin = 16;
        card.addView(confirm, cLp);

        cb.setOnCheckedChangeListener((b, checked) -> {
            confirm.setEnabled(checked);
            confirm.animate().alpha(checked ? 1f : 0.4f).setDuration(150).start();
        });

        confirm.setOnClickListener(v -> {
            App.getPrefs(this).edit()
                    .putBoolean("tos_accepted_v3", true)
                    .putLong("accepted_tos_version_ts", System.currentTimeMillis())
                    .apply();
            flyOut(card, this::startCursorFinale);
        });

        flyIn(card);
    }

    // ── Act 6: cursor finale — box closes and the app takes over ───────────

    private void startCursorFinale() {
        HapticUtil.forceVibrate(this, 80);
        Button closeBtn = eu.kodanetwork.mchost.util.KodaButtons.primary(this, getString(R.string.tutorial_tos_close));
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        bLp.bottomMargin = (int)(80 * getResources().getDisplayMetrics().density);
        root.addView(closeBtn, bLp);
        closeBtn.setAlpha(0f);
        closeBtn.animate().alpha(1f).setDuration(300).start();

        // Cursor travels from center to the close button and "clicks" it
        cursor.setAlpha(1f);
        float targetX = root.getWidth() / 2f - 60;
        float targetY = root.getHeight() - bLp.bottomMargin - 40;
        cursor.animate()
                .translationX(targetX - root.getWidth() / 2f + 60)
                .translationY(targetY - root.getHeight() / 2f)
                .setDuration(1400)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    HapticUtil.forceVibrate(this, 60);
                    closeBtn.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                            .withEndAction(() -> closeBtn.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                            .start();
                    handler.postDelayed(() -> {
                        cursor.animate().alpha(0f).setDuration(250).start();
                        closeBtn.animate().alpha(0f).setDuration(250).start();
                        playOnce(R.raw.tut_box_fall, () -> {
                            root.animate().alpha(0f).setDuration(500).withEndAction(() -> finishTutorial(false)).start();
                        });
                    }, 700);
                })
                .start();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void playOnce(int rawRes, Runnable onEnd) {
        cardHost.removeAllViews();
        actLottie.setAnimation(rawRes);
        actLottie.setProgress(0f);
        actLottie.addAnimatorListener(new android.animation.AnimatorListenerAdapter() {
            boolean done = false;
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (!done) { done = true; onEnd.run(); }
            }
        });
        actLottie.playAnimation();
    }

    private LinearLayout makeCard(String title, String body, String buttonLabel, Runnable onAck) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xF20A0807);
        card.setPadding(40, 32, 40, 32);
        GradientDrawableCard(card);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        card.addView(tvTitle);

        TextView tvBody = new TextView(this);
        tvBody.setText(body);
        tvBody.setTextColor(0xFFF0F0F0);
        tvBody.setTextSize(13);
        tvBody.setLineSpacing(4, 1.1f);
        card.addView(tvBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btn = eu.kodanetwork.mchost.util.KodaButtons.primary(this, buttonLabel);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(52 * getResources().getDisplayMetrics().density));
        bLp.topMargin = 24;
        card.addView(btn, bLp);
        btn.setOnClickListener(v -> onAck.run());

        return card;
    }

    private void GradientDrawableCard(LinearLayout card) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF20E0E14);
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        bg.setStroke(2, 0x66FF6B00);
        card.setBackground(bg);
    }

    private void flyIn(LinearLayout card) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.82f),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.5f), Gravity.CENTER);
        card.setLayoutParams(lp);
        cardHost.addView(card);
        card.setAlpha(0f);
        card.setTranslationY(-120 * getResources().getDisplayMetrics().density);
        card.animate().alpha(1f).translationY(0f).setDuration(450)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void flyOut(LinearLayout card, Runnable onDone) {
        card.animate().alpha(0f).translationY(-120 * getResources().getDisplayMetrics().density)
                .setDuration(350).setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    cardHost.removeView(card);
                    onDone.run();
                }).start();
    }

    private String readAssetText(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "Terms of Service could not be loaded.";
        }
    }

    /** @param skipped true when the user aborted — coach phases are skipped then */
    private void finishTutorial(boolean skipped) {
        if (finished) return;
        finished = true;
        App.getPrefs(this).edit()
                .putBoolean("tutorial_completed_v2", true)
                .putInt("tutorial_phase", skipped ? 0 : 1)
                .apply();
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        // Tutorial can only be left via skip; blocks accidental aborts mid-cinema
    }
}
