package eu.kodanetwork.mchost.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.service.KodaServerService;
import eu.kodanetwork.mchost.util.CrashFixer;
import eu.kodanetwork.mchost.util.HapticUtil;

import java.io.*;
import java.util.LinkedList;

/**
 * Full-screen PRAETOR-style crash alert with cause analysis, fix button, and crash log.
 * Replaces the old alarm-based CrashAlertActivity.
 */
public class CrashAlertActivity extends androidx.appcompat.app.AppCompatActivity {
    private ObjectAnimator anim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        // ── Extract intent data ──────────────────────────────────────────────
        String id            = getIntent().getStringExtra("id");
        String name          = getIntent().getStringExtra("name");
        String crashReason   = getIntent().getStringExtra("crashReason");
        String crashCategory = getIntent().getStringExtra("crashCategory");
        String crashFix      = getIntent().getStringExtra("crashFix");
        String crashFixAction= getIntent().getStringExtra("crashFixAction");
        String crashStack    = getIntent().getStringExtra("crashStackTrace");
        int    crashExitCode = getIntent().getIntExtra("crashExitCode", 0);
        String serverType    = getIntent().getStringExtra("serverType");
        String serverVersion = getIntent().getStringExtra("serverVersion");
        int    serverRam     = getIntent().getIntExtra("serverRam", 0);
        int    serverPort    = getIntent().getIntExtra("serverPort", 0);
        ServerInstance srv   = ServerRepo.get(this).byId(id);
        
        if (srv != null) {
            if (crashReason == null) crashReason = srv.crashReason;
            if (crashCategory == null) crashCategory = srv.crashCategory;
            if (crashFix == null) crashFix = srv.crashFix;
            if (crashFixAction == null) crashFixAction = srv.crashFixAction;
            if (crashStack == null) crashStack = srv.crashStackTrace;
            if (crashExitCode == 0) crashExitCode = srv.crashExitCode;
            if (serverType == null) serverType = srv.getType().name();
            if (serverVersion == null) serverVersion = srv.getVersion();
            if (serverRam == 0) serverRam = srv.getRamMB();
            if (serverPort == 0) serverPort = srv.getPort();
        }

        // ── Vibration feedback (no alarm sound) ──────────────────────────────
        HapticUtil.forceVibrate(this, 200);
        new Handler(Looper.getMainLooper()).postDelayed(() -> HapticUtil.forceVibrate(this, 300), 300);
        new Handler(Looper.getMainLooper()).postDelayed(() -> HapticUtil.forceVibrate(this, 150), 700);

