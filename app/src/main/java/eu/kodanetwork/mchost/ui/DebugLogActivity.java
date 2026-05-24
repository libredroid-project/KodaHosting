package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.AppLogger;

public class DebugLogActivity extends AppCompatActivity {
    private TextView tvLog;

    private final AppLogger.Listener logListener = line -> {
        tvLog.append(line + "\n");
    };

    private String lastTheme = "modern";
    private String lastThemeMode = "dark";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);
        
        android.content.SharedPreferences prefs = getSharedPreferences("koda_settings", MODE_PRIVATE);
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

        tvLog = findViewById(R.id.tv_debug_log);
        MaterialButton btnCopy = findViewById(R.id.btn_copy_debug);
        MaterialButton btnBack = findViewById(R.id.btn_back_debug);

        btnBack.setOnClickListener(v -> finish());
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Koda Debug Log", AppLogger.getAllLogs());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Debug-Log kopiert!", Toast.LENGTH_SHORT).show();
        });

        tvLog.setText(AppLogger.getAllLogs());
        AppLogger.addListener(logListener);

        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences("koda_settings", MODE_PRIVATE);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode)) {
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.removeListener(logListener);
    }
}
