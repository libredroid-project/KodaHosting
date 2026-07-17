package eu.kodanetwork.mchost.utils;

import android.content.Context;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient;
import java.io.File;

public class HibernationManager {
    
    public static void hibernateServer(Context context, ServerInstance server, ServerRepo repo) throws Exception {
        if (server.state == ServerInstance.State.HIBERNATED) return;
        
        // 1. Delete DNS entries if any
        if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("koda_prefs", Context.MODE_PRIVATE);
                String token = prefs.getString("koda_session_token", null);
                if (token == null) token = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                
                new SupabaseFunctionsClient(context).deleteDnsLink(token, server.getSubdomain(), server.getBaseDomain());
                
                // Update Supabase to show HIBERNATED
                String domain = server.getSubdomain();
                String jsonBody = "{\"server_version\": \"HIBERNATED\", \"online_players\": 0}";
                okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?id=eq." + server.getId())
                    .patch(body)
                    .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                    .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();
                new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {}
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) { response.close(); }
                });
                
            } catch (Exception e) {
                android.util.Log.e("HibernationManager", "Failed to delete DNS or update status", e);
            }
        }
        
        // 2. Zip the server directory
        File serverDir = new File(server.getServerDir());
        File zipFile = new File(serverDir.getParentFile(), server.getId() + "_hibernated.zip");
        if (serverDir.exists()) {
            ZipUtils.zipFolder(serverDir.getAbsolutePath(), zipFile.getAbsolutePath());
            
            // 3. Delete the original folder
            ZipUtils.deleteDirectory(serverDir);
        }
        
        // 4. Update state
        server.state = ServerInstance.State.HIBERNATED;
        server.setDomainLink(""); // Clear domain link UI
        repo.update(server);
    }
    
    public static void wakeUpServer(Context context, ServerInstance server, ServerRepo repo) throws Exception {
        if (server.state != ServerInstance.State.HIBERNATED) return;
        
        // 1. Check if DNS is occupied
        if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("koda_prefs", Context.MODE_PRIVATE);
            String token = prefs.getString("koda_session_token", null);
            if (token == null) token = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
            
            try {
                boolean isTaken = new SupabaseFunctionsClient(context).checkServerName(token, server.getSubdomain(), server.getBaseDomain());
                if (isTaken) {
                    throw new RuntimeException("DNS_OCCUPIED");
                }
            } catch (Exception e) {
                if ("DNS_OCCUPIED".equals(e.getMessage())) throw e;
                android.util.Log.e("HibernationManager", "Failed to check DNS", e);
            }
        }
        
        File zipFile = new File(new File(server.getServerDir()).getParentFile(), server.getId() + "_hibernated.zip");
        if (zipFile.exists()) {
            ZipUtils.unzip(zipFile.getAbsolutePath(), new File(server.getServerDir()).getParent());
            zipFile.delete();
        } else {
            // Failsafe: if no zip found, recreate dir
            new File(server.getServerDir()).mkdirs();
        }
        
        server.state = ServerInstance.State.OFFLINE;
        server.setLastActive(System.currentTimeMillis());
        repo.update(server);
        
        // Update Supabase to remove HIBERNATED state so the app doesn't re-hibernate it
        if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
            new Thread(() -> {
                try {
                    String domain = server.getSubdomain();
                    String jsonBody = "{\"server_version\": \"" + (server.getVersion() == null ? "1.21.11" : server.getVersion()) + "\"}";
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json"));
                    okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?id=eq." + server.getId())
                        .patch(body)
                        .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                        .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();
                    new okhttp3.OkHttpClient().newCall(request).execute().close();
                } catch (Exception e) {
                    android.util.Log.e("HibernationManager", "Failed to clear hibernation status", e);
                }
            }).start();
        }
    }
}
