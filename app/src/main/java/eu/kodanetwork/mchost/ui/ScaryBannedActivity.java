package eu.kodanetwork.mchost.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.kodanetwork.mchost.R;
import android.content.Context;

public class ScaryBannedActivity extends AppCompatActivity {

    private Handler handler;
    private Runnable strobeRunnable;
    private boolean isRed = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Max brightness
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        window.setAttributes(layoutParams);

        setContentView(R.layout.activity_scary_banned);

        TextView hwidText = findViewById(R.id.bannedHwidText);
        String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "");
        hwidText.setText("HWID: " + uuid);

        // Setup strobe and glitch text
        final TextView glitchText = findViewById(R.id.glitchText);
        final String[] scaryTexts = {"SYSTEM COMPROMISED", "DATA PURGE IMMINENT", "HWID PERMANENTLY LOCKED", "SECURITY BREACH DETECTED", "FATAL ERROR"};
        final java.util.Random random = new java.util.Random();

        handler = new Handler();
        strobeRunnable = new Runnable() {
            @Override
            public void run() {
                getWindow().getDecorView().setBackgroundColor(isRed ? Color.RED : Color.BLACK);
                
                if (glitchText != null) {
                    // 15% chance to show glitch text
                    if (random.nextInt(100) < 15) {
                        glitchText.setVisibility(android.view.View.VISIBLE);
                        glitchText.setText(scaryTexts[random.nextInt(scaryTexts.length)]);
                        
                        // Random translation
                        glitchText.setTranslationX(random.nextInt(400) - 200);
                        glitchText.setTranslationY(random.nextInt(600) - 300);
                        
                        // Random scale
                        float scale = 0.5f + random.nextFloat() * 1.5f;
                        glitchText.setScaleX(scale);
                        glitchText.setScaleY(scale);
                    } else {
                        glitchText.setVisibility(android.view.View.INVISIBLE);
                    }
                }

                isRed = !isRed;
                handler.postDelayed(this, 80 + random.nextInt(40)); // Random fast strobe (80-120ms)
            }
        };
        handler.post(strobeRunnable);

        // Start GlitchBackgroundView
        eu.kodanetwork.mchost.ui.GlitchBackgroundView glitchView = findViewById(R.id.glitchBackgroundView);
        if (glitchView != null) {
            glitchView.startGlitch();
        }

        // Setup vibration
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 300, 100, 300, 100, 300, 100};
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // 0 means repeat
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && strobeRunnable != null) {
            handler.removeCallbacks(strobeRunnable);
        }
        
        eu.kodanetwork.mchost.ui.GlitchBackgroundView glitchView = findViewById(R.id.glitchBackgroundView);
        if (glitchView != null) {
            glitchView.stopGlitch();
        }

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent users from exiting banned screen
    }
}
