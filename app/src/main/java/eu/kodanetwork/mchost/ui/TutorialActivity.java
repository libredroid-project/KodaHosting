package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.HapticUtil;

/**
 * Cinematic first-open tutorial. ONE crate object: it tumbles in from the top,
 * lands with a squash, opens its lid in one direction, and every message
 * (welcome + ToS) is pulled out of it as a letter that types its text live.
 * The crate sits in the LOWER third so the letters hover above it.
 */
public class TutorialActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private FrameLayout centerStage;
    private LinearLayout cardHost;
    private TextView cursor;
    private boolean finished = false;

    private FrameLayout crate;
    private View crateBody;
    private View crateLid;
    private View crateTape;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        root.addView(new FloatingSquaresView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        centerStage = new FrameLayout(this);
        centerStage.setClipChildren(false);
        root.addView(centerStage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cardHost = new LinearLayout(this);
        cardHost.setOrientation(LinearLayout.VERTICAL);
        cardHost.setGravity(Gravity.CENTER);
        centerStage.addView(cardHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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

        cursor = new TextView(this);
        cursor.setText("➢");
        cursor.setTextSize(28);
        cursor.setTextColor(0xFFFF6B00);
        cursor.setAlpha(0f);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        buildCrate();
        startHum();
    }

    // ── the crate: solid cardboard, tape stripe, one-way hinged lid ─────────

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void buildCrate() {
        crate = new FrameLayout(this);
        int size = dp(132);
        // crate lives in the lower third of the screen
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        lp.topMargin = (int)(getResources().getDisplayMetrics().heightPixels * 0.16f);
        crate.setLayoutParams(lp);
        crate.setAlpha(0f);

        // lid: flap above the body, hinged at its bottom edge, opens backwards
        crateLid = new View(this);
        crateLid.setBackground(cardboard(true));
        FrameLayout.LayoutParams lidLp = new FrameLayout.LayoutParams(size, dp(36), Gravity.TOP);
        crate.addView(crateLid, lidLp);
        crateLid.setPivotY(dp(36));
        crateLid.setRotationX(0f);
        crateLid.setCameraDistance(dp(1400));

        // solid body below the lid
        crateBody = new View(this);
        crateBody.setBackground(cardboard(false));
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(size, size - dp(30), Gravity.BOTTOM);
        crate.addView(crateBody, bodyLp);

        // packing tape stripe across the front
        crateTape = new View(this);
        android.graphics.drawable.GradientDrawable tape = new android.graphics.drawable.GradientDrawable();
        tape.setColor(0xCCFF8C38);
        tape.setCornerRadius(dp(2));
        crateTape.setBackground(tape);
        FrameLayout.LayoutParams tapeLp = new FrameLayout.LayoutParams(size, dp(14), Gravity.CENTER_VERTICAL);
        crate.addView(crateTape, tapeLp);

        centerStage.addView(crate);
    }

    /** Solid cardboard look: filled brown surface with darker edges + orange rim. */
    private android.graphics.drawable.GradientDrawable cardboard(boolean isLid) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                isLid ? new int[]{0xFF3A2C20, 0xFF241A12} : new int[]{0xFF33261B, 0xFF1D140D});
        g.setCornerRadius(dp(isLid ? 5 : 8));
        g.setStroke(dp(2), 0x88FF6B00);
        return g;
    }

    private void openLid(Runnable onDone) {
        crateTape.animate().alpha(0f).setDuration(200).start();
        crateLid.animate().rotationX(-115f).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .withEndAction(onDone).start();
    }

    private void closeLid(Runnable onDone) {
        crateLid.animate().rotationX(0f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    crateTape.animate().alpha(1f).setDuration(200).start();
                    if (onDone != null) onDone.run();
                }).start();
    }

    // ── acts ────────────────────────────────────────────────────────────────

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
                crateFallsIn();
            }
        };
        handler.post(tick[0]);
    }

    /** The crate itself tumbles in from the top and lands with a squash. */
    private void crateFallsIn() {
        float h = getResources().getDisplayMetrics().heightPixels;
        crate.setAlpha(1f);
        crate.setScaleX(0.5f);
        crate.setScaleY(0.5f);
        crate.setRotation(540f);
        crate.setTranslationY(-h * 0.55f);

        crate.animate()
                .translationY(0f).rotation(0f).scaleX(1f).scaleY(1f)
                .setDuration(900)
                .setInterpolator(new AccelerateInterpolator(1.15f))
                .withEndAction(() -> {
                    HapticUtil.forceVibrate(this, 250); // the thud
                    // landing squash
                    crate.animate().scaleX(1.1f).scaleY(0.86f).setDuration(90)
                            .withEndAction(() -> crate.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(140).withEndAction(() ->
                                            handler.postDelayed(() -> openLid(() -> {
                                                HapticUtil.forceVibrate(this, 80);
                                                startWelcome();
                                            }), 220)).start()).start();
                }).start();
    }

    private void startWelcome() {
        final LinearLayout[] cardRef = new LinearLayout[1];
        LinearLayout card = makeLetterCard(
                getString(R.string.tutorial_welcome_title),
                getString(R.string.tutorial_welcome_body),
                getString(R.string.tutorial_ack),
                () -> flyIntoCrate(cardRef[0], () -> closeLid(() -> {
                    HapticUtil.forceVibrate(this, 80);
                    dropCrate(this::startElevator);
                })));
        cardRef[0] = card;
        flyOutOfCrate(card);
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

        View platform = new View(this);
        android.graphics.drawable.GradientDrawable platBg = new android.graphics.drawable.GradientDrawable();
        platBg.setColor(0x668A8A9A);
        platBg.setCornerRadius(dp(8));
        platBg.setStroke(dp(2), 0xFFFF6B00);
        platform.setBackground(platBg);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(180), dp(14), Gravity.CENTER);
        pLp.topMargin = (int)(getResources().getDisplayMetrics().heightPixels * 0.16f) + dp(74);
        root.addView(platform, pLp);

        float h = getResources().getDisplayMetrics().heightPixels;
        float crateRestY = 0f; // crate rests at its layout position
        float startY = h * 0.45f; // rise from near the bottom
        crate.setAlpha(1f);
        crate.setTranslationY(startY);
        platform.setTranslationY(startY + dp(90));
        platform.setAlpha(0f);

        crate.animate().translationY(crateRestY).setDuration(1600)
                .setInterpolator(new DecelerateInterpolator(1.2f)).start();
        platform.animate().alpha(1f).translationY(0f).setDuration(1600)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .withEndAction(() -> {
                    if (!tosShown) {
                        tosShown = true;
                        HapticUtil.forceVibrate(this, 120);
                        openLid(this::startTos);
                    }
                }).start();
    }

    private boolean tosShown = false;

    private void startTos() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(48, 32, 48, 32);
        card.setBackgroundResource(R.drawable.bg_dialog_custom);

        applyPraetorHeader(card, getString(R.string.tutorial_tos_title), "TERMS OF SERVICE");

        ScrollView scroll = new ScrollView(this);
        TextView tosText = new TextView(this);
        tosText.setText(readAssetText("licenses/tos.txt"));
        tosText.setTextColor(0xFF8A8A9A);
        tosText.setTextSize(11);
        tosText.setLineSpacing(4, 1f);
        scroll.addView(tosText);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
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
            flyIntoCrate(card, () -> closeLid(this::startCursorFinale));
        });

        flyOutOfCrate(card);
    }

    private void startCursorFinale() {
        HapticUtil.forceVibrate(this, 80);
        Button closeBtn = eu.kodanetwork.mchost.util.KodaButtons.primary(this, getString(R.string.tutorial_tos_close));
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        bLp.bottomMargin = dp(80);
        root.addView(closeBtn, bLp);
        closeBtn.setAlpha(0f);
        closeBtn.animate().alpha(1f).setDuration(300).start();

        cursor.setAlpha(1f);
        float targetX = root.getWidth() / 2f - dp(50);
        float targetY = root.getHeight() - bLp.bottomMargin - dp(40);
        cursor.animate()
                .translationX(targetX - root.getWidth() / 2f + dp(50))
                .translationY(targetY - root.getHeight() / 2f)
                .setDuration(1400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    HapticUtil.forceVibrate(this, 60);
                    closeBtn.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                            .withEndAction(() -> closeBtn.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                            .start();
                    handler.postDelayed(() -> {
                        cursor.animate().alpha(0f).setDuration(250).start();
                        closeBtn.animate().alpha(0f).setDuration(250).start();
                        dropCrate(() -> root.animate().alpha(0f).setDuration(500)
                                .withEndAction(() -> finishTutorial(false)).start());
                    }, 700);
                })
                .start();
    }

    // ── letter cards ────────────────────────────────────────────────────────

    private void applyPraetorHeader(LinearLayout card, String title, String subtitle) {
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFF3333);
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLetterSpacing(0.08f);
        tvTitle.setPadding(0, 0, 0, 8);
        card.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(0xFF8A8A9A);
        tvSub.setTextSize(10);
        tvSub.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvSub.setLetterSpacing(0.05f);
        tvSub.setPadding(0, 0, 0, 20);
        card.addView(tvSub);

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFFFF6B00);
        card.addView(divider, new LinearLayout.LayoutParams(dp(40), 2));
    }

    private LinearLayout makeLetterCard(String title, String body, String buttonLabel, Runnable onAck) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(48, 32, 48, 32);
        card.setBackgroundResource(R.drawable.bg_dialog_custom);

        applyPraetorHeader(card, title, "KODAHOSTING");

        android.view.View spacer = new android.view.View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(1, 20));

        TextView tvBody = new TextView(this);
        tvBody.setTextColor(0xFFF0F0F0);
        tvBody.setTextSize(14);
        tvBody.setLineSpacing(4, 1.1f);
        tvBody.setTag("tw_body");
        // weight=1 pushes the button to the very bottom of the letter
        card.addView(tvBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btn = eu.kodanetwork.mchost.util.KodaButtons.primary(this, buttonLabel);
        btn.setTag("tw_btn");
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bLp.topMargin = 24;
        card.addView(btn, bLp);
        btn.setOnClickListener(v -> onAck.run());

        tvBody.setText("");
        btn.setAlpha(0f);
        card.setTag(R.id.tw_text, body);
        return card;
    }

    // ── letters come OUT of the crate and hover above it ───────────────────

    private void flyOutOfCrate(LinearLayout card) {
        int cardHeight = (int)(getResources().getDisplayMetrics().heightPixels * 0.46f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.84f),
                cardHeight, Gravity.CENTER);
        // bottom of the letter overlaps the crate opening slightly; crate top
        // sits ~16%+66dp below center, so the letter center lands here:
        lp.bottomMargin = (int)(getResources().getDisplayMetrics().heightPixels * 0.16f) + dp(70);
        card.setLayoutParams(lp);
        cardHost.addView(card);
        card.setAlpha(0f);
        card.setScaleX(0.25f);
        card.setScaleY(0.25f);
        card.setTranslationY(dp(110)); // starts down at the crate
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(650)
                .setInterpolator(new DecelerateInterpolator(1.1f))
                .withEndAction(() -> {
                    TextView body = (TextView) card.findViewWithTag("tw_body");
                    Button btn = (Button) card.findViewWithTag("tw_btn");
                    String text = (String) card.getTag(R.id.tw_text);
                    if (body != null && text != null) {
                        typewrite(body, text, () -> {
                            if (btn != null) btn.animate().alpha(1f).setDuration(250).start();
                        });
                    }
                }).start();
    }

    private void flyIntoCrate(LinearLayout card, Runnable onDone) {
        card.animate().alpha(0f).scaleX(0.25f).scaleY(0.25f).translationY(dp(110))
                .setDuration(400).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    cardHost.removeView(card);
                    onDone.run();
                }).start();
    }

    private void dropCrate(Runnable onDone) {
        crate.animate().translationY(getResources().getDisplayMetrics().heightPixels * 0.6f)
                .rotation(16f).alpha(0f)
                .setDuration(700).setInterpolator(new AccelerateInterpolator())
                .withEndAction(onDone).start();
    }

    private void typewrite(TextView tv, String text, Runnable onDone) {
        final int[] i = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (finished) return;
            if (i[0] <= text.length()) {
                tv.setText(text.substring(0, i[0]++));
                handler.postDelayed(tick[0], 22);
            } else if (onDone != null) {
                onDone.run();
            }
        };
        handler.post(tick[0]);
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
        // Tutorial can only be left via skip
    }

    @Override
    protected void onUserLeaveHint() {
        // Leaving via home/recents counts as a skip — otherwise the cinema
        // would restart from scratch on every app re-entry
        finishTutorial(true);
    }
}
