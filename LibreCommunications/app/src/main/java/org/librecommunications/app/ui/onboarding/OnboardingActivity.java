package org.librecommunications.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.librecommunications.app.databinding.ActivityOnboardingBinding;
import org.librecommunications.app.ui.BaseSecureActivity;
import org.librecommunications.app.ui.MainActivity;

/**
 * Onboarding screen displaying initial setup instructions or welcome information.
 */
public class OnboardingActivity extends BaseSecureActivity {

    private ActivityOnboardingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize View Binding
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupInteractions();
    }

    private void setupInteractions() {
        // Set click listener for the main start button in the onboarding screen
        binding.btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToMain();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        // Clear task stack to prevent going back to onboarding
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear binding to prevent memory leaks
        binding = null;
    }
}
