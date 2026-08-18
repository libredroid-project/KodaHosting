package eu.kodanetwork.mchost.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a Modrinth .mrpack modpack into a Fabric server directory:
 * downloads every file from modrinth.index.json (skipping client-only
 * entries), verifies sha1 hashes, then layers overrides/ and
 * server-overrides/ over the directory. Blocking — call off the main thread.
 */
public class ModpackInstaller {

    public interface InstallListener {
        void onPhaseDownload();
        void onPhaseFiles(int done, int total);
        void onDone();
        void onError(String technicalMsg);
    }

    public static void installSync(String mrpackUrl, String expectedSha1, File serverDir, InstallListener listener) throws Exception {
        File tmpDir = new File(serverDir, ".mrpack_tmp");
        try {
            serverDir.mkdirs();
            listener.onPhaseDownload();

            // 1. Download the .mrpack archive and verify its hash
            File mrpackFile = new File(tmpDir, "pack.mrpack");
            mrpackFile.getParentFile().mkdirs();
            downloadTo(mrpackUrl, mrpackFile);
            if (expectedSha1 != null && !expectedSha1.isEmpty()) {
                String actual = ModrinthHelper.getSha1Hash(mrpackFile);
                if (!expectedSha1.equalsIgnoreCase(actual)) {
                    throw new IllegalStateException("sha1 mismatch: expected " + expectedSha1 + " got " + actual);
                }
            }

            // 2. Extract into the temp dir (zip-slip protected)
            unzipSafe(mrpackFile, tmpDir);
            mrpackFile.delete();

            // 3. Parse the index and download all server-relevant files
            File indexFile = new File(tmpDir, "modrinth.index.json");
            if (!indexFile.exists()) throw new IllegalStateException("modrinth.index.json missing");
            JSONObject index = new JSONObject(readFile(indexFile));
            JSONArray files = index.optJSONArray("files");
            int total = files != null ? files.length() : 0;
            int done = 0;
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    String path = f.optString("path", "");
                    if (!isSafePath(path)) throw new IllegalStateException("unsafe path in modpack: " + path);

                    JSONObject env = f.optJSONObject("env");
                    if (env != null && "unsupported".equals(env.optString("server", ""))) {
                        done++;
                        continue;
                    }

                    JSONArray downloads = f.optJSONArray("downloads");
                    if (downloads == null || downloads.length() == 0) { done++; continue; }

                    String sha1 = f.optJSONObject("hashes") != null
                            ? f.getJSONObject("hashes").optString("sha1", "") : "";
                    File target = new File(serverDir, path);
                    target.getParentFile().mkdirs();

                    boolean ok = false;
                    Exception last = null;
                    for (int d = 0; d < downloads.length() && !ok; d++) {
                        try {
                            downloadTo(downloads.getString(d), target);
                            if (sha1.isEmpty() || sha1.equalsIgnoreCase(ModrinthHelper.getSha1Hash(target))) {
                                ok = true;
                            } else {
                                target.delete();
                            }
                        } catch (Exception e) {
                            last = e;
                        }
                    }
                    if (!ok) {
                        throw new IllegalStateException("download failed for " + path
                                + (last != null ? ": " + last.getMessage() : ""));
                    }
                    done++;
                    listener.onPhaseFiles(done, total);
                }
            }

            // 4. Layer overrides/, then server-overrides/ over the server dir
            applyOverrides(new File(tmpDir, "overrides"), serverDir);
            applyOverrides(new File(tmpDir, "server-overrides"), serverDir);

            listener.onDone();
        } catch (Exception e) {
            try { deleteRecursive(tmpDir); } catch (Exception ignored) {}
            listener.onError(e.getMessage() == null ? "unknown error" : e.getMessage());
            throw e;
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    private static boolean isSafePath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.contains("..")) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        if (path.matches("^[A-Za-z]:.*")) return false;
        return true;
    }

    private static void applyOverrides(File src, File dest) {
        if (src == null || !src.exists()) return;
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(dest, child.getName());
            if (child.isDirectory()) {
                target.mkdirs();
                applyOverrides(child, target);
            } else {
                target.getParentFile().mkdirs();
                try (FileInputStream in = new FileInputStream(child);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void downloadTo(String urlStr, File dest) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent", "KodaNetwork/3.0");
        try (InputStream is = c.getInputStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
        } finally {
            c.disconnect();
        }
    }

    private static String readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            StringBuilder sb = new StringBuilder();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) != -1) sb.append(new String(b, 0, r));
            return sb.toString();
        }
    }

    /** Zip extraction with canonical zip-slip protection. */
    private static void unzipSafe(File zip, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            String canonicalDest = destDir.getCanonicalPath() + File.separator;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (!newFile.getCanonicalPath().startsWith(canonicalDest)) {
                    throw new IllegalStateException("zip slip blocked: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = zis.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
