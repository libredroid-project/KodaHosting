package eu.kodanetwork.mchost.security;

import android.content.Context;
import android.util.Log;

import com.scottyab.rootbeer.RootBeer;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AntiTamperSystem {

    private static final String TAG = "AntiTamperSystem";
    private static Context appContext;
    private static boolean hasChecked = false;

    private static final String[] HIGH_RISK_APPS = {
        "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su",
        "com.koushikdutta.superuser", "com.thirdparty.superuser", "com.yellowes.su",
        "com.zachspong.temprootremovejb", "com.ramdroid.appquarantine",
        "com.formyhm.hiderootPremium", "com.amphoras.hidemyroot", "com.amphoras.hidemyrootadfree",
        "com.saurik.substrate", "de.robv.android.xposed.installer", "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus", "com.android.vending.billing.InAppBillingService.COIN",
        "com.chelpus.lackypatch", "com.dimonvideo.luckypatcher", "com.forpda.lp"
    };

    public static void executePermanentBanNative(String reason) {
        if (appContext != null) {
            eu.kodanetwork.mchost.util.AppLogger.log(TAG, "Native Inotify Triggered: " + reason);
            executePermanentBan(appContext, reason);
        }
    }

    /**
     * Checks for root and tampering. If detected, flags the user as High Risk on Supabase.
     */
    public static void check(Context context) {
        appContext = context.getApplicationContext();

        // 1. Create honeypot file for native inotify
        try {
            java.io.File honeypot = new java.io.File(context.getApplicationInfo().dataDir, "koda_backend_auth.xml");
            if (!honeypot.exists()) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(honeypot);
                fos.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<config>\n    <key>DO_NOT_SHARE_THIS_KEY_12345</key>\n</config>".getBytes());
                fos.close();
            }
            // Start the native inotify watcher
            eu.kodanetwork.mchost.security.PraetorSecurity.startInotifyWatcher(context.getApplicationInfo().dataDir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup honeypot", e);
        }

        if (hasChecked) return;
        hasChecked = true;

        new Thread(() -> {
            try {
                // ALWAYS check server ban FIRST - this takes priority over everything
                boolean isBannedOnServer = checkServerForBan(context);
                if (isBannedOnServer) return; // Already banned, don't check anything else

                // Check root
                RootBeer rootBeer = new RootBeer(context);
                boolean isRooted = rootBeer.isRooted();
                if (isRooted) {
                    Log.w(TAG, "Rooted device detected! Flagging as High-Risk.");
                    flagUserAsHighRisk(context, "root_detected");
                }

                // Check USB Debugging AFTER server ban check
                boolean usbDebugging = android.provider.Settings.Global.getInt(
                    context.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
                if (usbDebugging) {
                    Log.w(TAG, "USB Debugging is enabled. Flagging as High-Risk.");
                    flagUserAsHighRisk(context, "usb_debugging_enabled");
                }
            } catch (Exception e) {
                Log.e(TAG, "AntiTamper check failed", e);
            }
        }).start();
    }

    private static boolean checkServerForBan(Context context) {
        try {
            String hwid = HWIDManager.getDeviceHWID(context);
            URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/get_hwid_ban_type");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("check_hwid", hwid);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                is.close();

                String banType = response.trim().replace("\"", "");

                if ("scary".equals(banType) || "normal".equals(banType)) {
                    Log.w(TAG, "Server confirmed HWID ban. Destroying assets and locking device.");
                    destroyAllUserAssets(context);
                    
                    if ("scary".equals(banType)) {
                        eu.kodanetwork.mchost.App.getPrefs(context).edit()
                            .putBoolean("PERM_BANNED_SCARY", true).apply();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            android.content.Intent intent = new android.content.Intent(context, eu.kodanetwork.mchost.ui.ScaryBannedActivity.class);
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            context.startActivity(intent);
                        });
                    } else {
                        eu.kodanetwork.mchost.App.getPrefs(context).edit()
                            .putBoolean("PERM_BANNED_HWID", true).apply();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            android.content.Intent intent = new android.content.Intent(context, eu.kodanetwork.mchost.ui.BannedActivity.class);
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            context.startActivity(intent);
                        });
                    }
                    return true;
                }
                // "none" = not banned, continue
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check server for ban", e);
        }
        return false;
    }

    private static void destroyAllUserAssets(Context context) {
        new Thread(() -> {
            try {
                eu.kodanetwork.mchost.model.ServerRepo repo = eu.kodanetwork.mchost.model.ServerRepo.get(context);
                java.util.List<eu.kodanetwork.mchost.model.ServerInstance> servers = repo.all();
                String anonKey = PraetorSecurity.getSupabaseKey();
                String token = eu.kodanetwork.mchost.App.getPrefs(context).getString("koda_session_token", null);
                String authHeader = token != null ? "Bearer " + token : "Bearer " + anonKey;

                for (eu.kodanetwork.mchost.model.ServerInstance server : servers) {
                    try {
                        // 1. Delete DNS Link
                        if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
                            new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(context)
                                .deleteDnsLink("", server.getSubdomain());
                        }

                        // 2. PATCH to change host and server_version
                        java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) new java.net.URL("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?host=eq." + server.getSubdomain()).openConnection();
                        patchConn.setRequestMethod("PATCH");
                        patchConn.setRequestProperty("apikey", anonKey);
                        patchConn.setRequestProperty("Authorization", authHeader);
                        patchConn.setRequestProperty("Content-Type", "application/json");
                        patchConn.setDoOutput(true);
                        String jsonPatch = "{\"host\": \"deleted_" + server.getSubdomain() + "\", \"server_version\": \"DELETED\"}";
                        patchConn.getOutputStream().write(jsonPatch.getBytes());
                        patchConn.getResponseCode();

                        // 3. Try to DELETE the row
                        java.net.URL url = new java.net.URL("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?host=eq.deleted_" + server.getSubdomain());
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("DELETE");
                        conn.setRequestProperty("apikey", anonKey);
                        conn.setRequestProperty("Authorization", authHeader);
                        conn.getResponseCode();

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to destroy server asset", e);
                    }
                    // Delete local files
                    try {
                        java.io.File dir = new java.io.File(server.getServerDir());
                        if (dir.exists()) {
                            deleteRecursively(dir);
                        }
                    } catch (Exception ignored) {}
                }
                
                // Clear local repo
                for (eu.kodanetwork.mchost.model.ServerInstance s : servers) {
                    repo.delete(s.getId());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error destroying user assets", e);
            }
        }).start();
    }

    private static void deleteRecursively(java.io.File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            java.io.File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private static void flagUserAsHighRisk(Context context, String reason) {
        try {
            String hwid = HWIDManager.getDeviceHWID(context);
            String sessionToken = eu.kodanetwork.mchost.App.getPrefs(context).getString("koda_session_token", null);
            String appUuid = eu.kodanetwork.mchost.App.getPrefs(context).getString("app_uuid", null);

            // Attempt to call RPC report_high_risk
            URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/report_high_risk");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
            
            // If logged in, use their token to link the account in the backend log
            if (sessionToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
            } else {
                conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey());
            }

            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("p_hwid", hwid);
            payload.put("p_reason", reason);
            if (appUuid != null && !appUuid.equals("UNLINKED") && appUuid.length() == 36) {
                payload.put("p_user_uuid", appUuid);
            }

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d(TAG, "Report tamper response code: " + code);
            if (code != 200) {
                java.io.InputStream es = conn.getErrorStream();
                if (es != null) {
                    java.util.Scanner scanner = new java.util.Scanner(es).useDelimiter("\\A");
                    String err = scanner.hasNext() ? scanner.next() : "";
                    Log.e(TAG, "Report tamper error response: " + err);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to report tamper", e);
        }
    }
    
    /**
     * Instantly permanently bans the user by updating the backend and locking the app.
     * Also deeply wipes all local application data so only the ban screen remains.
     */
    public static void executePermanentBan(Context context, String severityReason) {
        // Flag in SharedPreferences for scary ban (use fallback to avoid crypto loop if crypto is tampered)
        context.getSharedPreferences("koda_settings_enc_fallback", Context.MODE_PRIVATE).edit().putBoolean("PERM_BANNED_SCARY", true).apply();

        new Thread(() -> {
            flagUserAsHighRisk(context, "PERMANENT_BAN_" + severityReason);
            
            // Actually report the tamper to banned_hwids so the ban propagates!
            try {
                String hwid = HWIDManager.getDeviceHWID(context);
                String sessionToken = context.getSharedPreferences("koda_settings_enc_fallback", Context.MODE_PRIVATE).getString("koda_session_token", null);
                String appUuid = context.getSharedPreferences("koda_settings_enc_fallback", Context.MODE_PRIVATE).getString("app_uuid", null);

                URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/report_tamper");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
                if (sessionToken != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
                } else {
                    conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey());
                }
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("p_hwid", hwid);
                payload.put("p_reason", "PERMANENT_BAN_" + severityReason);
                if (appUuid != null && !appUuid.equals("UNLINKED") && appUuid.length() == 36) {
                    payload.put("p_user_uuid", appUuid);
                }

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();
                int code = conn.getResponseCode();
                eu.kodanetwork.mchost.util.AppLogger.log(TAG, "report_tamper response: " + code);
                if (code != 200 && code != 204) {
                    java.io.InputStream es = conn.getErrorStream();
                    if (es != null) {
                        java.util.Scanner scanner = new java.util.Scanner(es).useDelimiter("\\A");
                        String err = scanner.hasNext() ? scanner.next() : "";
                        eu.kodanetwork.mchost.util.AppLogger.log(TAG, "report_tamper error: " + err);
                    }
                }
            } catch (Exception e) {
                eu.kodanetwork.mchost.util.AppLogger.log(TAG, "Failed to call report_tamper: " + e.getMessage());
            }
            
            // Launch ScaryBannedActivity
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                android.content.Intent intent = new android.content.Intent(context, eu.kodanetwork.mchost.ui.ScaryBannedActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            });
            
            // Give time for UI and network to settle
            try { Thread.sleep(500); } catch (Exception ignored) {}
            
            // Wipe local files
            try {
                java.io.File filesDir = context.getFilesDir();
                if (filesDir != null && filesDir.exists()) {
                    deleteRecursive(filesDir);
                }
                java.io.File cacheDir = context.getCacheDir();
                if (cacheDir != null && cacheDir.exists()) {
                    deleteRecursive(cacheDir);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    private static void deleteRecursive(java.io.File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            java.io.File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
    
    public static boolean isBannedLocally(Context context) {
        return eu.kodanetwork.mchost.App.getPrefs(context).getBoolean("PERM_BANNED_HWID", false);
    }
    
    public static boolean isScaryBannedLocally(Context context) {
        return eu.kodanetwork.mchost.App.getPrefs(context).getBoolean("PERM_BANNED_SCARY", false);
    }
}
