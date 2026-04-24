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
            if (etEmail.getText().toString().trim().isEmpty()
                || etUsername.getText().toString().trim().isEmpty()
                || etPassword.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Bitte Pflichtfelder ausfüllen", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Account erstellt (Supabase Auth Hook)", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, CompleteSetupPageActivity.class)
                .putExtra("inviteCode", etInvite.getText().toString().trim()));
        });

        tvSignIn.setOnClickListener(v -> startActivity(new Intent(this, LoginPageActivity.class)));
    }
}
