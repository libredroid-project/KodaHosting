package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.tabs.TabLayout;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;

/**
 * In-app tutorial coach: after the cinematic TutorialActivity it guides the
 * user through creating the first server and the server-manager tabs.
 * Phases (pref tutorial_phase): 1 = await first server, 2 = tab tour, 0 = off.
 */
public class TutorialCoach {

    public static void maybeStartTutorial(Activity activity) {
        if (!App.getPrefs(activity).getBoolean("tutorial_completed_v2", false)) {
            android.content.Intent intent = new android.content.Intent(activity, eu.kodanetwork.mchost.ui.TutorialActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
    }

    /** Phase 1: pulse a highlight ring over the NEW SERVER button and explain it. */
    public static void maybeShowNewServerHint(Activity activity, View fabAdd) {
        if (App.getPrefs(activity).getInt("tutorial_phase", 0) != 1 || fabAdd == null) return;
        App.getPrefs(activity).edit().putInt("tutorial_phase", 2).apply();

        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        int[] loc = new int[2];
        fabAdd.getLocationInWindow(loc);
        int[] cLoc = new int[2];
        content.getLocationInWindow(cLoc);

        float d = activity.getResources().getDisplayMetrics().density;
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(0x99000000);
        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Highlight ring around the button
        TextView ring = new TextView(activity);
        android.graphics.drawable.GradientDrawable ringBg = new android.graphics.drawable.GradientDrawable();
        ringBg.setColor(0x00000000);
        ringBg.setCornerRadius(16 * d);
        ringBg.setStroke(4, 0xFFFF6B00);
        ring.setBackground(ringBg);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(
                fabAdd.getWidth() + (int)(24 * d), fabAdd.getHeight() + (int)(24 * d));
        ringLp.leftMargin = loc[0] - cLoc[0] - (int)(12 * d);
        ringLp.topMargin = loc[1] - cLoc[1] - (int)(12 * d);
        overlay.addView(ring, ringLp);
        ring.animate().scaleX(1.06f).scaleY(1.06f).setDuration(600)
                .withEndAction(() -> ring.animate().scaleX(1f).scaleY(1f).setDuration(600).start()).start();

        // Explanation card
        LinearLayout card = buildCard(activity,
                activity.getString(R.string.tutorial_hint_new_server_title),
                activity.getString(R.string.tutorial_hint_new_server_body));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.82f),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(card, cardLp);

        // Tap anywhere on the overlay dismisses it (the user then taps the real button)
        overlay.setOnClickListener(v -> overlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> content.removeView(overlay)).start());
    }

    /** Phase 2: walk through the server-manager tabs with one explanation per tab. */
    public static void maybeStartTabTour(Activity activity, TabLayout tabs) {
        if (App.getPrefs(activity).getInt("tutorial_phase", 0) != 2 || tabs == null) return;
        App.getPrefs(activity).edit().putInt("tutorial_phase", 0).apply();

        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(0x99000000);
        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int[] explanations = {
                R.string.tutorial_tab_dashboard, R.string.tutorial_tab_console,
                R.string.tutorial_tab_files, R.string.tutorial_tab_plugins, R.string.tutorial_tab_settings};
        final int[] current = {0};

        LinearLayout card = buildCard(activity, "", activity.getString(explanations[0]));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85f),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(card, cardLp);

        TextView btnNext = card.findViewWithTag("coach_next");
        btnNext.setOnClickListener(v -> {
            current[0]++;
            if (current[0] < tabs.getTabCount()) {
                TabLayout.Tab tab = tabs.getTabAt(current[0]);
                if (tab != null) tab.select();
                CharSequence t = tab != null && tab.getText() != null ? tab.getText().toString().toLowerCase() : "";
                ((TextView) card.findViewWithTag("coach_body")).setText(activity.getString(explanationFor(t.toString())));
            } else {
                // Finale: confetti + done card
                overlay.removeAllViews();
                LottieAnimationView confetti = new LottieAnimationView(activity);
                confetti.setAnimation(R.raw.tut_confetti);
                overlay.addView(confetti, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                confetti.playAnimation();
                LinearLayout done = buildCard(activity,
                        activity.getString(R.string.tutorial_done_title),
                        activity.getString(R.string.tutorial_done_body));
                overlay.addView(done, cardLp);
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.postDelayed(() -> overlay.animate().alpha(0f).setDuration(400)
                        .withEndAction(() -> content.removeView(overlay)).start(), 5200);
            }
        });

        // Open the first tab so the tour matches what the user sees
        TabLayout.Tab first = tabs.getTabAt(0);
        if (first != null) first.select();
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

    private static LinearLayout buildCard(Activity activity, String title, String body) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)(28 * activity.getResources().getDisplayMetrics().density),
                (int)(22 * activity.getResources().getDisplayMetrics().density),
                (int)(28 * activity.getResources().getDisplayMetrics().density),
                (int)(22 * activity.getResources().getDisplayMetrics().density));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF20E0E14);
        bg.setCornerRadius(16 * activity.getResources().getDisplayMetrics().density);
        bg.setStroke(2, 0x66FF6B00);
        card.setBackground(bg);

        if (title != null && !title.isEmpty()) {
            TextView tvTitle = new TextView(activity);
            tvTitle.setText(title);
            tvTitle.setTextColor(0xFFFF6B00);
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvTitle.setPadding(0, 0, 0, 10);
            card.addView(tvTitle);
        }

        TextView tvBody = new TextView(activity);
        tvBody.setTag("coach_body");
        tvBody.setText(body);
        tvBody.setTextColor(0xFFF0F0F0);
        tvBody.setTextSize(13);
        tvBody.setLineSpacing(4, 1.1f);
        card.addView(tvBody);

        com.google.android.material.button.MaterialButton next =
                KodaButtons.primary(activity, activity.getString(R.string.tutorial_next));
        next.setTag("coach_next");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * activity.getResources().getDisplayMetrics().density));
        lp.topMargin = (int)(18 * activity.getResources().getDisplayMetrics().density);
        card.addView(next, lp);
        return card;
    }
}
