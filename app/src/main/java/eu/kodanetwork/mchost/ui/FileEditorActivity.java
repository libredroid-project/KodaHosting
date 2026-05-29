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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

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

        etEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { syntaxHighlight(); }
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

    private void searchNext() {
        String query = etSearch.getText().toString();
        if (query.isEmpty()) return;
        String content = etEditor.getText().toString();
        int index = content.indexOf(query, lastSearchIndex);
        if (index == -1) { index = content.indexOf(query, 0); }
        if (index != -1) {
            etEditor.setSelection(index, index + query.length());
            etEditor.requestFocus();
            lastSearchIndex = index + query.length();
        } else {
            Toast.makeText(this, "Nicht gefunden", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFile() {
        if (!targetFile.exists()) { finish(); return; }
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
            try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> { Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show(); finish(); });
            } catch (IOException e) { runOnUiThread(() -> Toast.makeText(this, "Save Error", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        eu.kodanetwork.mchost.App.resetAfkTimer();
        return super.dispatchTouchEvent(ev);
    }
}
