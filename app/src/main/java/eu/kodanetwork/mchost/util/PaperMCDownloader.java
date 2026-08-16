package eu.kodanetwork.mchost.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PaperMCDownloader {
    private static final String TAG = "PaperMCDownloader";
    private static final String API_BASE_URL = "https://fill.papermc.io/v3/projects/paper";

    public interface DownloadCallback {
        void onProgress(String message);
        void onSuccess(File jarFile);
        void onError(String error);
    }

    public static void downloadLatestPaperMC(String version, File serverDir, DownloadCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Prüfe aktuelle PaperMC Version für " + version + "...");
                
                // 1. Get latest build for the version
                String buildsUrl = API_BASE_URL + "/versions/" + version + "/builds";
                String buildsJson = fetchJson(buildsUrl);
                if (buildsJson == null) {
                    callback.onError("Fehler beim Abrufen der Builds für Version " + version);
                    return;
                }

                JSONArray buildsArray = new JSONArray(buildsJson);
                if (buildsArray.length() == 0) {
                    callback.onError("Keine Builds gefunden für " + version);
                    return;
                }
                
                JSONObject latestBuildObj = buildsArray.getJSONObject(0);
                int latestBuild = latestBuildObj.getInt("id");
                
                String jarFileName = "paper-" + version + "-" + latestBuild + ".jar";
                File targetJar = new File(serverDir, jarFileName);

                if (targetJar.exists()) {
                    callback.onProgress("Aktuellste Version (" + latestBuild + ") ist bereits installiert.");
                    callback.onSuccess(targetJar);
                    return;
                }

                // Delete older paper versions in the directory
                File[] existingJars = serverDir.listFiles((dir, name) -> name.startsWith("paper-") && name.endsWith(".jar"));
                if (existingJars != null) {
                    for (File oldJar : existingJars) {
                        oldJar.delete();
                    }
                }

                // 2. Download the jar
                callback.onProgress("Lade PaperMC Build " + latestBuild + " herunter...");
                String downloadUrl = latestBuildObj.getJSONObject("downloads").getJSONObject("server:default").getString("url");
                
                HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                
                if (conn.getResponseCode() != 200) {
                    callback.onError("Download fehlgeschlagen mit HTTP " + conn.getResponseCode());
                    return;
                }

                try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(targetJar)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                callback.onProgress("Download erfolgreich abgeschlossen.");
                callback.onSuccess(targetJar);

            } catch (Exception e) {
                Log.e(TAG, "Fehler beim PaperMC Download", e);
                callback.onError("Fehler: " + e.getMessage());
            }
        }).start();
    }

    public static File downloadLatestPaperSync(String version, File serverDir, DownloadCallback callback) throws Exception {
        if (callback != null) callback.onProgress("Prüfe aktuelle PaperMC Version für " + version + "...");
        
        String buildsUrl = API_BASE_URL + "/versions/" + version + "/builds";
        String buildsJson = fetchJson(buildsUrl);
        if (buildsJson == null) {
            throw new Exception("Fehler beim Abrufen der Builds für Version " + version);
        }

        JSONArray buildsArray = new JSONArray(buildsJson);
        if (buildsArray.length() == 0) {
            throw new Exception("Keine Builds gefunden für " + version);
        }
        
        JSONObject latestBuildObj = buildsArray.getJSONObject(0);
        int latestBuild = latestBuildObj.getInt("id");
        
        String jarFileName = "paper-" + version + "-" + latestBuild + ".jar";
        File targetJar = new File(serverDir, jarFileName);

        if (targetJar.exists()) {
            if (callback != null) callback.onProgress("Aktuellste Version (" + latestBuild + ") ist bereits installiert.");
            return targetJar;
        }

        File[] existingJars = serverDir.listFiles((dir, name) -> name.startsWith("paper-") && name.endsWith(".jar"));
        if (existingJars != null) {
            for (File oldJar : existingJars) {
                oldJar.delete();
            }
        }

        if (callback != null) callback.onProgress("Lade PaperMC Build " + latestBuild + " herunter...");
        String downloadUrl = latestBuildObj.getJSONObject("downloads").getJSONObject("server:default").getString("url");
        
        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        
        if (conn.getResponseCode() != 200) {
            throw new Exception("Download fehlgeschlagen mit HTTP " + conn.getResponseCode());
        }

        try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(targetJar)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        return targetJar;
    }

    private static String fetchJson(String urlString) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            
            if (conn.getResponseCode() != 200) {
                return null;
            }
            
            try (InputStream is = conn.getInputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                StringBuilder sb = new StringBuilder();
                while ((len = is.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, len));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Fehler beim API Aufruf", e);
            return null;
        }
    }
}
