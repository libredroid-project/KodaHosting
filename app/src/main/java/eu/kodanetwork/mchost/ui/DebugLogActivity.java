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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);

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
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.removeListener(logListener);
    }
}
