package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.kodanetwork.mchost.R;

public class BannedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_banned_m3 : R.layout.activity_banned);

        TextView hwidText = findViewById(R.id.bannedHwidText);
        String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "");
        hwidText.setText("HWID: " + uuid);
        
        TextView reasonText = findViewById(R.id.bannedReasonText);
        String reason = getIntent().getStringExtra("reason");
        if (reason == null) {
            reason = eu.kodanetwork.mchost.App.getPrefs(this).getString("BANNED_REASON", null);
        }
        if (reason != null && !reason.isEmpty()) {
            reasonText.setText(reason);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent users from exiting banned screen
    }
}
