package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;

/**
 * In-app tutorial coach: an orange mouse cursor points at the target and a
 * freely DRAGGABLE P.R.A.E.T.O.R.-styled card types its text live. The
 * overlay itself is pass-through (no dimming) so the real buttons stay
 * tappable; the NEW SERVER step advances when the user actually taps the
 * button, the tab tour has a NEXT button on the card.
 */
public class TutorialCoach {

    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void maybeStartTutorial(Activity activity) {
        if (App.getPrefs(activity).getBoolean("tutorial_completed_v2", false)) return;
        if (App.getPrefs(activity).getBoolean("tutorial_started", false)) return; // already ran once
        App.getPrefs(activity).edit().putBoolean("tutorial_started", true).apply();
        android.content.Intent intent = new android.content.Intent(activity, eu.kodanetwork.mchost.ui.TutorialActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    /** Phase 1: cursor hovers over NEW SERVER; user taps the real button to continue. */
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

        ui.showAt(cx, cy,
                activity.getString(R.string.tutorial_hint_new_server_title),
                activity.getString(R.string.tutorial_hint_new_server_body),
                false);
    }

    /** Phase 2: cursor walks through the tabs — WAITS until the freshly created
     *  server finished its setup (jar download, INSTALLING/SETTING_UP states). */
    public static void maybeStartTabTour(Activity activity, TabLayout tabs,
                                         eu.kodanetwork.mchost.model.ServerRepo repo, String serverId) {
        if (App.getPrefs(activity).getInt("tutorial_phase", 0) != 2 || tabs == null) return;
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] waited = {0};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            eu.kodanetwork.mchost.model.ServerInstance s = repo.byId(serverId);
            boolean busy = s != null && (s.state == eu.kodanetwork.mchost.model.ServerInstance.State.INSTALLING
                    || s.state == eu.kodanetwork.mchost.model.ServerInstance.State.SETTING_UP
                    || s.state == eu.kodanetwork.mchost.model.ServerInstance.State.STARTING);
            if (busy && waited[0] < 450) { // up to 15 minutes
                waited[0] += 2;
                h.postDelayed(poll[0], 2000);
            } else if (App.getPrefs(activity).getInt("tutorial_phase", 0) == 2) {
                startTabTourNow(activity, tabs);
            }
        };
        poll[0].run();
    }

    private static void startTabTourNow(Activity activity, TabLayout tabs) {
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
                    ui.showAt(cx, cy, t.toString(),
                            activity.getString(explanationFor(t.toString().toLowerCase())), true);
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

    // ── CoachUi: pass-through overlay, cursor, draggable typed card ─────────

    private static class CoachUi {
        private final Activity activity;
        private final ViewGroup content;
        private final FrameLayout overlay; // NOT clickable: real buttons stay tappable
        private final TextView cursor;
        private final LinearLayout card;
        private final TextView cardTitle;
        private final TextView cardBody;
        private Runnable onAdvance;

        CoachUi(Activity activity, ViewGroup content) {
            this.activity = activity;
            this.content = content;

            overlay = new FrameLayout(activity);
            overlay.setClickable(false);
            overlay.setFocusable(false);

            cursor = new TextView(activity);
            cursor.setText("➢");
            cursor.setTextSize(26);
            cursor.setTextColor(0xFFFF6B00);
            cursor.setRotation(-35f);
            cursor.setClickable(false);
            overlay.addView(cursor, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(40, 28, 40, 28);
            card.setBackgroundResource(eu.kodanetwork.mchost.R.drawable.bg_dialog_custom);
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.78f),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            cardLp.bottomMargin = (int)(110 * activity.getResources().getDisplayMetrics().density);
            card.setLayoutParams(cardLp);
            makeDraggable(card);

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

            overlay.addView(card);

            content.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        /** The card can be moved anywhere with a finger. */
        private void makeDraggable(View v) {
            v.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY, startX, startY;
                boolean moved;
                @Override
                public boolean onTouch(View view, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX(); downY = e.getRawY();
                            startX = view.getTranslationX(); startY = view.getTranslationY();
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                            if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                            view.setTranslationX(startX + dx);
                            view.setTranslationY(startY + dy);
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }

        void setOnAdvance(Runnable r) { this.onAdvance = r; }

        void showAt(float targetX, float targetY, String title, String body, boolean withNext) {
            // remove previous NEXT button, keep title/body
            View oldNext = card.findViewWithTag("coach_next");
            if (oldNext != null) card.removeView(oldNext);

            cursor.setAlpha(1f);
            cursor.setTranslationX(targetX + 26);
            cursor.setTranslationY(targetY - 18);
            cursor.animate().translationX(targetX + 34).translationY(targetY - 26)
                    .setDuration(600).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> cursor.animate()
                            .translationX(targetX + 26).translationY(targetY - 18)
                            .setDuration(600).start()).start();

            card.setAlpha(0f);
            card.setTranslationY(60);
            card.animate().alpha(1f).translationY(0).setDuration(300).start();
            cardTitle.setText(title);
            typewrite(cardBody, body);

            if (withNext) {
                com.google.android.material.button.MaterialButton next =
                        KodaButtons.primary(activity, activity.getString(R.string.tutorial_next));
                next.setTag("coach_next");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int)(46 * activity.getResources().getDisplayMetrics().density));
                lp.topMargin = (int)(16 * activity.getResources().getDisplayMetrics().density);
                card.addView(next, lp);
                next.setOnClickListener(v -> { if (onAdvance != null) onAdvance.run(); });
            }
        }

        void showFinale() {
            overlay.removeAllViews();

            LottieAnimationViewShim.show(activity, overlay);

            LinearLayout done = new LinearLayout(activity);
            done.setOrientation(LinearLayout.VERTICAL);
            done.setPadding(40, 32, 40, 32);
            done.setBackgroundResource(eu.kodanetwork.mchost.R.drawable.bg_dialog_custom);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.8f),
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

    /** Small shim so the confetti import stays local. */
    private static class LottieAnimationViewShim {
        static void show(Activity activity, FrameLayout overlay) {
            com.airbnb.lottie.LottieAnimationView confetti = new com.airbnb.lottie.LottieAnimationView(activity);
            confetti.setAnimation(eu.kodanetwork.mchost.R.raw.tut_confetti);
            overlay.addView(confetti, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            confetti.playAnimation();
        }
    }
}
