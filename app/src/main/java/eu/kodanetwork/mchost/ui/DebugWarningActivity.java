package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.kodanetwork.mchost.R;

public class DebugWarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep it secure and fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Screen protection removed

        setContentView(R.layout.activity_debug_warning);

        TextView countdownText = findViewById(R.id.countdownText);

        // 10 second countdown
        new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                countdownText.setText("SHUTDOWN IN " + seconds);
            }

            @Override
            public void onFinish() {
                countdownText.setText("SHUTTING DOWN...");
                countdownText.postDelayed(() -> {
                    finishAffinity();
                    System.exit(0);
                }, 500);
            }
        }.start();
    }

    @Override
    public void onBackPressed() {
        // Block back button
    }
}
