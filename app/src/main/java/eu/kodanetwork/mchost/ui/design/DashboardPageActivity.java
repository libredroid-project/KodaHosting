package eu.kodanetwork.mchost.ui.design;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.ui.MainActivity;

public class DashboardPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_design_dashboard);

        Button btnDownload = findViewById(R.id.btn_download_external);
        Button btnAdmin = findViewById(R.id.btn_admin_dashboard);
        Button btnServerPanel = findViewById(R.id.btn_open_server_panel);

        btnDownload.setOnClickListener(v -> {
            // Hook for Supabase signed artifact download.
        });
        btnAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminPageActivity.class)));
        btnServerPanel.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    }
}
