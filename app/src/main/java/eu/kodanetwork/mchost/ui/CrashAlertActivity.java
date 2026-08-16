package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.animation.ObjectAnimator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.service.KodaServerService;

public class CrashAlertActivity extends Activity {
    private AudioTrack audioTrack;
    private volatile boolean playing = true;
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

        String id   = getIntent().getStringExtra("id");
        String name = getIntent().getStringExtra("name");
        ServerInstance srv = ServerRepo.get(this).byId(id);

        // ── Root ──────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0008);
        root.setFitsSystemWindows(true);

        // ── Header bar ────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(48, 60, 48, 36);
        header.setBackgroundColor(0xFF110008);

        // Pulsing red accent line at top
        android.view.View accentLine = new android.view.View(this);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(3));
        accentLine.setLayoutParams(lineParams);
        accentLine.setBackgroundColor(0xFFCC0033);
        root.addView(accentLine);

        // Warning icon
        TextView tvIcon = new TextView(this);
        tvIcon.setText("⚠");
        tvIcon.setTextSize(48f);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setTextColor(0xFFCC0033);
        header.addView(tvIcon);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(eu.kodanetwork.mchost.util.LocaleHelper.t(this,
            "SERVER CRASH", "SERVER ABGESTÜRZT"));
        tvTitle.setTextColor(0xFFFF3355);
        tvTitle.setTextSize(26f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setLetterSpacing(0.08f);
        tvTitle.setPadding(0, 12, 0, 0);
        header.addView(tvTitle);

        // Server name chip
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(0, 16, 0, 0);
        TextView tvNameLabel = new TextView(this);
        tvNameLabel.setText(eu.kodanetwork.mchost.util.LocaleHelper.t(this, "Server: ", "Server: "));
        tvNameLabel.setTextColor(0xFF888899);
        tvNameLabel.setTextSize(13f);
        chip.addView(tvNameLabel);
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Unknown");
        tvName.setTextColor(0xFFF0F0F0);
        tvName.setTextSize(13f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        chip.addView(tvName);
        header.addView(chip);

        root.addView(header);

        // Divider
        android.view.View divider = new android.view.View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
        divider.setBackgroundColor(0xFF330011);
        root.addView(divider);

        // ── Log section label ─────────────────────────────────────────────────
        TextView tvLogLabel = new TextView(this);
        tvLogLabel.setText(eu.kodanetwork.mchost.util.LocaleHelper.t(this,
            "CRASH LOG  (last 50 lines)", "ABSTURZPROTOKOLL  (letzte 50 Zeilen)"));
        tvLogLabel.setTextColor(0xFF555566);
        tvLogLabel.setTextSize(10f);
        tvLogLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvLogLabel.setLetterSpacing(0.1f);
        tvLogLabel.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(8));
        root.addView(tvLogLabel);

        // ── Log scroll area ───────────────────────────────────────────────────
        ScrollView scrollLog = new ScrollView(this);
        scrollLog.setBackgroundColor(0xFF06060C);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(16));
        scrollLog.setLayoutParams(scrollParams);

        TextView tvLog = new TextView(this);
        tvLog.setTextColor(0xFFAAAAAA);
        tvLog.setTextSize(10.5f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setLineSpacing(4f, 1f);
        tvLog.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        tvLog.setText(getLastLogs(srv));
        scrollLog.addView(tvLog);

        // Scroll to bottom after layout
        scrollLog.post(() -> scrollLog.fullScroll(android.view.View.FOCUS_DOWN));
        root.addView(scrollLog);

        // ── Buttons ───────────────────────────────────────────────────────────
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setGravity(Gravity.CENTER);
        btnBar.setPadding(dpToPx(20), 0, dpToPx(20), dpToPx(32));
        btnBar.setBackgroundColor(0xFF0A0008);

        Button btnDismiss = new Button(this);
        btnDismiss.setText(eu.kodanetwork.mchost.util.LocaleHelper.t(this, "DISMISS", "SCHLIESSEN"));
        btnDismiss.setBackgroundColor(0xFF1E1E28);
        btnDismiss.setTextColor(0xFF888899);
        btnDismiss.setTypeface(Typeface.DEFAULT_BOLD);
        btnDismiss.setTextSize(12f);
        btnDismiss.setStateListAnimator(null);
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
            0, dpToPx(52), 1.0f);
        dismissParams.setMargins(0, 0, dpToPx(10), 0);
        btnDismiss.setLayoutParams(dismissParams);
        btnDismiss.setOnClickListener(v -> finish());
        btnBar.addView(btnDismiss);

        Button btnRestart = new Button(this);
        btnRestart.setText(eu.kodanetwork.mchost.util.LocaleHelper.t(this, "RESTART", "NEUSTART"));
        btnRestart.setBackgroundColor(0xFFCC0033);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setTypeface(Typeface.DEFAULT_BOLD);
        btnRestart.setTextSize(12f);
        btnRestart.setStateListAnimator(null);
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(
            0, dpToPx(52), 1.0f);
        restartParams.setMargins(dpToPx(10), 0, 0, 0);
        btnRestart.setLayoutParams(restartParams);
        btnRestart.setOnClickListener(v -> {
            Intent si = new Intent(this, KodaServerService.class);
            si.setAction(KodaServerService.ACTION_START);
            si.putExtra(KodaServerService.EXTRA_ID, id);
            startService(si);
            finish();
        });
        btnBar.addView(btnRestart);

        root.addView(btnBar);

        setContentView(root);

        // Subtle pulse animation on header background only
        anim = ObjectAnimator.ofInt(header, "backgroundColor",
            0xFF110008, 0xFF1F000F);
        anim.setDuration(900);
        anim.setEvaluator(new ArgbEvaluator());
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.start();

        // Play alarm sound only if device is not in silent/DND mode
        if (shouldPlaySound()) {
            playCustomAlarm();
        }

        // Auto-dismiss after 60 seconds
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 60000);
    }

    /** Returns true only if device is in a normal/ring audio mode (not silent or vibrate). */
    private boolean shouldPlaySound() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;

        int ringerMode = am.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_SILENT ||
            ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return false;
        }

        // Also check Do Not Disturb on API 23+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                int filter = nm.getCurrentInterruptionFilter();
                if (filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
                    filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Reads the last 50 lines from server log.
     * Tries server.log first, then logs/latest.log (Minecraft standard).
     */
    private String getLastLogs(ServerInstance srv) {
        if (srv == null) return "Unknown server.";

        File serverDir = new File(srv.getServerDir());
        // Possible log locations in priority order
        File[] candidates = {
            new File(serverDir, "server.log"),
            new File(serverDir, "logs/latest.log"),
            new File(serverDir, "logs/latest.log.gz"),
        };

        File logFile = null;
        for (File f : candidates) {
            if (f.exists() && f.length() > 0) {
                logFile = f;
                break;
            }
        }

        if (logFile == null) {
            // Log not ready yet - show info from service memory
            return eu.kodanetwork.mchost.util.LocaleHelper.t(this,
                "Log file not available yet.\n\nThe server may have crashed immediately on startup.\n\nCheck the console tab for details.",
                "Logdatei noch nicht verfügbar.\n\nDer Server ist möglicherweise sofort beim Start abgestürzt.\n\nPrüfe die Konsole für Details.");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            java.util.LinkedList<String> lines = new java.util.LinkedList<>();
            String line;
            while ((line = br.readLine()) != null) {
                // Strip ANSI escape codes
                line = line.replaceAll("(?:\\x1B|\\u001B)\\[[;\\d]*[a-zA-Z]", "");
                // Skip empty lines from ANSI stripping
                if (line.trim().isEmpty()) continue;
                lines.add(line);
                if (lines.size() > 50) lines.removeFirst();
            }
            for (String l : lines) sb.append(l).append("\n");
        } catch (Exception e) {
            return "Failed to read log: " + e.getMessage();
        }

        String result = sb.toString().trim();
        return result.isEmpty()
            ? eu.kodanetwork.mchost.util.LocaleHelper.t(this, "(log is empty)", "(Log ist leer)")
            : result;
    }

    private void playCustomAlarm() {
        int sampleRate = 44100;
        int bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

        audioTrack = new AudioTrack(
            AudioManager.STREAM_ALARM, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            bufferSize, AudioTrack.MODE_STREAM);

        // Set volume to 70% of max alarm volume, not full blast
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, (int)(max * 0.7f), 0);
        }

        audioTrack.play();

        new Thread(() -> {
            short[] buf = new short[bufferSize];
            double phase = 0;
            double freq = 600;
            boolean rising = true;
            while (playing) {
                for (int i = 0; i < bufferSize; i++) {
                    buf[i] = (short)(Math.sin(phase) * Short.MAX_VALUE * 0.6); // 60% amplitude
                    phase += 2 * Math.PI * freq / sampleRate;
                    if (rising) {
                        freq += 0.15;
                        if (freq > 1100) rising = false;
                    } else {
                        freq -= 0.15;
                        if (freq < 600) rising = true;
                    }
                }
                try { audioTrack.write(buf, 0, bufferSize); } catch (Exception ignored) {}
            }
        }).start();
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        playing = false;
        if (audioTrack != null) {
            try { audioTrack.stop(); audioTrack.release(); } catch (Exception ignored) {}
        }
        if (anim != null) anim.cancel();
        super.onDestroy();
    }
}
