package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.tabs.TabLayout;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;

/**
 * In-app tutorial coach: an animated mouse cursor points at a target, a
 * P.R.A.E.T.O.R.-styled text card pops up next to it and types its text
 * live; tapping anywhere continues to the next step.
 * Phases (pref tutorial_phase): 1 = await first server, 2 = tab tour, 0 = off.
 */
public class TutorialCoach {

    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void maybeStartTutorial(Activity activity) {
        if (!App.getPrefs(activity).getBoolean("tutorial_completed_v2", false)) {
            android.content.Intent intent = new android.content.Intent(activity, eu.kodanetwork.mchost.ui.TutorialActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
    }

    /** Phase 1: cursor hovers over NEW SERVER, card types the explanation. */
    public static void maybeShowNewServerHint(Activity activity, View fabAdd) {
        if (App.getPrefs(activity).getInt("tutorial_phase", 0) != 1 || fabAdd == null) return;
        App.getPrefs(activity).edit().putInt("tutorial_phase", 2).apply();

        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        CoachUi ui = new CoachUi(activity, content);

        int[] loc = new int[2];
        fabAdd.getLocationInWindow(loc);
        int[] cLoc = new int[2];
        content.getLocationInWindow(cLoc);
        float cx = loc[0] - cLoc[0] + fabAdd.getWidth() / 2f;
        float cy = loc[1] - cLoc[1] + fabAdd.getHeight() / 2f;

        ui.show(cx, cy,
                activity.getString(R.string.tutorial_hint_new_server_title),
                activity.getString(R.string.tutorial_hint_new_server_body));
    }

    /** Phase 2: cursor walks through the tabs, one typed card per tab. */
    public static void maybeStartTabTour(Activity activity, TabLayout tabs) {
        if (App.getPrefs(activity).getInt("tutorial_phase", 0) != 2 || tabs == null) return;
        App.getPrefs(activity).edit().putInt("tutorial_phase", 0).apply();

        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        CoachUi ui = new CoachUi(activity, content);

        final int[] current = {-1};
        Runnable[] advance = new Runnable[1];
        advance[0] = () -> {
            current[0]++;
            if (current[0] < tabs.getTabCount()) {
                TabLayout.Tab tab = tabs.getTabAt(current[0]);
                if (tab != null) tab.select();
                View tabView = tab != null ? tab.view : null;
                if (tabView != null) {
                    int[] loc = new int[2];
                    tabView.getLocationInWindow(loc);
                    int[] cLoc = new int[2];
                    content.getLocationInWindow(cLoc);
                    float cx = loc[0] - cLoc[0] + tabView.getWidth() / 2f;
                    float cy = loc[1] - cLoc[1] + tabView.getHeight() / 2f;
                    CharSequence t = tab.getText() != null ? tab.getText() : "";
                    ui.show(cx, cy, t.toString(), activity.getString(explanationFor(t.toString().toLowerCase())));
                } else {
                    advance[0].run();
                }
            } else {
                ui.showFinale();
            }
        };
        ui.setOnAdvance(advance[0]);
        advance[0].run();
    }

    /** Tab labels are localized — map them to the matching explanation by keyword. */
    private static int explanationFor(String tabText) {
        if (tabText.contains("dash") || tabText.contains("bersicht") || tabText.contains("仪表"))
            return R.string.tutorial_tab_dashboard;
        if (tabText.contains("consol") || tabText.contains("konsole") || tabText.contains("控制"))
            return R.string.tutorial_tab_console;
        if (tabText.contains("file") || tabText.contains("datei") || tabText.contains("文件"))
            return R.string.tutorial_tab_files;
        if (tabText.contains("plug") || tabText.contains("mod") || tabText.contains("模组"))
            return R.string.tutorial_tab_plugins;
        return R.string.tutorial_tab_settings;
    }

    // ── CoachUi: dim, cursor, typed praetor card, tap-to-continue ───────────

    private static class CoachUi {
        private final Activity activity;
        private final ViewGroup content;
        private final FrameLayout overlay;
        private final TextView cursor;
        private final LinearLayout card;
        private final TextView cardTitle;
        private final TextView cardBody;
        private Runnable onAdvance;

        CoachUi(Activity activity, ViewGroup content) {
            this.activity = activity;
            this.content = content;

            overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(0x99000000);

            cursor = new TextView(activity);
            cursor.setText("➢");
            cursor.setTextSize(26);
            cursor.setTextColor(0xFFFF6B00);
            cursor.setRotation(-35f); // pointing up-right like a mouse cursor
            overlay.addView(cursor, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(40, 28, 40, 28);
            card.setBackgroundResource(eu.kodanetwork.mchost.R.drawable.bg_dialog_custom);
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.8f),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            cardLp.bottomMargin = (int)(90 * activity.getResources().getDisplayMetrics().density);
            overlay.addView(card, cardLp);

            cardTitle = new TextView(activity);
            cardTitle.setTextColor(0xFF3333);
            cardTitle.setTextSize(18);
            cardTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            cardTitle.setLetterSpacing(0.06f);
            cardTitle.setPadding(0, 0, 0, 6);
            card.addView(cardTitle);

            cardBody = new TextView(activity);
            cardBody.setTextColor(0xFFF0F0F0);
            cardBody.setTextSize(13);
            cardBody.setLineSpacing(4, 1.1f);
            card.addView(cardBody);

            TextView tapHint = new TextView(activity);
            tapHint.setText("▽");
            tapHint.setTextColor(0xFF8A8A9A);
            tapHint.setTextSize(12);
            tapHint.setGravity(Gravity.CENTER);
            tapHint.setPadding(0, 14, 0, 0);
            card.addView(tapHint);

            content.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            overlay.setOnClickListener(v -> {
                if (onAdvance != null) onAdvance.run();
            });
        }

        void setOnAdvance(Runnable r) { this.onAdvance = r; }

        void show(float targetX, float targetY, String title, String body) {
            // cursor pulses on the target
            cursor.setAlpha(1f);
            cursor.setTranslationX(targetX + 26);
            cursor.setTranslationY(targetY - 18);
            cursor.animate().translationX(targetX + 34).translationY(targetY - 26)
                    .setDuration(600).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> cursor.animate()
                            .translationX(targetX + 26).translationY(targetY - 18)
                            .setDuration(600).start()).start();

            // card slides up and types its text live
            card.setAlpha(0f);
            card.setTranslationY(60);
            card.animate().alpha(1f).translationY(0).setDuration(300).start();
            cardTitle.setText(title);
            typewrite(cardBody, body);
        }

        void showFinale() {
            overlay.removeAllViews();

            LottieAnimationView confetti = new LottieAnimationView(activity);
            confetti.setAnimation(eu.kodanetwork.mchost.R.raw.tut_confetti);
            overlay.addView(confetti, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            confetti.playAnimation();

            LinearLayout done = new LinearLayout(activity);
            done.setOrientation(LinearLayout.VERTICAL);
            done.setPadding(40, 32, 40, 32);
            done.setBackgroundResource(eu.kodanetwork.mchost.R.drawable.bg_dialog_custom);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.84f),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            overlay.addView(done, lp);

            TextView t = new TextView(activity);
            t.setText(activity.getString(R.string.tutorial_done_title));
            t.setTextColor(0xFF3333);
            t.setTextSize(20);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            done.addView(t);
            TextView b = new TextView(activity);
            b.setTextColor(0xFFF0F0F0);
            b.setTextSize(13);
            done.addView(b);
            typewrite(b, activity.getString(R.string.tutorial_done_body));

            handler.postDelayed(() -> overlay.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> content.removeView(overlay)).start(), 6000);
        }

        private void typewrite(TextView tv, String text) {
            final int[] i = {0};
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                if (i[0] <= text.length()) {
                    tv.setText(text.substring(0, i[0]++));
                    handler.postDelayed(tick[0], 20);
                }
            };
            handler.post(tick[0]);
        }
    }
}
