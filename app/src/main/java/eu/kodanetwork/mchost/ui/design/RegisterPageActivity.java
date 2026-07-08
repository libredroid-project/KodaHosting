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
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        setContentView(R.layout.activity_design_register);

        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        EditText etInvite = findViewById(R.id.et_invite);
        Button btnCreate = findViewById(R.id.btn_create_account);
        TextView tvSignIn = findViewById(R.id.tv_sign_in);
        TextView tvTitle = findViewById(R.id.tv_praetor_title);

        if (tvTitle != null) {
            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
            tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }

        android.widget.CheckBox cbLegal = findViewById(R.id.cb_legal);
        if (cbLegal != null) {
            cbLegal.setText(android.text.Html.fromHtml("I accept the <a href='https://host.kodanetwork.eu/tos.html'>Terms of Service</a> and <a href='https://host.kodanetwork.eu/privacy.html'>Privacy Policy</a>", android.text.Html.FROM_HTML_MODE_LEGACY));
            cbLegal.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }

        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);

        btnCreate.setOnClickListener(v -> {
            if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
            String email = etEmail.getText().toString().trim();
            String pwd = etPassword.getText().toString().trim();
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Bitte Pflichtfelder ausfüllen", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cbLegal != null && !cbLegal.isChecked()) {
                Toast.makeText(this, "You must accept the Terms of Service and Privacy Policy to continue.", Toast.LENGTH_LONG).show();
                return;
            }
            
            btnCreate.setEnabled(false);
            btnCreate.setText(getString(R.string.register_loading));

            // Generate 6 digit code
            String generatedCode = String.format("%06d", new java.util.Random().nextInt(999999));

            eu.kodanetwork.mchost.network.ResendHelper.sendOTP(email, generatedCode, new eu.kodanetwork.mchost.network.ResendHelper.Callback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        btnCreate.setEnabled(true);
                        btnCreate.setText(getString(R.string.register_create_account));

                        android.app.Dialog dialog = new android.app.Dialog(RegisterPageActivity.this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
                        dialog.setContentView(R.layout.dialog_praetor_verify);
                        dialog.setCancelable(false);

                        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
                        if (tvTitle != null) {
                            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
                            tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
                        }

                        TextView tvSubtitle = dialog.findViewById(R.id.tv_dialog_subtitle);
                        if (tvSubtitle != null) tvSubtitle.setText(getString(R.string.email_verification) + " - " + email);

                        EditText input = dialog.findViewById(R.id.et_verification_code);

                        dialog.findViewById(R.id.btn_dialog_verify).setOnClickListener(v -> {
                            String code = input.getText().toString().trim();
                            if (code.equals(generatedCode)) {
                                Toast.makeText(RegisterPageActivity.this, getString(R.string.register_code_accepted), Toast.LENGTH_SHORT).show();
                                btnCreate.setEnabled(false);
                                btnCreate.setText(getString(R.string.register_loading));
                                dialog.dismiss();

                                eu.kodanetwork.mchost.network.supabase.SupabaseAuth.signUp(RegisterPageActivity.this, email, pwd, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
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
                                            btnCreate.setText(getString(R.string.register_create_account));
                                            Toast.makeText(RegisterPageActivity.this, "Fehler: " + message, Toast.LENGTH_LONG).show();
                                        });
                                    }
                                });
                            } else {
                                Toast.makeText(RegisterPageActivity.this, "Falscher Code!", Toast.LENGTH_SHORT).show();
                            }
                        });
                        
                        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> {
                            dialog.cancel();
                            btnCreate.setEnabled(true);
                            btnCreate.setText(getString(R.string.register_create_account));
                        });
                        
                        dialog.show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnCreate.setEnabled(true);
                        btnCreate.setText(getString(R.string.register_create_account));
                        Toast.makeText(RegisterPageActivity.this, "Fehler beim Senden des Codes: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        tvSignIn.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.AnimHelper.startSlideVertical(this, new Intent(this, LoginPageActivity.class));
            finish();
        });
    }
}
