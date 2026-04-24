package eu.kodanetwork.mchost.ui.design;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;

public class LoginPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_design_login);

        EditText etUser = findViewById(R.id.et_username);
        EditText etPass = findViewById(R.id.et_password);
        Button btnSignIn = findViewById(R.id.btn_sign_in);
        Button btnDiscord = findViewById(R.id.btn_discord);
        TextView tvCreate = findViewById(R.id.tv_create_account);

        btnSignIn.setOnClickListener(v -> {
            if (etUser.getText().toString().trim().isEmpty() || etPass.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Username und Passwort erforderlich", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DashboardPageActivity.class));
            finish();
        });
        btnDiscord.setOnClickListener(v -> Toast.makeText(this, "Discord OAuth wird via Supabase API gestartet", Toast.LENGTH_SHORT).show());
        tvCreate.setOnClickListener(v -> startActivity(new Intent(this, RegisterPageActivity.class)));
    }
}
