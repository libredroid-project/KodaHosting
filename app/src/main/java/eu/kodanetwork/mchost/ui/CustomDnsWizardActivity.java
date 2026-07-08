package eu.kodanetwork.mchost.ui;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.pm.PackageManager;
import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;

public class CustomDnsWizardActivity extends AppCompatActivity {

    private String serverId;
    private ServerInstance server;

    private View cardStep1, cardStep2, cardStep3;
    private View dotStep1, dotStep2, dotStep3;
    private EditText etDomain;
    private Button btnNextStep1, btnVerify, btnFinish;

    private TextView tvSrvType, tvSrvName, tvSrvTarget, tvSrvPort;
    private TextView tvStep3Title, tvStep3Desc;
    private ProgressBar pbVerify;

    private String userDomain = "";
    private Vibrator vibrator;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean wasDomainValid = false;
    private MaterialButton btnGotoProvider;

    private View layoutGuideContent;
    private AnimatedTutorialView animatedTutorialView;
    private MaterialButton btnPrevTutorial, btnNextTutorial;
    private TextView tvTutorialStep, tvGuideText;
    private int currentTutorialStep = 1;
    private String detectedProvider = "Default";

    private static class DnsRecordTask {
        String service;
        String protocol;
        int port;

        DnsRecordTask(String service, String protocol, int port) {
            this.service = service;
            this.protocol = protocol;
            this.port = port;
        }
    }

