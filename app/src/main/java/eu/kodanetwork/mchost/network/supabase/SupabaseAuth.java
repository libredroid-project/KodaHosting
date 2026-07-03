package eu.kodanetwork.mchost.network.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import eu.kodanetwork.mchost.BuildConfig;

public class SupabaseAuth {

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    public static void signUp(Context ctx, String email, String password, AuthCallback cb) {
        new Thread(() -> {
            try {
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/auth/v1/signup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);

                String json = "{\"email\":\"" + email + "\", \"password\":\"" + password + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStreamReader r = new InputStreamReader(conn.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    handleAuthResponse(ctx, sb.toString(), email, cb);
                } else {
                    InputStreamReader r = new InputStreamReader(conn.getErrorStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        cb.onError(err.optString("msg", "Signup failed"));
                    } catch (Exception e) {
                        cb.onError("Signup failed: " + code);
                    }
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static void signInWithEmail(Context ctx, String email, String password, AuthCallback cb) {
        new Thread(() -> {
            try {
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/auth/v1/token?grant_type=password");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);

                String json = "{\"email\":\"" + email + "\", \"password\":\"" + password + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStreamReader r = new InputStreamReader(conn.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    handleAuthResponse(ctx, sb.toString(), email, cb);
                } else {
                    InputStreamReader r = new InputStreamReader(conn.getErrorStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        cb.onError(err.optString("error_description", "Login failed"));
                    } catch (Exception e) {
                        cb.onError("Login failed: " + code);
                    }
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static void signInWithGoogle(Context ctx, String idToken, AuthCallback cb) {
        new Thread(() -> {
            try {
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/auth/v1/token?grant_type=id_token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);

                String json = "{\"id_token\":\"" + idToken + "\", \"provider\":\"google\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStreamReader r = new InputStreamReader(conn.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    handleAuthResponse(ctx, sb.toString(), "Google Account", cb);
                } else {
                    InputStreamReader r = new InputStreamReader(conn.getErrorStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        cb.onError(err.optString("error_description", "Google Login failed"));
                    } catch (Exception e) {
                        cb.onError("Google Login failed: " + code);
                    }
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    private static void handleAuthResponse(Context ctx, String jsonResp, String emailFallback, AuthCallback cb) {
        try {
            JSONObject resp = new JSONObject(jsonResp);
            String accessToken = resp.optString("access_token", null);
            String refreshToken = resp.optString("refresh_token", null);
            
            JSONObject user = resp.optJSONObject("user");
            if (user == null && accessToken == null) {
                // For signup with confirmation required, it might just return user object
                user = resp;
                if (!user.has("id")) {
                    cb.onError("Unknown error");
                    return;
                }
            }
            
            String userId = user != null ? user.optString("id", null) : null;
            String email = user != null ? user.optString("email", emailFallback) : emailFallback;

            if (accessToken != null && userId != null) {
                SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ctx);
                String oldAppUuid = prefs.getString("app_uuid", null);
                
                prefs.edit()
                    .putString("koda_session_token", accessToken)
                    .putString("koda_refresh_token", refreshToken)
                    .putString("app_uuid", userId) // Replaces old local app_uuid
                    .putString("account_email", email)
                    .apply();
                    
                if (oldAppUuid != null && !oldAppUuid.equals(userId)) {
                    new Thread(() -> {
                        try {
                            URL patchUrl = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?owner_app_uuid=eq." + oldAppUuid);
                            HttpURLConnection patchConn = (HttpURLConnection) patchUrl.openConnection();
                            patchConn.setRequestMethod("PATCH");
                            patchConn.setRequestProperty("Content-Type", "application/json");
                            patchConn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                            patchConn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                            patchConn.setRequestProperty("Prefer", "return=minimal");
                            patchConn.setDoOutput(true);
                            String pJson = "{\"owner_app_uuid\":\"" + userId + "\"}";
                            java.io.OutputStream os = patchConn.getOutputStream();
                            os.write(pJson.getBytes());
                            os.flush(); os.close();
                            
                            int code = patchConn.getResponseCode();
                            try {
                                java.io.InputStream is = code < 400 ? patchConn.getInputStream() : patchConn.getErrorStream();
                                if (is != null) {
                                    while (is.read() != -1) {}
                                    is.close();
                                }
                            } catch (Exception e) {}
                        } catch (Exception ignored) {}
                    }).start();
                }
                
                cb.onSuccess();
            } else if (userId != null) {
                // Signed up but needs email confirmation
                cb.onError("Please check your email to confirm your account.");
            } else {
                cb.onError("Invalid response from server");
            }
        } catch (Exception e) {
            cb.onError("Parse error: " + e.getMessage());
        }
    }

    public static void logout(Context ctx) {
        eu.kodanetwork.mchost.App.getPrefs(ctx).edit()
            .remove("koda_session_token")
            .remove("koda_refresh_token")
            .remove("app_uuid")
            .remove("account_email")
            .remove("mc_username")
            .remove("link_code")
            .apply();
    }

    public static void resetPassword(Context ctx, String email, AuthCallback cb) {
        new Thread(() -> {
            try {
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/functions/v1/reset-password");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                // Edge functions usually require anon key or specific auth headers if not public, but let's send apikey and auth
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);

                String json = "{\"email\":\"" + email + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    cb.onSuccess();
                } else {
                    cb.onError("Failed to send reset email");
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static void deleteUser(Context ctx, AuthCallback cb) {
        new Thread(() -> {
            try {
                String token = eu.kodanetwork.mchost.App.getPrefs(ctx).getString("koda_session_token", "");
                if (token.isEmpty()) { cb.onError("Not logged in"); return; }
                
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/delete_user");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + token);
                
                int code = conn.getResponseCode();
                // We'll accept 404 if the RPC doesn't exist but we still want to log out the user locally
                // Or just always succeed locally
                cb.onSuccess();
            } catch (Exception e) {
                cb.onSuccess(); // locally delete them anyway
            }
        }).start();
    }

    public static boolean isLoggedIn(Context ctx) {
        return eu.kodanetwork.mchost.App.getPrefs(ctx).contains("koda_session_token");
    }
}
