package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.kodanetwork.mchost.model.ServerInstance;

public class ModrinthHelper {

    private static final String API_BASE = "https://api.modrinth.com/v2";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class ModrinthProject {
        public String id;
        public String title;
        public String description;
        public String author;
        public String iconUrl;
    }

    public interface SearchCallback {
        void onResult(List<ModrinthProject> results);
        void onError(String err);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File file);
        void onError(String err);
    }

    public static void search(String query, ServerInstance.Type type, SearchCallback cb) {
        executor.submit(() -> {
            try {
                String projectType = (type == ServerInstance.Type.PAPER || type == ServerInstance.Type.PURPUR) ? "plugin" : "mod";
                String loader = type.name().toLowerCase();
                if (type == ServerInstance.Type.PURPUR) loader = "paper"; // Purpur uses paper plugins
                
                String facets = "[[\"project_type:" + projectType + "\"],[\"categories:" + loader + "\"]]";
                String encodedFacets = URLEncoder.encode(facets, "UTF-8");
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                
                String urlStr = API_BASE + "/search?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=15";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    mainHandler.post(() -> cb.onError("HTTP " + responseCode));
                    return;
                }
                
                InputStream is = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r));
                is.close();
                
                JSONObject res = new JSONObject(sb.toString());
                JSONArray hits = res.getJSONArray("hits");
                
                List<ModrinthProject> projects = new ArrayList<>();
                for (int i = 0; i < hits.length(); i++) {
                    JSONObject hit = hits.getJSONObject(i);
                    ModrinthProject p = new ModrinthProject();
                    p.id = hit.getString("project_id");
                    p.title = hit.getString("title");
                    p.description = hit.getString("description");
                    p.author = hit.getString("author");
                    p.iconUrl = hit.optString("icon_url", "");
                    projects.add(p);
                }
                
                mainHandler.post(() -> cb.onResult(projects));
                
            } catch (Exception e) {
                mainHandler.post(() -> cb.onError(e.getMessage()));
            }
        });
    }

    public static void loadIcon(String url, ImageView iv) {
        if (url == null || url.isEmpty()) return;
        iv.setTag(url);
        executor.submit(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null) {
                    mainHandler.post(() -> {
                        if (url.equals(iv.getTag())) iv.setImageBitmap(bmp);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public static void autoDownload(String projectId, ServerInstance server, DownloadCallback cb) {
        executor.submit(() -> {
            try {
                String loader = server.getType().name().toLowerCase();
                if (server.getType() == ServerInstance.Type.PURPUR) loader = "paper";
                
                String mcVer = server.getVersion();
                
                java.util.List<String> compLoaders = new java.util.ArrayList<>();
                if (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) {
                    compLoaders.add("paper");
                    compLoaders.add("spigot");
                    compLoaders.add("bukkit");
                } else {
                    compLoaders.add(loader);
                }
                
                JSONArray versions = null;
                for (String l : compLoaders) {
                    String qL = URLEncoder.encode("[\"" + l + "\"]", "UTF-8");
                    String qG = URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                    
                    String u = API_BASE + "/project/" + projectId + "/version?loaders=" + qL + "&game_versions=" + qG;
                    HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        byte[] buf = new byte[4096]; int r;
                        while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r));
                        is.close();
                        versions = new JSONArray(sb.toString());
                        if (versions.length() > 0) break;
                    }
                    

                }
                
                if (versions == null || versions.length() == 0) {
                    final String fVer = mcVer;
                    final String fLoader = loader;
                    mainHandler.post(() -> cb.onError("No compatible version found for " + fVer + " (" + fLoader + ")"));
                    return;
                }
                
                // Get the newest matching version (index 0 usually)
                JSONObject latest = versions.getJSONObject(0);
                String latestVersionId = latest.getString("id");
                
                if (latestVersionId.equals(server.pluginVersions.get(projectId))) {
                    mainHandler.post(() -> cb.onError("Already up-to-date."));
                    return;
                }
                
                JSONArray files = latest.getJSONArray("files");
                if (files.length() == 0) {
                    mainHandler.post(() -> cb.onError("No files found in version."));
                    return;
                }
                
                // Find primary file or first file
                JSONObject fileObj = files.getJSONObject(0);
                for (int i = 0; i < files.length(); i++) {
                    if (files.getJSONObject(i).optBoolean("primary", false)) {
                        fileObj = files.getJSONObject(i);
                        break;
                    }
                }
                
                String downloadUrl = fileObj.getString("url");
                String fileName = fileObj.getString("filename");
                
                // Download file
                String folderName = (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) ? "plugins" : "mods";
                File targetDir = new File(server.getServerDir(), folderName);
                targetDir.mkdirs();
                
                // Delete existing old version of this plugin
                // Since we don't know the exact old filename, we can rely on standard naming if possible, but Modrinth jars vary.
                // A better approach is that `UpdateServerActivity` handles cleanup, but for now we just download it.
                // Actually, let's search for existing jars that might be the old version? 
                // Or maybe the caller handles it.
                
                File targetFile = new File(targetDir, fileName);
                
                HttpURLConnection dlConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                dlConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                dlConn.setInstanceFollowRedirects(true);
                
                int dlResponseCode = dlConn.getResponseCode();
                if (dlResponseCode >= 300) {
                    mainHandler.post(() -> cb.onError("Download HTTP " + dlResponseCode));
                    return;
                }
                
                
                String oldVersionId = server.pluginVersions.get(projectId);
                deleteOldVersion(oldVersionId, targetDir, fileName);
                
                long total = dlConn.getContentLengthLong();
                InputStream dlIs = dlConn.getInputStream();
                FileOutputStream fos = new FileOutputStream(targetFile);
                long downloaded = 0;
                long lastCb = 0;
                byte[] dlBuf = new byte[8192];
                int dlR;
                while ((dlR = dlIs.read(dlBuf)) != -1) {
                    fos.write(dlBuf, 0, dlR);
                    downloaded += dlR;
                    long now = System.currentTimeMillis();
                    if (now - lastCb > 500 && total > 0) {
                        int pct = (int) ((downloaded * 100) / total);
                        mainHandler.post(() -> cb.onProgress(pct));
                        lastCb = now;
                    }
                }
                fos.close();
                dlIs.close();
                
                server.pluginVersions.put(projectId, latestVersionId);
                
                mainHandler.post(() -> cb.onSuccess(targetFile));
                
            } catch (Exception e) {
                mainHandler.post(() -> cb.onError("Download exception: " + e.getMessage()));
            }
        });
    }

    public static void deleteOldVersion(String oldVersionId, File targetDir, String newFileName) {
        if (oldVersionId == null || oldVersionId.isEmpty()) return;
        try {
            String oldU = API_BASE + "/version/" + oldVersionId;
            HttpURLConnection oldConn = (HttpURLConnection) new URL(oldU).openConnection();
            oldConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
            if (oldConn.getResponseCode() == 200) {
                InputStream ois = oldConn.getInputStream();
                java.util.Scanner os = new java.util.Scanner(ois).useDelimiter("\\A");
                String oldRes = os.hasNext() ? os.next() : "";
                ois.close();
                JSONObject oldObj = new JSONObject(oldRes);
                JSONArray oldFiles = oldObj.getJSONArray("files");
                for (int i = 0; i < oldFiles.length(); i++) {
                    String oldFn = oldFiles.getJSONObject(i).getString("filename");
                    File oldF = new File(targetDir, oldFn);
                    if (oldF.exists() && !oldF.getName().equals(newFileName)) {
                        oldF.delete();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static String getLatestVersionIdSync(String projectId, ServerInstance server) {
        try {
            String loader = server.getType().name().toLowerCase();
            if (server.getType() == ServerInstance.Type.PURPUR) loader = "paper";
            
            String mcVer = server.getVersion();
            
            java.util.List<String> compLoaders = new java.util.ArrayList<>();
            if (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) {
                compLoaders.add("paper");
                compLoaders.add("spigot");
                compLoaders.add("bukkit");
            } else {
                compLoaders.add(loader);
            }
            
            JSONArray versions = null;
            for (String l : compLoaders) {
                String qL = URLEncoder.encode("[\"" + l + "\"]", "UTF-8");
                String qG = URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                
                String u = API_BASE + "/project/" + projectId + "/version?loaders=" + qL + "&game_versions=" + qG;
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[4096]; int r;
                    while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r));
                    is.close();
                    versions = new JSONArray(sb.toString());
                    if (versions.length() > 0) break;
                }
            }
            
            if (versions == null || versions.length() == 0) return null;
            return versions.getJSONObject(0).getString("id");
        } catch (Exception e) {
            return null;
        }
    }
    
    public static File autoDownloadSync(String projectId, ServerInstance server) {
        try {
            String loader = server.getType().name().toLowerCase();
            if (server.getType() == ServerInstance.Type.PURPUR) loader = "paper";
            String mcVer = server.getVersion();
            
            String urlStr = API_BASE + "/project/" + projectId;
            HttpURLConnection checkConn = (HttpURLConnection) new URL(urlStr).openConnection();
            checkConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
            checkConn.setRequestMethod("GET");
            if (checkConn.getResponseCode() != 200) {
                // Not found. Fallback to search
                String projectType = (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) ? "plugin" : "mod";
                String catFacets;
                if (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) {
                    catFacets = "[\"categories:paper\",\"categories:spigot\",\"categories:bukkit\"]";
                } else {
                    catFacets = "[\"categories:" + loader + "\"]";
                }
                String facetsStr = "[[\"project_type:" + projectType + "\"]," + catFacets + "]";
                String encodedFacets = URLEncoder.encode(facetsStr, "UTF-8");
                String searchUrl = API_BASE + "/search?query=" + URLEncoder.encode(projectId, "UTF-8") + "&facets=" + encodedFacets + "&limit=10";
                HttpURLConnection searchConn = (HttpURLConnection) new URL(searchUrl).openConnection();
                searchConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                if (searchConn.getResponseCode() == 200) {
                    InputStream sis = searchConn.getInputStream();
                    StringBuilder ssb = new StringBuilder();
                    byte[] sbuf = new byte[4096];
                    int sr;
                    while ((sr = sis.read(sbuf)) != -1) ssb.append(new String(sbuf, 0, sr));
                    sis.close();
                    JSONObject res = new JSONObject(ssb.toString());
                    JSONArray hits = res.getJSONArray("hits");
                    if (hits.length() > 0) {
                        projectId = hits.getJSONObject(0).getString("project_id");
                    } else {
                        return null; // Search failed to find any match
                    }
                } else {
                    return null;
                }
            }
            
            java.util.List<String> compLoaders = new java.util.ArrayList<>();
            if (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) {
                compLoaders.add("paper");
                compLoaders.add("spigot");
                compLoaders.add("bukkit");
            } else {
                compLoaders.add(loader);
            }
            
            JSONArray versions = null;
            for (String l : compLoaders) {
                String qL = URLEncoder.encode("[\"" + l + "\"]", "UTF-8");
                String qG = URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                
                String u = API_BASE + "/project/" + projectId + "/version?loaders=" + qL + "&game_versions=" + qG;
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[4096]; int r;
                    while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r));
                    is.close();
                    versions = new JSONArray(sb.toString());
                    if (versions.length() > 0) break;
                }

            }
            
            if (versions == null || versions.length() == 0) return null;
            
            JSONObject latest = versions.getJSONObject(0);
            String latestVersionId = latest.getString("id");
            JSONArray files = latest.getJSONArray("files");
            if (files.length() == 0) return null;
            
            JSONObject fileObj = files.getJSONObject(0);
            for (int i = 0; i < files.length(); i++) {
                if (files.getJSONObject(i).optBoolean("primary", false)) {
                    fileObj = files.getJSONObject(i);
                    break;
                }
            }
            
            String downloadUrl = fileObj.getString("url");
            String fileName = fileObj.getString("filename");
            
            String folderName = (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) ? "plugins" : "mods";
            File targetDir = new File(server.getServerDir(), folderName);
            targetDir.mkdirs();
            File targetFile = new File(targetDir, fileName);
            
            if (latestVersionId.equals(server.pluginVersions.get(projectId)) && targetFile.exists()) {
                return targetFile; // Already up-to-date
            }
            
            HttpURLConnection dlConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            dlConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
            dlConn.setInstanceFollowRedirects(true);
            
            if (dlConn.getResponseCode() >= 300) return null;
            
            
            String oldVersionId = server.pluginVersions.get(projectId);
            deleteOldVersion(oldVersionId, targetDir, fileName);
            
            InputStream dlIs = dlConn.getInputStream();
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] dlBuf = new byte[8192];
            int dlR;
            while ((dlR = dlIs.read(dlBuf)) != -1) fos.write(dlBuf, 0, dlR);
            fos.close();
            dlIs.close();
            
            server.pluginVersions.put(projectId, latestVersionId);
            
            return targetFile;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getSha1Hash(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            InputStream is = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            is.close();
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void scanAndUpdateImportedPluginSync(File jarFile, ServerInstance server, Handler handler, android.widget.TextView loadingText) {
        try {
            String hash = getSha1Hash(jarFile);
            if (hash == null) return;

            String urlStr = API_BASE + "/version_file/" + hash + "?algorithm=sha1";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
            
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String res = s.hasNext() ? s.next() : "";
                is.close();
                
                JSONObject versionObj = new JSONObject(res);
                String projectId = versionObj.getString("project_id");
                String versionId = versionObj.getString("id");
                
                server.pluginVersions.put(projectId, versionId);
                
                if (handler != null && loadingText != null) {
                    handler.post(() -> loadingText.setText("Update: " + jarFile.getName() + " ..."));
                }
                
                File newJar = autoDownloadSync(projectId, server);
                if (newJar != null && newJar.exists()) {
                    if (!jarFile.getAbsolutePath().equals(newJar.getAbsolutePath())) {
                        jarFile.delete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
