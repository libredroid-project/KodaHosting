package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.HapticUtil;

import android.view.View;
import android.widget.EditText;

public class PraetorWarningActivity extends Activity {

    private static java.util.concurrent.CountDownLatch jreFallbackLatch;
    
    public static void setJreFallbackLatch(java.util.concurrent.CountDownLatch latch) {
        jreFallbackLatch = latch;
    }

    public static final String EXTRA_REASON = "extra_reason";
    public static final String EXTRA_ACTION = "extra_action";

    private String targetFolderPath;
    private Button btnAction;
    private TextView tvCountdown;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_praetor_warning_m3 : R.layout.activity_praetor_warning);
        
        targetFolderPath = getIntent().getStringExtra("target_folder_path");
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        String praetorMode = getIntent().getStringExtra("praetor_mode");
        
        if (targetFolderPath != null) {
            showFolderBlockUI();
        } else if ("resource_pack_upload".equals(praetorMode)) {
            showResourcePackUploadUI();
        } else if ("JRE_FALLBACK".equals(getIntent().getStringExtra(EXTRA_ACTION))) {
            showJreFallbackUI(reason);
        } else if (reason != null) {
            showRamNetworkUI(reason, getIntent().getStringExtra(EXTRA_ACTION));
        } else {
            finish();
            return;
        }

        // Add Praetor Animation and Vibration
        HapticUtil.forceVibrate(this, 200);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            HapticUtil.forceVibrate(this, 300);
        }, 300);

        View warningIcon = findViewById(R.id.tv_warning_icon);
        if (warningIcon != null) {
            android.view.animation.AlphaAnimation blink = new android.view.animation.AlphaAnimation(1.0f, 0.0f);
            blink.setDuration(300);
            blink.setRepeatMode(android.view.animation.Animation.REVERSE);
            blink.setRepeatCount(5);
            warningIcon.startAnimation(blink);
        }
    }

    private void showRamNetworkUI(String reason, String actionStr) {
        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText(reason);

        btnAction = findViewById(R.id.btn_praetor_action);
        View layoutRamButtons = findViewById(R.id.layout_ram_buttons);

        if ("RAM_CONFLICT".equals(actionStr)) {
            btnAction.setVisibility(View.GONE);
            layoutRamButtons.setVisibility(View.VISIBLE);

            String serverId = getIntent().getStringExtra("extra_server_id");
            long maxRam = getIntent().getLongExtra("extra_max_ram", -1);

            findViewById(R.id.btn_praetor_fix_ram).setOnClickListener(v -> {
                HapticUtil.forceVibrate(this, 80);
                if (serverId != null && maxRam > 0) {
                    try {
                        eu.kodanetwork.mchost.model.ServerInstance s = eu.kodanetwork.mchost.model.ServerRepo.get(this).byId(serverId);
                        s.setRamMB((int) maxRam);
                        eu.kodanetwork.mchost.model.ServerRepo.get(this).update(s);
                        Toast.makeText(this, "RAM reduced to " + maxRam + "MB", Toast.LENGTH_SHORT).show();
                        
                        Intent intent = new Intent(PraetorWarningActivity.this, eu.kodanetwork.mchost.service.KodaServerService.class);
                        intent.setAction("START");
                        intent.putExtra("extra_id", serverId);
                        startService(intent);
                    } catch (Exception ignored) {}
                }
                finish();
            });

            findViewById(R.id.btn_praetor_cancel).setOnClickListener(v -> {
                HapticUtil.forceVibrate(this, 80);
                finish();
            });

            findViewById(R.id.btn_praetor_proceed).setOnClickListener(v -> {
                HapticUtil.forceVibrate(this, 120);
                layoutRamButtons.setVisibility(View.GONE);
                
                tvCountdown = findViewById(R.id.tv_praetor_countdown);
                tvCountdown.setVisibility(View.VISIBLE);

                timer = new CountDownTimer(10000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        tvCountdown.setText("Wait " + (millisUntilFinished / 1000) + " seconds...");
                    }
                    @Override
                    public void onFinish() {
                        tvCountdown.setVisibility(View.GONE);
                        
                        int a = new java.util.Random().nextInt(10) + 1;
                        int b = new java.util.Random().nextInt(10) + 1;
                        int answer = a + b;
                        
                        tvReason.setText("To override, prove you are aware of the risks. What is " + a + " + " + b + "?");
                        
                        EditText etMath = findViewById(R.id.et_math_answer);
                        etMath.setVisibility(View.VISIBLE);
                        
                        btnAction.setVisibility(View.VISIBLE);
                        btnAction.setText("CONFIRM START");
                        btnAction.setBackgroundColor(0xFFFF0000);
                        btnAction.setTextColor(0xFF000000);
                        btnAction.setOnClickListener(confirmView -> {
                            HapticUtil.forceVibrate(PraetorWarningActivity.this, 80);
                            if (etMath.getText().toString().equals(String.valueOf(answer))) {
                                if (serverId != null) {
                                    Intent intent = new Intent(PraetorWarningActivity.this, eu.kodanetwork.mchost.service.KodaServerService.class);
                                    intent.setAction("START");
                                    intent.putExtra("extra_id", serverId);
                                    startService(intent);
                                    Toast.makeText(PraetorWarningActivity.this, "Override Accepted. Starting Server...", Toast.LENGTH_LONG).show();
                                }
                                finish();
                            } else {
                                Toast.makeText(PraetorWarningActivity.this, "Incorrect.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }.start();
            });

        } else {
            btnAction.setText(actionStr != null ? actionStr : "UNDERSTOOD");
            btnAction.setOnClickListener(v -> {
                HapticUtil.forceVibrate(this, 80);
                finish();
            });
        }
    }

    private void showFolderBlockUI() {
        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        
        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText(getString(R.string.praetor_reason_manual_override));
            
        btnAction = findViewById(R.id.btn_praetor_action);
        btnAction.setText(getString(R.string.praetor_action_proceed));
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
            HapticUtil.forceVibrate(this, 120);
            try {
                new File(targetFolderPath, ".manual_override").createNewFile();
                Toast.makeText(this, "Manual Override Granted.", Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        tvCountdown = findViewById(R.id.tv_praetor_countdown);
        tvCountdown.setVisibility(View.VISIBLE);

        timer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText("Wait " + (millisUntilFinished / 1000) + " seconds...");
            }
            @Override
            public void onFinish() {
                tvCountdown.setText("You may now proceed.");
                tvCountdown.setTextColor(0xFF00FF00);
                btnAction.setEnabled(true);
                btnAction.setBackgroundColor(0xFFFF0000);
                btnAction.setTextColor(0xFF000000);
            }
        }.start();
    }

    private void showResourcePackUploadUI() {
        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        
        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText("You are about to upload a custom Resource Pack to a public hosting API.\n\nBy proceeding, you confirm that the file does NOT contain illegal, malicious, or copyrighted material. YOU are fully responsible for the uploaded content.");
            
        btnAction = findViewById(R.id.btn_praetor_action);
        btnAction.setText("I CONFIRM");
        btnAction.setEnabled(false);
        btnAction.setBackgroundColor(0xFF333333);
        
        View layoutRamButtons = findViewById(R.id.layout_ram_buttons);
        layoutRamButtons.setVisibility(View.GONE);
        
        Button btnCancel = findViewById(R.id.btn_praetor_cancel);
        btnCancel.setVisibility(View.VISIBLE);
        btnCancel.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 80);
            setResult(RESULT_CANCELED);
            finish();
        });
        
        btnAction.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 120);
            setResult(RESULT_OK);
            finish();
        });
        
        tvCountdown = findViewById(R.id.tv_praetor_countdown);
        tvCountdown.setVisibility(View.VISIBLE);

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText("Please read the warning. Wait " + (millisUntilFinished / 1000) + " seconds...");
            }
            @Override
            public void onFinish() {
                tvCountdown.setText("You may now proceed.");
                tvCountdown.setTextColor(0xFF00FF00);
                btnAction.setEnabled(true);
                btnAction.setBackgroundColor(0xFFFF0000);
                btnAction.setTextColor(0xFF000000);
            }
        }.start();
    }
    
    private void showJreFallbackUI(String reason) {
        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        
        TextView tvReason = findViewById(R.id.tv_praetor_reason);
        tvReason.setText(reason);
            
        btnAction = findViewById(R.id.btn_praetor_action);
        btnAction.setText(getString(R.string.praetor_action_understood));
        
        View layoutRamButtons = findViewById(R.id.layout_ram_buttons);
        layoutRamButtons.setVisibility(View.GONE);
        findViewById(R.id.btn_praetor_cancel).setVisibility(View.GONE);
        
        btnAction.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 80);
            if (jreFallbackLatch != null) {
                jreFallbackLatch.countDown();
                jreFallbackLatch = null;
            }
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
