package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.CountDownLatch;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.Material3ThemeHelper;
import eu.kodanetwork.mchost.util.RuntimeManager;

public class DownloadJreActivity extends AppCompatActivity {

    private static CountDownLatch downloadLatch;

    public static void setDownloadLatch(CountDownLatch latch) {
        downloadLatch = latch;
    }

    private int javaVersion;
    private TextView tvMsg;
    private TextView tvSubMsg;
    private android.view.View btnClose;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_update_server_m3 : R.layout.activity_update_server);

        tvMsg = findViewById(R.id.tv_update_msg);
        tvSubMsg = findViewById(R.id.tv_update_submsg);
        btnClose = findViewById(R.id.btn_close_update);
        handler = new Handler(Looper.getMainLooper());

        if (btnClose != null) {
            btnClose.setVisibility(View.GONE);
            btnClose.setOnClickListener(v -> finish());
        }

        javaVersion = getIntent().getIntExtra("VERSION", 25);
        
        startDownload();
    }

    private void setMsg(String msg, String sub) {
        handler.post(() -> {
            tvMsg.setText(msg);
            if (sub != null) tvSubMsg.setText(sub);
        });
    }

    private void startDownload() {
        setMsg(getString(R.string.java_runtime_label) + " " + javaVersion, "0%");
        new Thread(() -> {
            RuntimeManager.Result result = RuntimeManager.ensureRuntimeSync(this, javaVersion, (pct, msg) -> {
                setMsg(getString(R.string.java_runtime_downloading, javaVersion, pct), msg);
            });

            handler.post(() -> {
                if (result.success) {
                    if (downloadLatch != null) {
                        downloadLatch.countDown();
                        downloadLatch = null;
                    }
                    finish();
                } else {
                    setMsg(getString(R.string.praetor_jre_reason_download_failed, ""), result.failReason);
                    if (btnClose != null) {
                        btnClose.setVisibility(View.VISIBLE);
                    }
                    if (downloadLatch != null) {
                        downloadLatch.countDown();
                        downloadLatch = null;
                    }
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadLatch != null) {
            downloadLatch.countDown();
            downloadLatch = null;
        }
    }
}
