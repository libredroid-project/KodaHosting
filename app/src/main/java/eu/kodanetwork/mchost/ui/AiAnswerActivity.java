package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.util.AiHelper;
import eu.kodanetwork.mchost.util.HapticUtil;

/**
 * Shows the AI crash analysis (cause + fix + confidence) in the app's
 * PRAETOR dialog style. Offers TRY TO FIX when the model returned a
 * CERTAIN whitelisted auto-fix (set_ram / set_java).
 */
public class AiAnswerActivity extends AppCompatActivity {

    private ServerInstance server;
    private AiHelper.AiResult result;
    private LinearLayout content;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String serverId = getIntent().getStringExtra("serverId");
        server = serverId != null ? ServerRepo.get(this).byId(serverId) : null;
        String logTail = getIntent().getStringExtra("logTail");

        // PRAETOR full-screen look (same tones as CrashAlertActivity)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0808);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.ai_title));
        tvTitle.setTextColor(0xFFFF3333);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_bold));
        root.addView(tvTitle);

        View divider = new View(this);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(40), dp(2));
        dLp.topMargin = dp(8);
        dLp.bottomMargin = dp(14);
        divider.setBackgroundColor(0xFFFF6B00);
        root.addView(divider, dLp);

        tvStatus = new TextView(this);
        tvStatus.setText(getString(R.string.ai_thinking));
        tvStatus.setTextColor(0xFF8A8A9A);
        tvStatus.setTextSize(14);
        tvStatus.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_regular));
        root.addView(tvStatus);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.topMargin = dp(6);
        root.addView(scroll, sLp);

        setContentView(root);

        final String fLog = logTail;
        new Thread(() -> {
            try {
                AiHelper.AiResult res = AiHelper.askAiSync(this, server, fLog);
                runOnUiThread(() -> showResult(res));
            } catch (AiHelper.ConsentException e) {
                runOnUiThread(() -> { finish(); });
            } catch (AiHelper.RateLimitException e) {
                runOnUiThread(() -> showError(getString(R.string.ai_rate_limited, e.used)));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : "unknown"));
            }
        }).start();
    }

    private void showResult(AiHelper.AiResult res) {
        result = res;
        tvStatus.setVisibility(View.GONE);
        content.removeAllViews();

        // Confidence label
        TextView tvConf = new TextView(this);
        int col;
        String label;
        switch (res.confidence) {
            case "CERTAIN":         col = 0xFF4FC3F7; label = getString(R.string.confidence_certain); break;
            case "HIGH_CONFIDENCE": col = 0xFF00E676; label = getString(R.string.confidence_high); break;
            case "CONFIDENT":       col = 0xFFFFCC00; label = getString(R.string.confidence_confident); break;
            default:                col = 0xFFFF4444; label = getString(R.string.confidence_not_confident); break;
        }
        tvConf.setText(getString(R.string.ai_confidence_prefix) + " " + label);
        tvConf.setTextColor(col);
        tvConf.setTextSize(12);
        tvConf.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_bold));
        tvConf.setBackgroundResource(R.drawable.bg_dialog_custom);
        tvConf.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.bottomMargin = dp(12);
        content.addView(tvConf, cLp);

        addSection(getString(R.string.ai_cause), res.cause, 0xFFF0F0F0);
        if (res.fix != null && !res.fix.isEmpty()) {
            addSection(getString(R.string.ai_fix), res.fix, 0xFFE8E2D6);
        }

        // TRY TO FIX: only for CERTAIN + whitelisted action
        if (res.autoFixAction != null && server != null) {
            MaterialButton btnFix = new MaterialButton(this, null, 0);
            btnFix.setText(getString(R.string.ai_try_fix));
            btnFix.setTextColor(0xFF111111);
            btnFix.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B00));
            btnFix.setCornerRadius(dp(12));
            btnFix.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda));
            btnFix.setAllCaps(true);
            btnFix.setTextSize(13);
            btnFix.setOnClickListener(v -> confirmAutoFix());
            LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            fLp.topMargin = dp(16);
            content.addView(btnFix, fLp);
        }

        MaterialButton btnClose = new MaterialButton(this, null, 0);
        btnClose.setText(getString(R.string.sd_action_cancel));
        btnClose.setTextColor(0xFFF0F0F0);
        btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2A33));
        btnClose.setCornerRadius(dp(12));
        btnClose.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda));
        btnClose.setAllCaps(true);
        btnClose.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        bLp.topMargin = dp(10);
        content.addView(btnClose, bLp);
    }

    private void addSection(String title, String body, int bodyColor) {
        if (body == null || body.isEmpty()) return;
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(0xFFFF6B00);
        tvT.setTextSize(12);
        tvT.setAllCaps(true);
        tvT.setLetterSpacing(0.1f);
        tvT.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.bottomMargin = dp(4);
        content.addView(tvT, tLp);

        TextView tvB = new TextView(this);
        tvB.setText(body);
        tvB.setTextColor(bodyColor);
        tvB.setTextSize(14);
        tvB.setLineSpacing(dp(2), 1f);
        tvB.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.space_grotesk_regular));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.bottomMargin = dp(14);
        content.addView(tvB, bLp);
    }

    private void showError(String msg) {
        tvStatus.setText(getString(R.string.ai_error_prefix) + " " + msg);
        content.removeAllViews();
        MaterialButton btnRetry = new MaterialButton(this, null, 0);
        btnRetry.setText(getString(R.string.ai_retry));
        btnRetry.setTextColor(0xFFF0F0F0);
        btnRetry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2A33));
        btnRetry.setCornerRadius(dp(12));
        btnRetry.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda));
        btnRetry.setAllCaps(true);
        btnRetry.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 40);
            recreate();
        });
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        bLp.topMargin = dp(14);
        content.addView(btnRetry, bLp);
    }

    private void confirmAutoFix() {
        HapticUtil.forceVibrate(this, 50);
        String msg;
        if ("set_ram".equals(result.autoFixAction)) {
            msg = getString(R.string.ai_fix_confirm_ram, server.getRamMB(), (int) result.autoFixValue);
        } else {
            msg = getString(R.string.ai_fix_confirm_java, (int) result.autoFixValue);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.ai_try_fix))
                .setMessage(msg)
                .setPositiveButton(getString(R.string.ai_apply), (d, w) -> applyAutoFix())
                .setNegativeButton(getString(R.string.sd_action_cancel), null)
                .show();
    }

    private void applyAutoFix() {
        try {
            if ("set_ram".equals(result.autoFixAction)) {
                server.setRamMB((int) result.autoFixValue);
            } else if ("set_java".equals(result.autoFixAction)) {
                server.setJavaRuntime((int) result.autoFixValue);
            }
            ServerRepo.get(this).update(server);
            Toast.makeText(this, getString(R.string.ai_fix_applied), Toast.LENGTH_LONG).show();
            HapticUtil.forceVibrate(this, 60);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.ai_fix_failed), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
