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
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
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
                try {
                    if (result.getCryptoObject() != null && result.getCryptoObject().getCipher() != null) {
                        // Perform dummy operation to verify the key was actually unlocked by hardware
                        result.getCryptoObject().getCipher().doFinal("koda_auth".getBytes());
                    } else {
                        throw new Exception("Cryptographic object was missing");
                    }
                    setResult(RESULT_OK);
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),
                            "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    if (killOnCancel) {
                        finishAffinity();
                        System.exit(0);
                    } else {
                        finish();
                    }
                }
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
            BiometricPrompt.CryptoObject cryptoObject = eu.kodanetwork.mchost.util.BiometricHelper.getCryptoObject();
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject);
            } else {
                Toast.makeText(this, "Biometric Keystore initialization failed", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                boolean killOnCancel = getIntent().getBooleanExtra("kill_on_cancel", false);
                if (killOnCancel) {
                    finishAffinity();
                    System.exit(0);
                } else {
                    finish();
                }
            }
        }
    }
}
