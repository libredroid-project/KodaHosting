package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.EditText;
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

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.AppLogger;

public class FileEditorActivity extends AppCompatActivity {

    private EditText etEditor;
    private File targetFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        targetFile = new File(path);

        etEditor = findViewById(R.id.et_editor);
        TextView tvFilename = findViewById(R.id.tv_filename);
        MaterialButton btnSave = findViewById(R.id.btn_save_editor);
        MaterialButton btnBack = findViewById(R.id.btn_back_editor);

        tvFilename.setText(targetFile.getName());

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveFile());

        loadFile();
    }

    private void loadFile() {
        if (!targetFile.exists()) {
            Toast.makeText(this, "Datei existiert nicht!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Only allow editing files smaller than 1MB to avoid OOM
        if (targetFile.length() > 1024 * 1024) {
            Toast.makeText(this, "Datei zu groß zum Bearbeiten!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        new Thread(() -> {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(targetFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                runOnUiThread(() -> etEditor.setText(content.toString()));
            } catch (IOException e) {
                AppLogger.log("Editor", "Load error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ladefehler: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveFile() {
        String content = etEditor.getText().toString();
        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                AppLogger.log("Editor", "Saved file: " + targetFile.getAbsolutePath());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Datei gespeichert!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (IOException e) {
                AppLogger.log("Editor", "Save error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Fehler beim Speichern: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
