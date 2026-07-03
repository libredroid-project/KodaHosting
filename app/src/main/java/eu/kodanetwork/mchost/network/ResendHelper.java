package eu.kodanetwork.mchost.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ResendHelper {

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public static void sendOTP(String email, String code, Callback callback) {
        new Thread(() -> {
            try {
                // Endpoint of the Supabase Edge Function we deployed
                URL url = new URL("https://scsezpfrrmpyuapblbxk.supabase.co/functions/v1/send-otp");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                // The anon key is required if JWT verification is not disabled on the edge function
                conn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("code", code);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess());
                } else {
                    InputStreamReader r = new InputStreamReader(conn.getErrorStream());
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    
                    String errMsg = "Error " + responseCode;
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        if (err.has("error")) {
                            errMsg = err.getString("error");
                        }
                    } catch (Exception e) {}
                    
                    final String finalErr = errMsg;
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError(finalErr));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }
}