    private java.util.List<DnsRecordTask> requiredRecords = new java.util.ArrayList<>();
    private String targetHostname = "node.kodahosting.com";
    private String targetRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_dns_wizard);

        serverId = getIntent().getStringExtra("SERVER_ID");
        if (serverId == null || serverId.isEmpty()) {
            finish();
            return;
        }

        server = ServerRepo.get(this).byId(serverId);
        if (server == null) {
            finish();
            return;
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        targetHostname = server.getSubdomain() + ".kodanetwork.eu";

        targetRecord = getIntent().getStringExtra("TARGET_RECORD");
        if (targetRecord == null) targetRecord = "all";

        if ("all".equals(targetRecord) || "java".equals(targetRecord)) {
            requiredRecords.add(new DnsRecordTask("_minecraft", "TCP", server.getPort()));
        }
        if ("all".equals(targetRecord) || "bedrock".equals(targetRecord)) {
            if ("bedrock".equals(targetRecord) || server.isBedrockSupport()) {
                requiredRecords.add(new DnsRecordTask("_minecraft", "UDP", server.getBedrockPort()));
            }
        }
        if ("all".equals(targetRecord) || "voicechat".equals(targetRecord)) {
            if ("voicechat".equals(targetRecord) || server.isVoicechat()) {
                requiredRecords.add(new DnsRecordTask("_voicechat", "UDP", server.getVoicechatPort()));
            }
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        cardStep1 = findViewById(R.id.card_step1);
        cardStep2 = findViewById(R.id.card_step2);
        cardStep3 = findViewById(R.id.card_step3);

        TextView tvStep1Title = findViewById(R.id.tv_step1_title);
        if (tvStep1Title != null) {
            if ("bedrock".equals(targetRecord)) {
                tvStep1Title.setText(getString(R.string.cdns_step1_title).replace("EIGENE DOMAIN", "BEDROCK DOMAIN").replace("DOMAIN", "BEDROCK DOMAIN")); // fallback if string isn't exactly matching
            } else if ("voicechat".equals(targetRecord)) {
                tvStep1Title.setText(getString(R.string.cdns_step1_title).replace("EIGENE DOMAIN", "VOICECHAT DOMAIN").replace("DOMAIN", "VOICECHAT DOMAIN"));
            }
        }

        etDomain = findViewById(R.id.et_domain);
        btnNextStep1 = findViewById(R.id.btn_next_step1);
        btnVerify = findViewById(R.id.btn_verify);
        btnFinish = findViewById(R.id.btn_finish);
        btnGotoProvider = findViewById(R.id.btn_goto_provider);

        tvSrvType = findViewById(R.id.tv_srv_type);
        tvSrvName = findViewById(R.id.tv_srv_name);
        tvSrvTarget = findViewById(R.id.tv_srv_target);
        tvSrvPort = findViewById(R.id.tv_srv_port);

        tvStep3Title = findViewById(R.id.tv_step3_title);
        tvStep3Desc = findViewById(R.id.tv_step3_desc);
        pbVerify = findViewById(R.id.pb_verify);

        dotStep1 = findViewById(R.id.dot_step1);
        dotStep2 = findViewById(R.id.dot_step2);
        dotStep3 = findViewById(R.id.dot_step3);

        setupAnimatedButton(btnNextStep1);
        setupAnimatedButton(btnVerify);
        setupAnimatedButton(btnFinish);
        setupAnimatedButton(btnGotoProvider);

        AnimatedBorderLayout btnNextWrapper = findViewById(R.id.layout_btn_next_wrapper);
        if (btnNextWrapper != null) {
            btnNextWrapper.setDrawBorder(false);
        }

        layoutGuideContent = findViewById(R.id.layout_guide_content);
        animatedTutorialView = findViewById(R.id.animated_tutorial_view);
        btnPrevTutorial = findViewById(R.id.btn_prev_tutorial);
        btnNextTutorial = findViewById(R.id.btn_next_tutorial);
        tvTutorialStep = findViewById(R.id.tv_tutorial_step);
        tvGuideText = findViewById(R.id.tv_guide_text);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        com.google.android.material.chip.Chip chipExisting = findViewById(R.id.chip_existing_domain);
        if (server.getCustomDomain() != null && !server.getCustomDomain().trim().isEmpty() && !server.getCustomDomain().contains("koda.network")) {
            chipExisting.setVisibility(View.VISIBLE);
            chipExisting.setText(getString(R.string.cdns_use_existing, server.getCustomDomain()));
            chipExisting.setOnClickListener(v -> {
                etDomain.setText(server.getCustomDomain());
                btnNextStep1.performClick();
            });
        }
    }

    private void setupListeners() {
        etDomain.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String domain = s.toString().trim().toLowerCase();
                // Check if s matches basic domain format: e.g., domain.com or domain.co.uk
                boolean isValid = domain.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,15}$");
                if (isValid && !wasDomainValid) {
                    // Trigger a strong shimmer sweep on the next button wrapper!
                    AnimatedBorderLayout btnNextWrapper = findViewById(R.id.layout_btn_next_wrapper);
                    if (btnNextWrapper != null) {
                        btnNextWrapper.triggerShimmerSweep(null);
                    }
                }
                wasDomainValid = isValid;
            }
        });

        btnNextStep1.setOnClickListener(v -> {
            userDomain = etDomain.getText().toString().trim().toLowerCase();
            if (userDomain.isEmpty() || !userDomain.contains(".")) {
                Toast.makeText(this, "Please enter a valid domain", Toast.LENGTH_SHORT).show();
                vibrateError();
                return;
            }

            vibrateClick();

            // Hide the keyboard
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etDomain.getWindowToken(), 0);
            }

            AnimatedBorderLayout inputWrapper = findViewById(R.id.layout_input_wrapper);
            inputWrapper.triggerShimmerSweep(() -> {
                // Prepare Step 2
                DnsRecordTask mainTask = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(0);
                TextView tvSrvService = findViewById(R.id.tv_srv_service);
                TextView tvSrvProtocol = findViewById(R.id.tv_srv_protocol);
                TextView tvSrvPriority = findViewById(R.id.tv_srv_priority);
                TextView tvSrvWeight = findViewById(R.id.tv_srv_weight);
                
                tvSrvService.setText(String.format("Service: %s", mainTask.service));
                tvSrvProtocol.setText(String.format("Protocol: %s", mainTask.protocol));
                tvSrvName.setText("Name/Host: @");
                tvSrvTarget.setText(String.format("Target: %s", targetHostname));
                tvSrvPriority.setText("Priority: 0");
                tvSrvWeight.setText("Weight: 5");
                tvSrvPort.setText(String.format("Port: %d", mainTask.port));

                setupCopyListener(tvSrvType, "SRV");
                setupCopyListener(tvSrvService, mainTask.service);
                setupCopyListener(tvSrvProtocol, mainTask.protocol);
                setupCopyListener(tvSrvName, "@");
                setupCopyListener(tvSrvTarget, targetHostname);
                setupCopyListener(tvSrvPriority, "0");
                setupCopyListener(tvSrvWeight, "5");
                setupCopyListener(tvSrvPort, String.valueOf(mainTask.port));

                if (animatedTutorialView != null) {
                    animatedTutorialView.setRecordDetails(mainTask.service, mainTask.protocol, targetHostname, mainTask.port);
                }

                // Start async DNS provider detection
                detectDnsProvider(userDomain);

                // Hide the keyboard
                android.view.inputmethod.InputMethodManager imm2 = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm2 != null) {
                    imm2.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }

                transitionCards(cardStep1, cardStep2);
                updateDots(2);
            });
        });

        btnVerify.setOnClickListener(v -> {
            vibrateClick();
            transitionCards(cardStep2, cardStep3);
            updateDots(3);
            startVerification();
        });

        btnFinish.setOnClickListener(v -> {
            vibrateClick();
            finish();
        });


        btnPrevTutorial.setOnClickListener(v -> {
            vibrateClick();
            if (currentTutorialStep > 1) {
                currentTutorialStep--;
                updateTutorialUI();
            }
        });

        btnNextTutorial.setOnClickListener(v -> {
            vibrateClick();
            int maxSteps = 2 + requiredRecords.size();
            if (currentTutorialStep < maxSteps) {
                currentTutorialStep++;
                updateTutorialUI();
            }
        });
    }

    private void setupCopyListener(View view, String textToCopy) {
        view.setOnClickListener(v -> {
            vibrateClick();
            // Pulse animation
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1f);
            scaleX.setDuration(150);
            scaleY.setDuration(150);
            scaleX.start();
            scaleY.start();

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("DNS Record", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.cdns_copied), Toast.LENGTH_SHORT).show();
        });
    }

    private void transitionCards(View current, View next) {
        AnimatorSet currentAnim = new AnimatorSet();
        ObjectAnimator rotX = ObjectAnimator.ofFloat(current, "rotationX", 0f, -20f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(current, "scaleX", 1f, 0.85f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(current, "scaleY", 1f, 0.85f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(current, "alpha", 1f, 0f);
        currentAnim.playTogether(rotX, scaleX, scaleY, alpha);
        currentAnim.setDuration(350);
        currentAnim.setInterpolator(new AccelerateDecelerateInterpolator());

        next.setRotationX(15f);
        next.setScaleX(0.9f);
        next.setScaleY(0.9f);
        next.setAlpha(0f);
        next.setVisibility(View.VISIBLE);

        AnimatorSet nextAnim = new AnimatorSet();
        ObjectAnimator nRotX = ObjectAnimator.ofFloat(next, "rotationX", 15f, 0f);
        ObjectAnimator nScaleX = ObjectAnimator.ofFloat(next, "scaleX", 0.9f, 1f);
        ObjectAnimator nScaleY = ObjectAnimator.ofFloat(next, "scaleY", 0.9f, 1f);
        ObjectAnimator nAlpha = ObjectAnimator.ofFloat(next, "alpha", 0f, 1f);
        nextAnim.playTogether(nRotX, nScaleX, nScaleY, nAlpha);
        nextAnim.setDuration(400);
        nextAnim.setStartDelay(150);
        nextAnim.setInterpolator(new OvershootInterpolator(0.8f));

        currentAnim.start();
        nextAnim.start();

        mainHandler.postDelayed(() -> current.setVisibility(View.GONE), 350);
    }

    private void updateDots(int activeStep) {
        View[] dots = {dotStep1, dotStep2, dotStep3};
        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];
            boolean active = (i + 1) == activeStep;
            dot.setBackgroundResource(active ? R.drawable.dot_online : R.drawable.dot_offline);
            // Animate size
            int targetSizeDp = active ? 8 : 6;
            float px = targetSizeDp * getResources().getDisplayMetrics().density;
            ObjectAnimator sizeW = ObjectAnimator.ofInt(dot.getLayoutParams(), "width", (int) px);
            sizeW.setDuration(250);
            sizeW.addUpdateListener(a -> dot.requestLayout());
            ObjectAnimator sizeH = ObjectAnimator.ofInt(dot.getLayoutParams(), "height", (int) px);
            sizeH.setDuration(250);
            sizeH.addUpdateListener(a -> dot.requestLayout());
            sizeW.start();
            sizeH.start();
        }
    }

    private void startVerification() {
        pbVerify.setVisibility(View.VISIBLE);
        tvStep3Title.setText(getString(R.string.cdns_step3_title));
        tvStep3Desc.setText(getString(R.string.cdns_step3_desc));
        btnFinish.setVisibility(View.GONE);

        // Heartbeat vibration during check
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 100, 150, 100, 800, 100, 150, 100, 800}, -1));
        }

        new Thread(() -> {
            boolean success = true;
            try {
                // Wait for the animation and heartbeat to play a bit
                Thread.sleep(2000);

                if (requiredRecords.isEmpty()) {
                    requiredRecords.add(new DnsRecordTask("_minecraft", "TCP", server.getPort()));
                }

                for (DnsRecordTask task : requiredRecords) {
                    String queryUrl = "https://dns.google/resolve?name=" + task.service + "._" + task.protocol.toLowerCase() + "." + userDomain + "&type=SRV";
                    HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");

                    boolean taskSuccess = false;
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                        String res = s.hasNext() ? s.next() : "";
                        is.close();

                        JSONObject json = new JSONObject(res);
                        if (json.has("Answer")) {
                            JSONArray answers = json.getJSONArray("Answer");
                            for (int i = 0; i < answers.length(); i++) {
                                JSONObject answer = answers.getJSONObject(i);
                                String data = answer.getString("data");
                                // Data usually format: priority weight port target
                                if (data.contains(String.valueOf(task.port)) && (data.contains("85.215.180.87") || data.contains("kodanetwork.eu"))) {
                                    taskSuccess = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!taskSuccess) {
                        success = false;
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }

            final boolean isSuccess = success;
            mainHandler.post(() -> {
                pbVerify.setVisibility(View.GONE);
                btnFinish.setVisibility(View.VISIBLE);

                if (isSuccess) {
                    vibrateSuccess();
                    tvStep3Title.setText(getString(R.string.cdns_success));
                    tvStep3Title.setTextColor(0xFF00FF00); // Green
                    tvStep3Desc.setText("");
                    server.setCustomDomain(userDomain);
                    ServerRepo.get(CustomDnsWizardActivity.this).update(server);
                } else {
                    vibrateError();
                    tvStep3Title.setText("Error");
                    tvStep3Title.setTextColor(0xFFFF0000); // Red
                    tvStep3Desc.setText(getString(R.string.cdns_error));
                    
                    // Shake animation
                    ObjectAnimator shake = ObjectAnimator.ofFloat(cardStep3, "translationX", 0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
                    shake.setDuration(500);
                    shake.start();
                }
            });
        }).start();
    }

    private void detectDnsProvider(String domain) {
        new Thread(() -> {
            String providerName = null;
            String providerUrl = null;
            String[] providerPackages = null;
            int providerColor = Color.parseColor("#333333");
            int providerIcon = R.drawable.ic_provider_default;

            try {
                String queryUrl = "https://dns.google/resolve?name=" + domain + "&type=NS";
                HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(res);
                    if (json.has("Answer")) {
                        JSONArray answers = json.getJSONArray("Answer");
                        for (int i = 0; i < answers.length(); i++) {
                            JSONObject answer = answers.getJSONObject(i);
                            String nsData = answer.getString("data").toLowerCase();

                            if (nsData.contains("cloudflare")) {
                                providerName = "Cloudflare";
                                providerUrl = "https://dash.cloudflare.com";
                                providerPackages = new String[]{"com.cloudflare.onedotonedotonedotone"};
                                providerColor = Color.parseColor("#F38020");
                                providerIcon = R.drawable.ic_provider_cloudflare;
                                break;
                            } else if (nsData.contains("ui-dns") || nsData.contains("ionos")) {
                                providerName = "IONOS";
                                providerUrl = "https://login.ionos.de";
                                providerPackages = new String[]{"com.oneandone.ciso.mobile.app.android", "de.ionos.app"};
                                providerColor = Color.parseColor("#002E6E");
                                providerIcon = R.drawable.ic_provider_ionos;
                                break;
                            } else if (nsData.contains("domaincontrol") || nsData.contains("godaddy")) {
                                providerName = "GoDaddy";
                                providerUrl = "https://dcc.godaddy.com";
                                providerPackages = new String[]{"app.over.editor", "com.godaddy.mobile.android"};
                                providerColor = Color.parseColor("#00A699");
                                providerIcon = R.drawable.ic_provider_godaddy;
                                break;
                            } else if (nsData.contains("namecheap")) {
                                providerName = "Namecheap";
                                providerUrl = "https://www.namecheap.com/myaccount/login/";
                                providerColor = Color.parseColor("#DE3726");
                                break;
                            } else if (nsData.contains("hostinger")) {
                                providerName = "Hostinger";
                                providerUrl = "https://hpanel.hostinger.com";
                                providerColor = Color.parseColor("#673DE6");
                                break;
                            } else if (nsData.contains("strato")) {
                                providerName = "Strato";
                                providerUrl = "https://www.strato.de/apps/CustomerService";
                                providerColor = Color.parseColor("#FF6600");
                                break;
                            } else if (nsData.contains("hetzner")) {
                                providerName = "Hetzner";
                                providerUrl = "https://dns.hetzner.com";
                                providerColor = Color.parseColor("#D61C23");
                                break;
                            } else if (nsData.contains("inwx")) {
                                providerName = "INWX";
                                providerUrl = "https://www.inwx.de";
                                providerColor = Color.parseColor("#0C2E60");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final String name = providerName;
            final String url = providerUrl;
            final String[] packages = providerPackages;
            final int color = providerColor;
            final int icon = providerIcon;

            mainHandler.post(() -> {
                if (name != null) {
                    // Check if mobile app is installed using packages list
                    boolean isAppInstalled = false;
                    Intent launchIntent = null;
                    if (packages != null) {
                        for (String pkg : packages) {
                            try {
                                getPackageManager().getPackageInfo(pkg, 0);
                                launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                                if (launchIntent != null) {
                                    isAppInstalled = true;
                                    break;
                                }
                            } catch (PackageManager.NameNotFoundException ignored) {}
                        }
                    }

                    // Apply Brand Theme to Outlined Button to make it solid custom
                    btnGotoProvider.setStrokeWidth(0);
                    btnGotoProvider.setBackgroundTintList(ColorStateList.valueOf(color));
                    btnGotoProvider.setTextColor(Color.WHITE);
                    btnGotoProvider.setIconResource(icon);
                    btnGotoProvider.setIconTint(ColorStateList.valueOf(Color.WHITE));

                    detectedProvider = name;
                    animatedTutorialView.setProvider(name);
                    updateTutorialUI();

                    if (isAppInstalled) {
                        btnGotoProvider.setText(getString(R.string.cdns_open_app, name));
                        final Intent finalIntent = launchIntent;
                        btnGotoProvider.setOnClickListener(v -> {
                            vibrateClick();
                            try {
                                startActivity(finalIntent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Could not open app", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        btnGotoProvider.setText(getString(R.string.cdns_open_settings, name));
                        btnGotoProvider.setOnClickListener(v -> {
                            vibrateClick();
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    btnGotoProvider.setVisibility(View.VISIBLE);
                } else {
                    btnGotoProvider.setVisibility(View.GONE);
                    detectedProvider = "Default";
                    animatedTutorialView.setProvider("Default");
                    updateTutorialUI();
                }
            });
        }).start();
    }

    private void updateTutorialUI() {
        if (animatedTutorialView == null) return;
        
        int maxSteps = 2 + requiredRecords.size();
        
        if (currentTutorialStep > 2) {
            int taskIndex = currentTutorialStep - 3;
            if (taskIndex >= 0 && taskIndex < requiredRecords.size()) {
                DnsRecordTask task = requiredRecords.get(taskIndex);
                animatedTutorialView.setRecordDetails(task.service, task.protocol, targetHostname, task.port);
                animatedTutorialView.setStep(3); // Visual drawing logic handles any record as "step 3" visually
                
                TextView tvSrvService = findViewById(R.id.tv_srv_service);
                TextView tvSrvProtocol = findViewById(R.id.tv_srv_protocol);
                TextView tvSrvPriority = findViewById(R.id.tv_srv_priority);
                TextView tvSrvWeight = findViewById(R.id.tv_srv_weight);
                
                tvSrvService.setText(String.format("Service: %s", task.service));
                tvSrvProtocol.setText(String.format("Protocol: %s", task.protocol));
                tvSrvName.setText("Name/Host: @");
                tvSrvTarget.setText(String.format("Target: %s", targetHostname));
                tvSrvPriority.setText("Priority: 0");
                tvSrvWeight.setText("Weight: 5");
                tvSrvPort.setText(String.format("Port: %d", task.port));
                
                setupCopyListener(tvSrvService, task.service);
                setupCopyListener(tvSrvProtocol, task.protocol);
                setupCopyListener(tvSrvName, "@");
                setupCopyListener(tvSrvPriority, "0");
                setupCopyListener(tvSrvWeight, "5");
                setupCopyListener(tvSrvPort, String.valueOf(task.port));
            }
        } else {
            animatedTutorialView.setStep(currentTutorialStep);
        }
        
        tvTutorialStep.setText("Step " + currentTutorialStep + "/" + maxSteps);
        btnPrevTutorial.setEnabled(currentTutorialStep > 1);
        btnNextTutorial.setEnabled(currentTutorialStep < maxSteps);

        boolean isApp = false;
        // Check if the provider button text contains "Open" (meaning the app is installed)
        if (btnGotoProvider != null && btnGotoProvider.getVisibility() == View.VISIBLE) {
            String btnText = btnGotoProvider.getText().toString().toLowerCase();
            if (btnText.contains("open")) {
                isApp = true;
            }
        }
        animatedTutorialView.setAppInstalled(isApp);

        String text = "";
        switch (detectedProvider) {
            case "Cloudflare":
                if (isApp) {
                    if (currentTutorialStep == 1) {
                        text = "Open the Cloudflare app on your phone and log in to your account.";
                    } else if (currentTutorialStep == 2) {
                        text = "Tap on your domain from the list, then tap on the 'DNS' settings tab.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Tap '+ Add Record', choose Type 'SRV', enter: Service '" + t.service + "', Protocol '" + t.protocol + "', Target '" + targetHostname + "', Port, and tap Save.";
                    }
                } else {
                    if (currentTutorialStep == 1) {
                        text = "Log in to your Cloudflare Dashboard and select your domain. Navigate to the DNS settings tab on the left.";
                    } else if (currentTutorialStep == 2) {
                        text = "Click the '+ Add Record' button. Under the Record Type dropdown, select 'SRV'.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Enter the service parameters: Service as '" + t.service + "', Protocol as '" + t.protocol + "', Name/Host as '@', and input the Target and Port values shown above. Click Save.";
                    }
                }
                break;
            case "IONOS":
                if (isApp) {
                    if (currentTutorialStep == 1) {
                        text = "Open the IONOS app on your phone. From the home screen dashboard, tap on 'Domains'.";
                    } else if (currentTutorialStep == 2) {
                        text = "Select your domain name from the list and tap on 'DNS Settings'.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Tap '+ Add Record', choose Type 'SRV', and fill in: Service '" + t.service + "', Protocol '" + t.protocol + "', Target '" + targetHostname + "', Port. Tap Save.";
                    }
                } else {
                    if (currentTutorialStep == 1) {
                        text = "Log in to IONOS, go to Domains & SSL, choose your domain, and open its DNS settings.";
                    } else if (currentTutorialStep == 2) {
                        text = "Click 'Add Record' and select 'SRV' from the list of record types.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Enter the SRV data: Service '" + t.service + "', Protocol '" + t.protocol + "', Value as '@', Target '" + targetHostname + "', and Port. Save the record.";
                    }
                }
                break;
            case "GoDaddy":
                if (isApp) {
                    if (currentTutorialStep == 1) {
                        text = "Open the GoDaddy app on your phone and open your Domain Portfolio list.";
                    } else if (currentTutorialStep == 2) {
                        text = "Tap your domain, scroll down to the bottom, and tap on 'Manage DNS'.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Tap '+ Add', choose Type 'SRV', enter: Service '" + t.service + "', Protocol '" + t.protocol + "', Target '" + targetHostname + "', Port, and tap Save.";
                    }
                } else {
                    if (currentTutorialStep == 1) {
                        text = "Log in to GoDaddy, go to Domain Portfolio, select your domain, and choose Manage DNS.";
                    } else if (currentTutorialStep == 2) {
                        text = "Click 'Add New Record' and select Type 'SRV' from the dropdown.";
                    } else {
                        DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                        text = "Fill in the fields: Service '" + t.service + "', Protocol '" + t.protocol + "', Name '@', Target '" + targetHostname + "', and Port. Save your changes.";
                    }
                }
                break;
            default:
                if (currentTutorialStep == 1) {
                    text = "Log in to your domain registrar's control panel and open the DNS Management settings.";
                } else if (currentTutorialStep == 2) {
                    text = "Add a new record and select the 'SRV' record type from the menu.";
                } else {
                    DnsRecordTask t = requiredRecords.isEmpty() ? new DnsRecordTask("_minecraft", "TCP", server.getPort()) : requiredRecords.get(currentTutorialStep - 3);
                    text = "Enter: Service '" + t.service + "', Protocol '" + t.protocol + "', Name/Host '@' (or domain), Target '" + targetHostname + "', and Port. Click Save.";
                }
                break;
        }
        tvGuideText.setText(text);
    }

    private void vibrateClick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void vibrateSuccess() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void vibrateError() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
        }
    }

    private void setupAnimatedButton(Button button) {
        boolean animsEnabled = eu.kodanetwork.mchost.App.getPrefs(this).getBoolean("animations_enabled", true);
        if (!animsEnabled) {
            return;
        }

        // Set camera distance for perspective 3D distortion
        float scale = getResources().getDisplayMetrics().density * 4000;
        button.setCameraDistance(scale);

        // 3D Parallax/Tilt touch controller
        button.setOnTouchListener((v, event) -> {
            float width = v.getWidth();
            float height = v.getHeight();
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Relativize coordinate from -0.5 to 0.5
                    float relX = (x / width) - 0.5f;
                    float relY = (y / height) - 0.5f;

                    // Bound checks to ensure we only tilt if touching inside or close to the view bounds
                    if (x >= 0 && x <= width && y >= 0 && y <= height) {
                        float maxRotation = 18f; // Max tilt rotation in degrees
                        float rotY = relX * maxRotation;   // Y rotation based on horizontal touch offset
                        float rotX = -relY * maxRotation;  // X rotation based on vertical touch offset

                        v.animate()
                                .rotationX(rotX)
                                .rotationY(rotY)
                                .scaleX(0.95f)
                                .scaleY(0.95f)
                                .translationZ(12f * getResources().getDisplayMetrics().density) // Lift up in Z space
                                .setDuration(60)
                                .start();
                    } else {
                        // Reset if dragged outside
                        v.animate()
                                .rotationX(0f)
                                .rotationY(0f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .translationZ(0f)
                                .setDuration(200)
                                .setInterpolator(new OvershootInterpolator(1.2f))
                                .start();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Return with overshoot spring effect
                    v.animate()
                            .rotationX(0f)
                            .rotationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationZ(0f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(1.5f))
                            .start();
                    break;
            }
            return false;
        });

        // Loop subtle floating animation (floating up and down and tilting slightly on X-axis to show off 3D shape)
        ObjectAnimator translationY = ObjectAnimator.ofFloat(button, "translationY", 0f, -4f * getResources().getDisplayMetrics().density, 0f);
        ObjectAnimator rotXContinuous = ObjectAnimator.ofFloat(button, "rotationX", 0f, 2f, 0f);
        translationY.setDuration(3000);
        rotXContinuous.setDuration(3000);
        translationY.setRepeatCount(ValueAnimator.INFINITE);
        rotXContinuous.setRepeatCount(ValueAnimator.INFINITE);
        translationY.setRepeatMode(ValueAnimator.REVERSE);
        rotXContinuous.setRepeatMode(ValueAnimator.REVERSE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(translationY, rotXContinuous);
        set.start();
    }
}
