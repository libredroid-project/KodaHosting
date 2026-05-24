package eu.kodanetwork.mchost.ui.design;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;

public class RegisterPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_design_register);

        EditText etEmail = findViewById(R.id.et_email);
        EditText etUsername = findViewById(R.id.et_username);
        EditText etPassword = findViewById(R.id.et_password);
        EditText etInvite = findViewById(R.id.et_invite);
        Button btnCreate = findViewById(R.id.btn_create_account);
        TextView tvSignIn = findViewById(R.id.tv_sign_in);

        btnCreate.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd = etPassword.getText().toString().trim();
            if (email.isEmpty() || etUsername.getText().toString().trim().isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Bitte Pflichtfelder ausfüllen", Toast.LENGTH_SHORT).show();
                return;
            }
            
            btnCreate.setEnabled(false);
            btnCreate.setText("Lade...");
            
            eu.kodanetwork.mchost.network.supabase.SupabaseAuth.signUp(this, email, pwd, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterPageActivity.this, "Account erfolgreich erstellt!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterPageActivity.this, eu.kodanetwork.mchost.ui.MainActivity.class));
                        finishAffinity();
                    });
                }
                
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnCreate.setEnabled(true);
                        btnCreate.setText("Account Erstellen");
                        Toast.makeText(RegisterPageActivity.this, "Fehler: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        tvSignIn.setOnClickListener(v -> startActivity(new Intent(this, LoginPageActivity.class)));
    }
}
