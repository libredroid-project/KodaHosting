package eu.kodanetwork.mchost.ui.design;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.ui.MainActivity;

public class HomePageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_design_home);

        Button btnLogin = findViewById(R.id.btn_login);
        Button btnRegister = findViewById(R.id.btn_register);
        Button btnCheckout = findViewById(R.id.btn_checkout);
        Button btnDashboard = findViewById(R.id.btn_dashboard);
        Button btnServerManager = findViewById(R.id.btn_server_manager);

        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginPageActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterPageActivity.class)));
        btnCheckout.setOnClickListener(v -> startActivity(new Intent(this, CheckoutPageActivity.class)));
        btnDashboard.setOnClickListener(v -> startActivity(new Intent(this, DashboardPageActivity.class)));
        btnServerManager.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    }
}
