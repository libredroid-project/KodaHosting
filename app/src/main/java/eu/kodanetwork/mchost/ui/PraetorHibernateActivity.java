package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import eu.kodanetwork.mchost.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.Html;

public class PraetorHibernateActivity extends Activity {

    private int countdown = 10;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_praetor_hibernate_m3 : R.layout.activity_praetor_hibernate);

        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color='#555555'>P.R.</font><font color='#AAAAAA'>A.E.T.</font><font color='#FFFFFF'>O.R.</font>";
        if (tvTitle != null) tvTitle.setText(Html.fromHtml(praetorHtml, Html.FROM_HTML_MODE_LEGACY));

        TextView tvCountdown = findViewById(R.id.tv_praetor_countdown);
        MaterialButton btnConfirm = findViewById(R.id.btn_praetor_confirm);
        MaterialButton btnCancel = findViewById(R.id.btn_praetor_cancel);
        MaterialButton btnExplanation = findViewById(R.id.btn_praetor_explanation);

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        btnExplanation.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Hibernation Detail-Erklärung")
                .setMessage("Wenn ein Server in den 'Hibernate' (Winterschlaf) Zustand versetzt wird:\n\n1. Deine DNS-Einträge (Join-Adresse) werden gelöscht. Andere Nutzer können diese Adresse übernehmen.\n2. Deine Server-Dateien werden platzsparend komprimiert.\n3. Der Server-Ordner wird gelöscht, um Speicherplatz freizugeben.\n\nBeim Aufwecken wird die Komprimierung rückgängig gemacht. Falls deine Join-Adresse belegt ist, musst du eine neue wählen.")
                .setPositiveButton("Verstanden", null)
                .show();
        });

        btnConfirm.setOnClickListener(v -> {
            if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(PraetorHibernateActivity.this)) return;
            setResult(RESULT_OK);
            finish();
        });

        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                countdown--;
                if (countdown > 0) {
                    tvCountdown.setText(String.valueOf(countdown));
                    handler.postDelayed(this, 1000);
                } else {
                    tvCountdown.setText("");
                    btnConfirm.setEnabled(true);
                }
            }
        };
        handler.postDelayed(timerRunnable, 1000);
    }
}
