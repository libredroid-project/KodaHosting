package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class BiometricAuthActivity extends FragmentActivity {

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make activity invisible
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        boolean killOnCancel = getIntent().getBooleanExtra("kill_on_cancel", false);

        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(BiometricAuthActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                if (killOnCancel) {
                    finishAffinity();
                    System.exit(0);
                } else {
                    finish();
                }
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Verify your identity")
                .setNegativeButtonText("Cancel")
                .build();
    }

    private boolean hasStartedAuth = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasStartedAuth && biometricPrompt != null && promptInfo != null) {
            hasStartedAuth = true;
            biometricPrompt.authenticate(promptInfo);
        }
    }
}
