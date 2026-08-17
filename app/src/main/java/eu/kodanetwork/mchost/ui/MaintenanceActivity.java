package eu.kodanetwork.mchost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.kodanetwork.mchost.R;

public class MaintenanceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance);

        TextView reasonText = findViewById(R.id.maintenanceReasonText);
        TextView durationText = findViewById(R.id.maintenanceDurationText);

        Intent intent = getIntent();
        String reason = intent.getStringExtra("reason");
        int duration = intent.getIntExtra("duration", 60);

        if (reason != null && !reason.isEmpty()) {
            reasonText.setText(reason);
        }
        durationText.setText("Estimated duration: " + duration + " minutes");
    }

    @Override
    public void onBackPressed() {
        // Prevent users from exiting maintenance mode by pressing back
        // moveTaskToBack(true);
    }
}
