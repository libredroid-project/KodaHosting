package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

import eu.kodanetwork.mchost.util.HapticUtil;

import android.view.View;
import android.widget.EditText;
import eu.kodanetwork.mchost.R;

public class LegacyModeWarningActivity extends Activity {

    private int currentStage = 1;
    private CountDownTimer timer;
    private Button btnAction;
    private TextView tvCountdown;
    private EditText etMath;
    private int mathTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_praetor_warning);
        showStage1();
    }

    private void showStage1() {
        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText("You are attempting to enable LEGACY MODE.\n\n" +
            "This mode is extremely outdated, much slower, and highly unstable compared to the Standard Engine.\n\n" +
            "It is no longer supported and is only left here for specific, technical edge cases.\n\n" +
            "Note: If this app is downloaded from the Google Play Store, this mode will NOT work.");

        btnAction = findViewById(R.id.btn_praetor_action);
        btnAction.setText("NEXT");
        btnAction.setEnabled(false);
        btnAction.setBackgroundColor(0xFF333333);

        View layoutRamButtons = findViewById(R.id.layout_ram_buttons);
        layoutRamButtons.setVisibility(View.VISIBLE);

        Button btnCancel = findViewById(R.id.btn_praetor_cancel);
        btnCancel.setVisibility(View.VISIBLE);
        btnCancel.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 80);
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btn_praetor_fix_ram).setVisibility(View.GONE);
        findViewById(R.id.btn_praetor_proceed).setVisibility(View.GONE);

        btnAction.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 80);
            showStage2();
        });

        tvCountdown = findViewById(R.id.tv_praetor_countdown);
        tvCountdown.setVisibility(View.VISIBLE);

        startTimer();
    }

    private void showStage2() {
        if (timer != null) timer.cancel();

        Random r = new Random();
        int a = r.nextInt(10) + 1;
        int b = r.nextInt(10) + 1;
        mathTarget = a + b;

        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText("To confirm you are fully awake and making a conscious decision to ruin your server performance, please solve the following problem:\n\n" +
                "What is " + a + " + " + b + "?");

        etMath = findViewById(R.id.et_math_answer);
        etMath.setVisibility(View.VISIBLE);

        btnAction.setText("ENABLE LEGACY");
        btnAction.setEnabled(false);
        btnAction.setBackgroundColor(0xFF333333);
        btnAction.setTextColor(0xFFAAAAAA);

        btnAction.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 120);
            try {
                int ans = Integer.parseInt(etMath.getText().toString());
                if (ans == mathTarget) {
                    Toast.makeText(this, "Legacy Mode Enabled", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Wrong answer. Try again.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Invalid input.", Toast.LENGTH_SHORT).show();
            }
        });

        tvCountdown.setText("");
        tvCountdown.setTextColor(0xFFFFA500);

        startTimer();
    }

    private void startTimer() {
        timer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvCountdown != null) {
                    tvCountdown.setText("Wait " + (millisUntilFinished / 1000) + " seconds...");
                }
            }
            @Override
            public void onFinish() {
                if (tvCountdown != null) {
                    tvCountdown.setText("You may now proceed.");
                    tvCountdown.setTextColor(0xFF00FF00);
                }
                if (btnAction != null) {
                    btnAction.setEnabled(true);
                    btnAction.setBackgroundColor(0xFFFF0000);
                    btnAction.setTextColor(0xFF000000);
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
