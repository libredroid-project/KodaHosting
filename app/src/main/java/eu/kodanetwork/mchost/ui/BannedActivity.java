package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.security.KodaIntegrityHelper;

public class BannedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banned);

        TextView hwidText = findViewById(R.id.bannedHwidText);
        String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "");
        hwidText.setText("HWID: " + uuid);
    }

    @Override
    public void onBackPressed() {
        // Prevent users from exiting banned screen
    }
}
