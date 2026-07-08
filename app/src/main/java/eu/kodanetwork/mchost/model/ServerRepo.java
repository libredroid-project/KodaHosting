package eu.kodanetwork.mchost.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerRepo {

    private static ServerRepo inst;
    private final SharedPreferences sp;
    private final Context context;
    private final List<ServerInstance> list = new ArrayList<>();
    private final List<Runnable> listeners  = new ArrayList<>();

    private ServerRepo(Context ctx) {
        this.context = ctx.getApplicationContext();
        sp = ctx.getApplicationContext().getSharedPreferences("koda_v3", Context.MODE_PRIVATE);
        load();
    }

    public static ServerRepo get(Context ctx) {
        if (inst == null) inst = new ServerRepo(ctx);
        return inst;
    }

    public List<ServerInstance> all() { return new ArrayList<>(list); }

    public ServerInstance byId(String id) {
        for (ServerInstance s : list) if (s.getId().equals(id)) return s;
        return null;
    }

    public void add(ServerInstance s) {
        if (s.getId() == null) s.setId(UUID.randomUUID().toString());
        list.add(s);
        save(); notify_();
    }

    public void update(ServerInstance s) {
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).getId().equals(s.getId())) { list.set(i, s); break; }
        save(); notify_();
    }

    public void delete(String id) {
        list.removeIf(s -> s.getId().equals(id));
        save(); notify_();
    }

    public void addListener(Runnable r)    { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    private void notify_()                 { for (Runnable r : listeners) r.run(); }

    private void save() {
        try {
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Saving " + list.size() + " servers...");
            JSONArray a = new JSONArray();
            for (ServerInstance s : list) a.put(s.toJson());
            String raw = a.toString();
            sp.edit().putString("servers", raw).apply();
            
            // Generate and save signature in secure keystore
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                eu.kodanetwork.mchost.App.getPrefs(context).edit().putString("repo_sig", hexString.toString()).apply();
            } catch (Exception ignored) {}
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Persistence requested asynchronously");
        } catch (JSONException e) {
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Save failed: " + e.getMessage());
        }
    }

    private void load() {
        String raw = sp.getString("servers", null);
        if (raw == null) {
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "No saved servers found.");
            return;
        }
        
        // Check file integrity
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String expectedSig = eu.kodanetwork.mchost.App.getPrefs(context).getString("repo_sig", null);
            if (expectedSig != null && !expectedSig.equals(hexString.toString())) {
                // TAMPER DETECTED!
                eu.kodanetwork.mchost.security.AntiTamperSystem.executePermanentBan(context, "FILE_TAMPER_DETECTED");
                eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "CRITICAL: Database tamper detected! Hash mismatch.");
                list.clear();
                return;
            }
        } catch (Exception ignored) {}

        try {
            JSONArray a = new JSONArray(raw);
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Loading " + a.length() + " servers from JSON...");
            list.clear();
            for (int i = 0; i < a.length(); i++) {
                try {
                    list.add(ServerInstance.fromJson(a.getJSONObject(i)));
                } catch (Exception e) {
                    eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Error loading server index " + i + ": " + e.getMessage());
                }
            }
        } catch (JSONException e) {
            eu.kodanetwork.mchost.util.AppLogger.log("ServerRepo", "Load failed: " + e.getMessage());
        }
    }
}
