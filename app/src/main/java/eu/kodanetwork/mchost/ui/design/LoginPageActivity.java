package eu.kodanetwork.mchost.ui.design;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.AnimHelper;

public class LoginPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        setContentView(R.layout.activity_design_login);

        EditText etUser = findViewById(R.id.et_username);
        EditText etPass = findViewById(R.id.et_password);
        Button btnSignIn = findViewById(R.id.btn_sign_in);
        Button btnGoogle = findViewById(R.id.btn_google);
        TextView tvCreate = findViewById(R.id.tv_create_account);
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

        btnSignIn.setOnClickListener(v -> {
            String email = etUser.getText().toString().trim();
            String pwd = etPass.getText().toString().trim();
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Email und Passwort erforderlich", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cbLegal != null && !cbLegal.isChecked()) {
                Toast.makeText(this, "You must accept the Terms of Service and Privacy Policy to continue.", Toast.LENGTH_LONG).show();
                return;
            }
            btnSignIn.setEnabled(false);
            btnSignIn.setText("Lade...");

            eu.kodanetwork.mchost.network.supabase.SupabaseAuth.signInWithEmail(this, email, pwd, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        if (getIntent().getBooleanExtra("from_link_button", false)) {
                            android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(LoginPageActivity.this);
                            prefs.edit().putBoolean("auto_generate_code", true).apply();
                        } else {
                            startActivity(new Intent(LoginPageActivity.this, eu.kodanetwork.mchost.ui.MainActivity.class));
                        }
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText("Sign In");
                        Toast.makeText(LoginPageActivity.this, "Fehler: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        btnGoogle.setOnClickListener(v -> {
            if (!cbLegal.isChecked()) {
                Toast.makeText(this, "Du musst die Terms of Service und Privacy Policy akzeptieren.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInOptions gso = new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(getResources().getIdentifier("default_web_client_id", "string", getPackageName())))
                    .requestEmail()
                    .build();
                com.google.android.gms.auth.api.signin.GoogleSignInClient mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso);
                startActivityForResult(mGoogleSignInClient.getSignInIntent(), 9001);
            } catch (Exception e) {
                Toast.makeText(this, "Google Sign-In is not configured correctly in the app. Ensure default_web_client_id is set.", Toast.LENGTH_LONG).show();
            }
        });

        tvCreate.setOnClickListener(v -> {
            AnimHelper.startSlideVertical(this, new Intent(this, RegisterPageActivity.class));
            finish();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9001) {
            try {
                com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data);
                com.google.android.gms.auth.api.signin.GoogleSignInAccount account = task.getResult(com.google.android.gms.common.api.ApiException.class);
                String idToken = account.getIdToken();
                if (idToken != null) {
                    Toast.makeText(this, "Google Token received, verifying...", Toast.LENGTH_SHORT).show();
                    eu.kodanetwork.mchost.network.supabase.SupabaseAuth.signInWithGoogle(this, idToken, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                if (getIntent().getBooleanExtra("from_link_button", false)) {
                                    android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(LoginPageActivity.this);
                                    prefs.edit().putBoolean("auto_generate_code", true).apply();
                                } else {
                                    startActivity(new Intent(LoginPageActivity.this, eu.kodanetwork.mchost.ui.MainActivity.class));
                                }
                                finish();
                            });
                        }
                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(LoginPageActivity.this, "Fehler: " + message, Toast.LENGTH_LONG).show());
                        }
                    });
                }
            } catch (Exception e) {
                if (e instanceof com.google.android.gms.common.api.ApiException) {
                    int code = ((com.google.android.gms.common.api.ApiException)e).getStatusCode();
                    Toast.makeText(this, "Google Login aborted (Code: " + code + ")", Toast.LENGTH_LONG).show();
                    android.util.Log.e("GoogleLogin", "ApiException code " + code, e);
                } else {
                    Toast.makeText(this, "Google Login aborted: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    android.util.Log.e("GoogleLogin", "Exception during login", e);
                }
            }
        }
    }
}
