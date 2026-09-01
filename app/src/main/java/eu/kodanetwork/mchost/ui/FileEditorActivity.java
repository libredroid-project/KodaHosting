package eu.kodanetwork.mchost.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.AppLogger;

public class FileEditorActivity extends AppCompatActivity {

    private EditText etEditor, etSearch;
    private File targetFile;
    private int lastSearchIndex = 0;

    private String lastTheme = "modern";
    private String lastThemeMode = "dark";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_file_editor_m3 : R.layout.activity_file_editor);

        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        lastTheme = prefs.getString("app_theme", "modern");
        lastThemeMode = prefs.getString("theme_mode", "dark");

        // Apply light mode background early
        if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
            findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().setStatusBarColor(0xFFF5F5F5);
                getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().setNavigationBarColor(0xFFF5F5F5);
            }
        }

        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        targetFile = new File(path);

        etEditor = findViewById(R.id.et_editor);
        etSearch = findViewById(R.id.et_search);
        TextView tvFilename = findViewById(R.id.tv_filename);
        MaterialButton btnSave = findViewById(R.id.btn_save_editor);
        MaterialButton btnBack = findViewById(R.id.btn_back_editor);
        ImageButton btnSearchNext = findViewById(R.id.btn_search_next);

        tvFilename.setText(targetFile.getName());

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveFile());
        btnSearchNext.setOnClickListener(v -> searchNext());
        ImageButton btnSearchPrev = findViewById(R.id.btn_search_prev);
        if (btnSearchPrev != null) btnSearchPrev.setOnClickListener(v -> searchPrev());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                lastSearchIndex = 0;
                rebuildMatches(e.toString());
                if (!matchPositions.isEmpty()) jumpToMatch(0 - 1 + 1); // jump to first
            }
        });
        // rebuild match list after content changes too
        etEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                syntaxHighlight();
                rebuildMatches(etSearch.getText().toString());
            }
        });

        loadFile();
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode)) {
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            recreate();
        }
    }

    private void syntaxHighlight() {
        // full-text regex over huge files freezes scrolling — skip for big content
        if (etEditor.length() > 200_000) return;
        String content = etEditor.getText().toString();
        Spannable spannable = etEditor.getText();
        ForegroundColorSpan[] spans = spannable.getSpans(0, content.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) spannable.removeSpan(span);

        highlight(spannable, "\"[^\"]*\"|'[^']*'", 0xFFCE9178);
        highlight(spannable, "\\b\\d+\\b", 0xFFB5CEA8);
        highlight(spannable, "(?m)^\\s*[a-zA-Z0-9_-]+(?=:)", 0xFF9CDCFE);
        highlight(spannable, "#.*|//.*", 0xFF6A9955);
        highlight(spannable, "\\b(true|false|null)\\b", 0xFF569CD6);
    }

    private void highlight(Spannable s, String regex, int color) {
        try {
            Matcher m = Pattern.compile(regex).matcher(s.toString());
            while (m.find()) s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception ignored) {}
    }

    private int totalMatches = 0;
    private java.util.ArrayList<Integer> matchPositions = new java.util.ArrayList<>();
    private int matchCursor = -1;

    private void rebuildMatches(String query) {
        matchPositions.clear();
        matchCursor = -1;
        if (query != null && !query.isEmpty()) {
            String content = etEditor.getText().toString();
            String lower = content.toLowerCase();
            String q = query.toLowerCase();
            int idx = 0;
            while ((idx = lower.indexOf(q, idx)) != -1) {
                matchPositions.add(idx);
                idx += q.length();
            }
        }
        updateMatchCounter();
    }

    private void updateMatchCounter() {
        TextView tvCount = findViewById(R.id.tv_match_count);
        if (tvCount == null) return;
        tvCount.setText(matchPositions.isEmpty()
                ? ""
                : (matchCursor + 1) + "/" + matchPositions.size());
    }

    private void jumpToMatch(int dir) {
        if (matchPositions.isEmpty()) { updateMatchCounter(); return; }
        matchCursor = (matchCursor + dir + matchPositions.size()) % matchPositions.size();
        int index = matchPositions.get(matchCursor);
        int len = etSearch.getText().toString().length();
        etEditor.requestFocus();
        etEditor.setSelection(index, index + len);
        // auto-scroll the surrounding NestedScrollView to the hit line
        etEditor.post(() -> {
            if (etEditor.getLayout() == null) return;
            int line = etEditor.getLayout().getLineForOffset(index);
            int y = etEditor.getLayout().getLineTop(line) - etEditor.getHeight() / 3;
            android.view.View sv = findViewById(R.id.scroll_editor);
            if (sv instanceof androidx.core.widget.NestedScrollView) {
                ((androidx.core.widget.NestedScrollView) sv).smoothScrollTo(0, Math.max(0, y));
            }
        });
        updateMatchCounter();
    }

    private void searchNext() { jumpToMatch(1); }
    private void searchPrev() { jumpToMatch(-1); }

    /** Extensions that never open in the text editor (binaries / blocked per request). */
    private static final String[] BLOCKED_EXT = {
            ".jar", ".java", ".class", ".exe", ".dll", ".so", ".bin",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico",
            ".zip", ".gz", ".tar", ".xz", ".rar", ".7z",
            ".db", ".sqlite", ".dat", ".mca", ".region", ".mcr",
            ".mp3", ".ogg", ".wav", ".mp4", ".flac"};

    private boolean isBlocked(String name) {
        String n = name.toLowerCase();
        for (String ext : BLOCKED_EXT) if (n.endsWith(ext)) return true;
        return false;
    }

    private void loadFile() {
        if (!targetFile.exists()) { finish(); return; }
        if (isBlocked(targetFile.getName())) {
            Toast.makeText(this, "Dieser Dateityp kann hier nicht geöffnet werden", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        if (targetFile.length() > 1024 * 1024) { Toast.makeText(this, "File too large!", Toast.LENGTH_LONG).show(); finish(); return; }

        new Thread(() -> {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(targetFile))) {
                String line;
                while ((line = reader.readLine()) != null) content.append(line).append("\n");
                runOnUiThread(() -> {
                    etEditor.setText(content.toString());
                });
            } catch (IOException e) { runOnUiThread(() -> Toast.makeText(this, "Load Error", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    private void saveFile() {
        String content = etEditor.getText().toString();
        new Thread(() -> {
            try {
                String out = content;
                if ("server.properties".equals(targetFile.getName())) {
                    out = protectManagedProps(content);
                }
                try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
                    fos.write(out.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> { Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show(); finish(); });
            } catch (IOException e) { runOnUiThread(() -> Toast.makeText(this, "Save Error", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    /**
     * server-port and server-ip are app-managed (tunnel/DNS wiring depends on
     * them); a manual change breaks the whole app. Re-applies the values from
     * the file on disk and warns the user when an edit was reverted.
     */
    private String protectManagedProps(String newContent) {
        java.util.Map<String, String> original = new java.util.HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(targetFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("server-port=") || t.startsWith("server-ip=")) {
                    int eq = t.indexOf('=');
                    original.put(t.substring(0, eq), t.substring(eq + 1));
                }
            }
        } catch (IOException ignored) {}

        if (original.isEmpty()) return newContent;

        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean changed = false;
        for (String line : newContent.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("server-port=") || t.startsWith("server-ip=")) {
                int eq = t.indexOf('=');
                String key = t.substring(0, eq);
                seen.add(key);
                String keep = key + "=" + original.get(key);
                if (!t.equals(keep)) changed = true;
                sb.append(keep);
            } else {
                sb.append(line);
            }
            sb.append("\n");
        }
        // Re-add the keys if the user deleted their lines entirely
        for (java.util.Map.Entry<String, String> e : original.entrySet()) {
            if (!seen.contains(e.getKey())) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
                changed = true;
            }
        }
        if (changed) {
            runOnUiThread(() -> Toast.makeText(this, getString(R.string.sd_props_port_ip_managed), Toast.LENGTH_LONG).show());
        }
        return sb.toString();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        eu.kodanetwork.mchost.App.resetAfkTimer();
        return super.dispatchTouchEvent(ev);
    }
}
