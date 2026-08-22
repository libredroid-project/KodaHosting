package eu.kodanetwork.mchost.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.kodanetwork.mchost.model.ServerInstance;

/**
 * Per-server Java runtime resolution and on-demand provisioning.
 *
 * - Java 25 is bundled with the APK (assets/jre25-android-arm64.tar.xz) and extracted by
 *   StartOrchestrator to filesDir/jre25 — the proven default. The bundle is the AngelAuraMC
 *   openjdk build family this manager downloads the other versions from.
 * - Java 8/17/21 are downloaded on demand as android-arm64 tar.xz from
 *   AngelAuraMC/angelauramc-openjdk-build (verified: ELF aarch64, /system/bin/linker64,
 *   NDK r27d). Fabric servers default to 21 because many mods only run on it.
 */
public class RuntimeManager {

    public interface ProgressListener {
        void onProgress(int percent, String message);
    }

    // Primary source: our own Supabase artifacts bucket (public).
    public static class Result {
        public final boolean success;
        public final String failReason;
        public Result(boolean success, String failReason) {
            this.success = success;
            this.failReason = failReason;
        }
        public static Result ok() { return new Result(true, null); }
        public static Result fail(String reason) { return new Result(false, reason); }
    }
    private static String runtimeUrl(int version) {
        return eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl()
                + "/storage/v1/object/public/artifacts/jre" + version + "-android-arm64.tar.xz";
    }


    private static final AtomicBoolean downloadLock = new AtomicBoolean(false);

    /** Auto-pick based on Minecraft version and Server Type */
    public static int resolveAutoVersion(ServerInstance srv) {
        return resolveAutoVersion(srv.getVersion(), srv.getType() == ServerInstance.Type.FABRIC);
    }
    
    public static int resolveAutoVersion(String ver, boolean isFabric) {
        if (ver == null || !ver.startsWith("1.")) return isFabric ? 21 : 25;
        try {
            String[] parts = ver.split("\\.");
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            
            if (minor >= 22) return 25;
            if (minor == 21 && patch >= 11) return 25; // >= 1.21.11 uses 25
            if (minor == 21) return 21;
            if (minor == 20 && patch >= 5) return 21;
            if (minor >= 17) return 17; // 1.17 to 1.20.4
            if (minor <= 16) return 8;  // 1.16.5 and below
        } catch (Exception ignored) {}
        
        return isFabric ? 21 : 25;
    }

    public static boolean isRuntimeInstalled(Context ctx, int version) {
        return getJavaBin(ctx, version) != null;
    }

    /** Path to bin/java of the given runtime, or null when not installed. */
    public static String getJavaBin(Context ctx, int version) {
        File bin = new File(ctx.getFilesDir(), "jre" + version + "/bin/java");
        if (bin.exists()) {
            bin.setExecutable(true, false);
            return bin.getAbsolutePath();
        }
        return null;
    }

    /**
     * Ensures the runtime is installed; downloads + extracts it on first use.
     * Blocking — call off the main thread. Returns Result when the runtime is ready.
     */
    public static Result ensureRuntimeSync(Context ctx, int version, ProgressListener listener) {
        if (isRuntimeInstalled(ctx, version)) return Result.ok();
        if (version == 25) return Result.ok(); // bundled only; StartOrchestrator handles it
        String url = runtimeUrl(version);
        if (url == null) return Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_unknown));

        if (!downloadLock.compareAndSet(false, true)) {
            // Another download is running; wait for it to finish
            while (downloadLock.get()) {
                try { Thread.sleep(300); } catch (InterruptedException e) { return Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_unknown)); }
            }
            return isRuntimeInstalled(ctx, version) ? Result.ok() : Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_install_failed));
        }
        try {
            File targetDir = new File(ctx.getFilesDir(), "jre" + version);
            File tmpDir = new File(ctx.getFilesDir(), "jre" + version + "_tmp");
            deleteRecursive(tmpDir);
            tmpDir.mkdirs();

            File archive = new File(ctx.getCacheDir(), "jre" + version + ".tar.xz");
            try {
                download(url, archive, listener);
                extractTarXzSafe(archive, tmpDir);
            } finally {
                archive.delete();
            }

            // The archive may extract flat (./bin, ./lib) or nested in a single folder
            File contentDir = tmpDir;
            File[] children = tmpDir.listFiles();
            if (children != null && children.length == 1 && children[0].isDirectory()
                    && !new File(tmpDir, "bin/java").exists()) {
                contentDir = children[0];
            }
            if (!new File(contentDir, "bin/java").exists()) {
                throw new IllegalStateException("archive has no bin/java");
            }

            deleteRecursive(targetDir);
            if (!contentDir.renameTo(targetDir)) {
                throw new IllegalStateException("rename to jre" + version + " failed");
            }
            deleteRecursive(tmpDir);
            makeExecutableRecursive(targetDir);
            return Result.ok();
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            android.util.Log.e("RuntimeManager", "jre" + version + " network failed", e);
            return Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_download_unreachable));
        } catch (Exception e) {
            android.util.Log.e("RuntimeManager", "jre" + version + " provisioning failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (msg.contains("archive has no bin/java")) {
                return Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_archive_corrupt));
            }
            return Result.fail(ctx.getString(eu.kodanetwork.mchost.R.string.praetor_jre_reason_download_failed, msg));
        } finally {
            downloadLock.set(false);
        }
    }

    private static void download(String urlStr, File dest, ProgressListener listener) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent", "KodaNetwork/3.0");
        try (InputStream is = c.getInputStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            long total = c.getContentLengthLong();
            long done = 0;
            byte[] buf = new byte[16384];
            int r;
            int lastPct = -1;
            while ((r = is.read(buf)) != -1) {
                fos.write(buf, 0, r);
                done += r;
                if (total > 0 && listener != null) {
                    int pct = (int) (done * 100 / total);
                    if (pct != lastPct && pct % 5 == 0) {
                        lastPct = pct;
                        listener.onProgress(pct, done / 1048576 + "/" + total / 1048576 + " MB");
                    }
                }
            }
        } finally {
            c.disconnect();
        }
    }

    /** tar.xz extraction with canonical zip-slip protection and symlink support. */
    private static void extractTarXzSafe(File archive, File destDir) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(archive);
             org.apache.commons.compress.compressors.xz.XZCompressorInputStream xzIn =
                     new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(fis);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn =
                     new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            String canonicalDest = destDir.getCanonicalPath();
            if (!canonicalDest.endsWith(File.separator)) canonicalDest += File.separator;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                String canonicalNewFile = newFile.getCanonicalPath();
                if (!canonicalNewFile.startsWith(canonicalDest) && !canonicalNewFile.equals(destDir.getCanonicalPath())) {
                    throw new IllegalStateException("archive slip blocked: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else if (entry.isSymbolicLink()) {
                    newFile.getParentFile().mkdirs();
                    try {
                        java.nio.file.Path link = newFile.toPath();
                        java.nio.file.Path target = java.nio.file.Paths.get(entry.getLinkName());
                        if (java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            java.nio.file.Files.delete(link);
                        }
                        java.nio.file.Files.createSymbolicLink(link, target);
                    } catch (Exception e) {
                        android.util.Log.e("RuntimeManager", "Symlink failed: " + e.getMessage());
                    }
                } else {
                    newFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = tarIn.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                }
            }
        }
    }

    private static void makeExecutableRecursive(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) makeExecutableRecursive(f);
            else f.setExecutable(true, false);
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
