package eu.kodanetwork.mchost.util;

import android.content.Context;
import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.security.PraetorSecurity;
import android.widget.Toast;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Pushes the locally saved nickname to koda_users.nickname via the
 * rpc_patch_user RPC. Used by the automatic daily backfill (MainActivity)
 * and the "retry" button in the developer options.
 */
public final class NicknameSync {

    private NicknameSync() {}

    /** @param interactive show toasts with the outcome (dev retry); silent otherwise */
    public static void sync(Context ctx, boolean interactive) {
        android.content.SharedPreferences prefs = App.getPrefs(ctx);
        String nick = prefs.getString("nickname", "");
        if (nick.isEmpty()) {
            if (interactive) Toast.makeText(ctx, "Kein lokaler Nickname gespeichert", Toast.LENGTH_SHORT).show();
            return;
        }
        String appUuid = prefs.getString("app_uuid", "");
        if (appUuid.isEmpty()) {
            if (interactive) Toast.makeText(ctx, "Keine app_uuid", Toast.LENGTH_SHORT).show();
            return;
        }
        // same auth as the working last_active patcher: the plain anon key.
        // The session JWT gets rejected with 401 here (likely expired/RLS-scoped).
        String auth = PraetorSecurity.getSupabaseKey();
        String base = PraetorSecurity.getSupabaseUrl();
        String apikey = PraetorSecurity.getSupabaseKey();

        new Thread(() -> {
            boolean dbHasIt = false;
            String detail = "";
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        base + "/rest/v1/koda_users?select=nickname&app_uuid=eq." + appUuid).openConnection();
                c.setRequestProperty("apikey", apikey);
                c.setRequestProperty("Authorization", "Bearer " + auth);
                Scanner sc = new Scanner(c.getInputStream()).useDelimiter("\\A");
                JSONArray arr = new JSONArray(sc.hasNext() ? sc.next() : "[]");
                dbHasIt = arr.length() > 0 && !arr.getJSONObject(0).optString("nickname", "").isEmpty();
                c.disconnect();
            } catch (Exception e) {
                detail = "read: " + e.getMessage();
            }

            if (dbHasIt) {
                final String d = detail;
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                if (interactive) h.post(() -> Toast.makeText(ctx, "Nickname ist bereits in der DB", Toast.LENGTH_SHORT).show());
                android.util.Log.d("Nickname", "db already has nickname");
                return;
            }

            int code = -1;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        base + "/rest/v1/rpc/rpc_patch_user").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("apikey", apikey);
                c.setRequestProperty("Authorization", "Bearer " + auth);
                String body = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_payload\":{\"nickname\":\"" + nick + "\"}}";
                c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                code = c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {
                detail = "patch: " + e.getMessage();
            }

            final int fCode = code;
            final String fDetail = detail;
            android.util.Log.d("Nickname", "push rpc=" + fCode + " " + fDetail);
            if (interactive) {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> Toast.makeText(ctx,
                        fCode >= 200 && fCode < 300
                                ? "Gesendet ✓ (rpc " + fCode + ")"
                                : "Fehlgeschlagen (rpc " + fCode + ") " + fDetail,
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
