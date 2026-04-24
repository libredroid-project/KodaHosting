package eu.kodanetwork.mchost.ui.design;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;

public class AdminPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_design_admin);

        Button btnCreateSub = findViewById(R.id.btn_create_subscription);
        Button btnGenerateInvite = findViewById(R.id.btn_generate_invite);
        Button btnGeneratePromo = findViewById(R.id.btn_generate_promo);
        Button btnGenerateRedeem = findViewById(R.id.btn_generate_redeem);

        btnCreateSub.setOnClickListener(v -> toast("Subscription action via Supabase Admin Function"));
        btnGenerateInvite.setOnClickListener(v -> toast("Invite generated"));
        btnGeneratePromo.setOnClickListener(v -> toast("Promo generated"));
        btnGenerateRedeem.setOnClickListener(v -> toast("Redeem code generated"));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
