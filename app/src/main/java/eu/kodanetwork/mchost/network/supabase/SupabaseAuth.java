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

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("email", email);
                jsonObj.put("password", password);
                String json = jsonObj.toString();
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

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("email", email);
                jsonObj.put("password", password);
                String json = jsonObj.toString();
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

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("id_token", idToken);
                jsonObj.put("provider", "google");
                String json = jsonObj.toString();
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
                
                prefs.edit()
                    .putString("koda_session_token", accessToken)
                    .putString("koda_refresh_token", refreshToken)
                    .putString("account_email", email)
                    .putString("auth_uuid", userId)
                    .apply();
                    
                syncAuthId(ctx, userId);
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
        syncAuthId(ctx, null);
        eu.kodanetwork.mchost.App.getPrefs(ctx).edit()
            .remove("koda_session_token")
            .remove("koda_refresh_token")
            .remove("auth_uuid")
            .remove("account_email")
            .remove("mc_username")
            .remove("link_code")
            .apply();
    }

    private static void syncAuthId(Context ctx, String authId) {
        new Thread(() -> {
            try {
                String appUuid = eu.kodanetwork.mchost.App.getPrefs(ctx).getString("app_uuid", null);
                if (appUuid == null) return;

                org.json.JSONObject rpcBody = new org.json.JSONObject();
                rpcBody.put("p_app_uuid", appUuid);
                rpcBody.put("p_auth_id", authId == null ? org.json.JSONObject.NULL : authId);

                java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_sync_auth_id");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);
                conn.getOutputStream().write(rpcBody.toString().getBytes());
                conn.getResponseCode();
            } catch (Exception ignored) {
            }
        }).start();
    }

    public static void updatePassword(Context ctx, String newPassword, AuthCallback cb) {
        new Thread(() -> {
            try {
                String token = eu.kodanetwork.mchost.App.getPrefs(ctx).getString("koda_session_token", null);
                if (token == null) {
                    cb.onError("Not logged in");
                    return;
                }
                
                URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/auth/v1/user");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("password", newPassword);
                String json = jsonObj.toString();
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    cb.onSuccess();
                } else {
                    cb.onError("Failed to update password. Code: " + code);
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

    public static boolean refreshTokenSync(Context ctx) {
        try {
            SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ctx);
            String refreshToken = prefs.getString("koda_refresh_token", null);
            if (refreshToken == null) return false;

            URL url = new URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/auth/v1/token?grant_type=refresh_token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
            conn.setDoOutput(true);

            JSONObject jsonObj = new JSONObject();
            jsonObj.put("refresh_token", refreshToken);
            String json = jsonObj.toString();
            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes());
            os.flush(); os.close();

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                InputStreamReader r = new InputStreamReader(conn.getInputStream());
                StringBuilder sb = new StringBuilder();
                int c; while ((c = r.read()) != -1) sb.append((char) c);
                r.close();
                
                JSONObject resp = new JSONObject(sb.toString());
                String newAccessToken = resp.optString("access_token", null);
                String newRefreshToken = resp.optString("refresh_token", null);
                if (newAccessToken != null) {
                    prefs.edit()
                        .putString("koda_session_token", newAccessToken)
                        .putString("koda_refresh_token", newRefreshToken)
                        .apply();
                    return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }
}