        Typeface kodaFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_regular);
        Typeface kodaBold = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_bold);

        // ── Root layout ──────────────────────────────────────────────────────
        getWindow().getDecorView().setBackgroundColor(0xFF0F0808); // Dark reddish black background
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F0808);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));

        // Use root directly instead of card
        LinearLayout card = root;

        // ── P.R.A.E.T.O.R. title ────────────────────────────────────────────
        TextView tvPraetor = new TextView(this);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvPraetor.setText(Html.fromHtml(praetorHtml, Html.FROM_HTML_MODE_LEGACY));
        tvPraetor.setTextSize(32f);
        tvPraetor.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
        tvPraetor.setLetterSpacing(0.1f);
        tvPraetor.setGravity(Gravity.CENTER);
        card.addView(tvPraetor);

        // ── Subtitle ─────────────────────────────────────────────────────────
        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(getString(R.string.crash_subtitle));
        tvSubtitle.setTextColor(0xFF8A8A9A);
        tvSubtitle.setTextSize(9f);
        tvSubtitle.setTypeface(kodaFont != null ? kodaFont : Typeface.DEFAULT_BOLD, Typeface.BOLD);
        tvSubtitle.setLetterSpacing(0.05f);
        tvSubtitle.setGravity(Gravity.CENTER);
        tvSubtitle.setPadding(0, dp(6), 0, 0);
        card.addView(tvSubtitle);

        // ── Orange divider ───────────────────────────────────────────────────
        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(dp(40), dp(2));
        divParams.setMargins(0, dp(20), 0, dp(20));
        divParams.gravity = Gravity.CENTER;
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(0xFFFF6B00);
        card.addView(divider);

        // ── Warning icon (pulsing) ───────────────────────────────────────────
        TextView tvIcon = new TextView(this);
        tvIcon.setText("⚠");
        tvIcon.setTextSize(44f);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setTextColor(0xFFFF3333);
        card.addView(tvIcon);
        tvIcon.startAnimation(new android.view.animation.AlphaAnimation(1.0f, 0.0f) {{
            setDuration(300);
            setRepeatMode(android.view.animation.Animation.REVERSE);
            setRepeatCount(5);
        }});

        // ── CRASH title ──────────────────────────────────────────────────────
        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.crash_title));
        tvTitle.setTextColor(0xFFFF3355);
        tvTitle.setTextSize(24f);
        tvTitle.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setLetterSpacing(0.06f);
        tvTitle.setPadding(0, dp(8), 0, 0);
        card.addView(tvTitle);

        // ── Server info chip ─────────────────────────────────────────────────
        String typeDisplay = serverType != null ? serverType : "Unknown";
        String verDisplay = serverVersion != null ? serverVersion : "";
        TextView tvServerInfo = new TextView(this);
        tvServerInfo.setText(getString(R.string.crash_server_info, name != null ? name : "Unknown", typeDisplay, verDisplay));
        tvServerInfo.setTextColor(0xFF888899);
        tvServerInfo.setTextSize(12f);
        tvServerInfo.setGravity(Gravity.CENTER);
        tvServerInfo.setPadding(0, dp(6), 0, 0);
        card.addView(tvServerInfo);

        // ── Exit code badge ──────────────────────────────────────────────────
        if (crashExitCode != 0) {
            TextView tvExitCode = new TextView(this);
            tvExitCode.setText(getString(R.string.crash_exit_code, crashExitCode));
            tvExitCode.setTextColor(0xFFFF6B00);
            tvExitCode.setTextSize(10f);
            tvExitCode.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvExitCode.setGravity(Gravity.CENTER);
            tvExitCode.setLetterSpacing(0.08f);
            tvExitCode.setPadding(0, dp(6), 0, 0);
            card.addView(tvExitCode);
        }

        // ══════════════════════════════════════════════════════════════════════
        //  CAUSE CARD
        // ══════════════════════════════════════════════════════════════════════
        if (crashCategory != null || crashReason != null) {
            LinearLayout causeCard = new LinearLayout(this);
            causeCard.setOrientation(LinearLayout.VERTICAL);
            causeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams causeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            causeParams.setMargins(0, dp(20), 0, 0);
            causeCard.setLayoutParams(causeParams);

            // Cause label
            TextView tvCauseLabel = new TextView(this);
            tvCauseLabel.setText(getString(R.string.crash_cause_label).toUpperCase());
            tvCauseLabel.setTextColor(0xFFFF5555);
            tvCauseLabel.setTextSize(10f);
            tvCauseLabel.setTypeface(kodaFont, Typeface.BOLD);
            tvCauseLabel.setLetterSpacing(0.15f);
            causeCard.addView(tvCauseLabel);

            // Category name (big)
            String categoryDisplay = getCategoryDisplayName(crashCategory);
            TextView tvCategory = new TextView(this);
            tvCategory.setText(categoryDisplay);
            tvCategory.setTextColor(0xFFF0F0F0);
            tvCategory.setTextSize(18f);
            tvCategory.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
            tvCategory.setPadding(0, dp(8), 0, 0);
            causeCard.addView(tvCategory);

            // Reason explanation
            if (crashReason != null) {
                String reasonDisplay = getReasonDisplayText(crashCategory, crashReason, serverRam, serverPort, crashExitCode);
                TextView tvReason = new TextView(this);
                tvReason.setText(reasonDisplay);
                tvReason.setTextColor(0xFFAAAAAA);
                tvReason.setTextSize(13f);
                tvReason.setLineSpacing(4f, 1f);
                tvReason.setPadding(0, dp(8), 0, 0);
                causeCard.addView(tvReason);
            }

            // ── Fix Button ───────────────────────────────────────────────────
            if (crashFixAction != null && crashFix != null) {
                com.google.android.material.button.MaterialButton btnFix = new com.google.android.material.button.MaterialButton(this);
                btnFix.setText(getString(R.string.crash_btn_fix, crashFix));
                btnFix.setTextColor(0xFFFFFFFF);
                btnFix.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                btnFix.setCornerRadius(dp(8));
                btnFix.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
                
                LinearLayout.LayoutParams fixParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
                fixParams.setMargins(0, dp(14), 0, 0);
                btnFix.setLayoutParams(fixParams);
                btnFix.setTextSize(12f);
                final String fixActionFinal = crashFixAction;
                btnFix.setOnClickListener(v -> {
                    HapticUtil.forceVibrate(this, 80);
                    if (srv != null) {
                        CrashFixer.FixResult result = CrashFixer.executeFix(this, srv, fixActionFinal);
                        if (result.success) {
                            Toast.makeText(this, getString(R.string.crash_fix_success, result.message), Toast.LENGTH_LONG).show();
                            btnFix.setEnabled(false);
                            btnFix.setText(result.message.toUpperCase());
                            btnFix.setTextColor(0xFF00E676);
                            btnFix.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0A2E15));
                            new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 1200);
                        } else {
                            Toast.makeText(this, getString(R.string.crash_fix_failed, result.message), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                causeCard.addView(btnFix);
            }

            card.addView(causeCard);
        }

        // ══════════════════════════════════════════════════════════════════════
        //  CRASH LOG (collapsible)
        // ══════════════════════════════════════════════════════════════════════
        TextView tvLogLabel = new TextView(this);
        tvLogLabel.setText(getString(R.string.crash_log_label).toUpperCase() + " (TAP TO TOGGLE)");
        tvLogLabel.setTextColor(0xFF777788);
        tvLogLabel.setTextSize(10f);
        tvLogLabel.setTypeface(kodaFont, Typeface.BOLD);
        tvLogLabel.setLetterSpacing(0.08f);
        tvLogLabel.setPadding(0, dp(20), 0, dp(8));
        card.addView(tvLogLabel);

        ScrollView scrollLog = new ScrollView(this);
        scrollLog.setBackgroundColor(0xFF06060C);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setCornerRadius(dp(8));
        logBg.setColor(0xFF06060C);
        scrollLog.setBackground(logBg);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        scrollParams.setMargins(0, 0, 0, 0);
        scrollLog.setLayoutParams(scrollParams);

        TextView tvLog = new TextView(this);
        tvLog.setTextColor(0xFFAAAAAA);
        tvLog.setTextSize(9.5f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setLineSpacing(3f, 1f);
        tvLog.setPadding(dp(10), dp(10), dp(10), dp(10));
        // Prefer stack trace if available, otherwise read log file
        if (crashStack != null && !crashStack.isEmpty()) {
            tvLog.setText(crashStack);
        } else {
            tvLog.setText(getLastLogs(srv));
        }
        scrollLog.addView(tvLog);

        // Toggle expand/collapse on label click
        tvLogLabel.setOnClickListener(v -> {
            if (scrollLog.getVisibility() == View.VISIBLE) {
                scrollLog.setVisibility(View.GONE);
            } else {
                scrollLog.setVisibility(View.VISIBLE);
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        });
        card.addView(scrollLog);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));

        // ══════════════════════════════════════════════════════════════════════
        //  BUTTONS
        // ══════════════════════════════════════════════════════════════════════
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnBarParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnBarParams.setMargins(0, dp(20), 0, 0);
        btnBar.setLayoutParams(btnBarParams);

        // Dismiss button
        com.google.android.material.button.MaterialButton btnDismiss = new com.google.android.material.button.MaterialButton(this);
        btnDismiss.setText(getString(R.string.crash_btn_dismiss));
        btnDismiss.setTextColor(0xFFBBBBBB);
        btnDismiss.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2A33));
        btnDismiss.setCornerRadius(dp(8));
        btnDismiss.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        dismissParams.setMargins(0, 0, dp(8), 0);
        btnDismiss.setLayoutParams(dismissParams);
        btnDismiss.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 40);
            finish();
        });
        btnBar.addView(btnDismiss);
        
        // Full Logs button
        com.google.android.material.button.MaterialButton btnFullLogs = new com.google.android.material.button.MaterialButton(this);
        btnFullLogs.setText("FULL LOGS");
        btnFullLogs.setTextColor(0xFFFFFFFF);
        btnFullLogs.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF333344));
        btnFullLogs.setCornerRadius(dp(8));
        btnFullLogs.setTypeface(kodaBold != null ? kodaBold : Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams fullLogsParams = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        fullLogsParams.setMargins(dp(8), 0, 0, 0);
        btnFullLogs.setLayoutParams(fullLogsParams);
        btnFullLogs.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 40);
            if (srv != null) {
                Intent logIntent = new Intent(this, eu.kodanetwork.mchost.ui.FileEditorActivity.class);
                logIntent.putExtra("filePath", srv.getServerDir() + "/logs/latest.log");
                logIntent.putExtra("title", "Server Logs");
                logIntent.putExtra("readOnly", true);
                startActivity(logIntent);
            }
        });
        btnBar.addView(btnFullLogs);

        card.addView(btnBar);

        // root.addView(card); // removed because root IS card
        scroll.addView(root);
        setContentView(scroll);

        // ── Subtle pulse on the background ──────────────────────────────────
        anim = ObjectAnimator.ofInt(getWindow().getDecorView(), "backgroundColor", 0xFF0F0808, 0xFF1A0A0A);
        anim.setDuration(1200);
        anim.setEvaluator(new ArgbEvaluator());
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.start();

        // Auto-dismiss after 2 minutes
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 120000);
    }

    /** Resolve crash category key to localized display name. */
    private String getCategoryDisplayName(String category) {
        if (category == null) return getString(R.string.crash_category_unknown);
        switch (category) {
            case "EULA":           return getString(R.string.crash_category_eula);
            case "OOM":            return getString(R.string.crash_category_oom);
            case "PORT":           return getString(R.string.crash_category_port);
            case "MOD_CRASH":      return getString(R.string.crash_category_mod_crash);
            case "MISSING_JAR":    return getString(R.string.crash_category_missing_jar);
            case "JAVA_VERSION":   return getString(R.string.crash_category_java_version);
            case "PERMISSION":     return getString(R.string.crash_category_permission);
            case "STACK_OVERFLOW": return getString(R.string.crash_category_stack_overflow);
            case "WORLD_CORRUPT":  return getString(R.string.crash_category_world_corrupt);
            case "CONFIG_INVALID": return getString(R.string.crash_category_config_invalid);
            case "OS_KILLED":      return getString(R.string.crash_category_os_killed);
            case "NATIVE_LIB":     return getString(R.string.crash_category_native_lib);
            case "CLASS_NOT_FOUND": return getString(R.string.crash_category_class_not_found);
            case "PLUGIN_CONFIG":  return getString(R.string.crash_category_plugin_config);
            case "STORAGE":        return getString(R.string.crash_category_storage);
            default:               return getString(R.string.crash_category_unknown);
        }
    }

    /** Get a rich localized reason text using string resources with parameters. */
    private String getReasonDisplayText(String category, String rawReason, int ram, int port, int exitCode) {
        if (category == null) return rawReason != null ? rawReason : "";
        switch (category) {
            case "EULA":           return getString(R.string.crash_reason_eula);
            case "OOM":            return getString(R.string.crash_reason_oom, ram);
            case "PORT":           return getString(R.string.crash_reason_port, port);
            case "MOD_CRASH":      return getString(R.string.crash_reason_mod_crash);
            case "MISSING_JAR":    return getString(R.string.crash_reason_missing_jar);
            case "JAVA_VERSION":   return getString(R.string.crash_reason_java_version);
            case "PERMISSION":     return getString(R.string.crash_reason_permission);
            case "STACK_OVERFLOW": return getString(R.string.crash_reason_stack_overflow);
            case "WORLD_CORRUPT":  return getString(R.string.crash_reason_world_corrupt);
            case "CONFIG_INVALID": return getString(R.string.crash_reason_config_invalid);
            case "OS_KILLED":      return getString(R.string.crash_reason_os_killed);
            case "NATIVE_LIB":     return getString(R.string.crash_reason_native_lib);
            case "CLASS_NOT_FOUND": return getString(R.string.crash_reason_class_not_found);
            case "PLUGIN_CONFIG":  return getString(R.string.crash_reason_plugin_config);
            case "STORAGE":        return getString(R.string.crash_reason_storage);
            case "UNKNOWN":
                if (exitCode != 0) return getString(R.string.crash_reason_unknown, exitCode);
                return getString(R.string.crash_reason_startup_exit);
            default: return rawReason != null ? rawReason : "";
        }
    }

    /** Read last 50 lines from server log. */
    private String getLastLogs(ServerInstance srv) {
        if (srv == null) return getString(R.string.crash_log_unavailable);
        File serverDir = new File(srv.getServerDir());
        File[] candidates = {
            new File(serverDir, "server.log"),
            new File(serverDir, "logs/latest.log"),
        };
        File logFile = null;
        for (File f : candidates) {
            if (f.exists() && f.length() > 0) { logFile = f; break; }
        }
        if (logFile == null) return getString(R.string.crash_log_unavailable);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            LinkedList<String> lines = new LinkedList<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.replaceAll("(?:\\\\x1B|\\\\u001B)\\[[;\\\\d]*[a-zA-Z]", "");
                if (line.trim().isEmpty()) continue;
                lines.add(line);
                if (lines.size() > 50) lines.removeFirst();
            }
            for (String l : lines) sb.append(l).append("\n");
        } catch (Exception e) {
            return "Failed to read log: " + e.getMessage();
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? getString(R.string.crash_log_empty) : result;
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (anim != null) anim.cancel();
        super.onDestroy();
    }
}
