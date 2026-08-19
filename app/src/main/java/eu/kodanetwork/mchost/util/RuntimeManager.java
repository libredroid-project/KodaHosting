package eu.kodanetwork.mchost.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import eu.kodanetwork.mchost.model.ServerInstance;

/**
 * Per-server Java runtime resolution and on-demand provisioning.
 *
 * - Java 25 is bundled with the APK (assets/jre25-android-arm64.tar.xz) and extracted by
 *   StartOrchestrator to filesDir/jre25 — the proven default.
 * - Java 21 is downloaded on demand (Amethyst/Pojav ecosystem build: QuestCraft++
 *   android-openjdk-build-multiarch fork, aarch64 Android NDK build, verified ELF).
 *   Fabric servers default to 21 because many mods only run on it.
 * - Java 17 is reserved for a future release — the Pojav multiarch "arm64" jre17 build is
 *   a Mach-O/iOS binary and unusable on Android; a proper Android aarch64 build is needed.
 */
public class RuntimeManager {

    public interface ProgressListener {
        void onProgress(int percent, String message);
    }

    // Verified aarch64 Android build (ELF, NDK r25c, bin/java + lib/server/libjvm.so)
    private static final String JRE21_URL =
            "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/download/jre22-6.0.0/JRE-21.zip";

    private static final AtomicBoolean downloadLock = new AtomicBoolean(false);

    /** Auto-pick: Fabric must run on Java 21 (mods break on 25), everything else uses 25. */
    public static int resolveAutoVersion(ServerInstance srv) {
        if (srv.getType() == ServerInstance.Type.FABRIC) return 21;
        return 25;
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
     * Blocking — call off the main thread. Returns true when the runtime is ready.
     */
    public static boolean ensureRuntimeSync(Context ctx, int version, ProgressListener listener) {
        if (isRuntimeInstalled(ctx, version)) return true;
        if (version == 25) return false; // bundled only; StartOrchestrator handles it
        if (version != 21) return false; // not provisionable yet

        if (!downloadLock.compareAndSet(false, true)) {
            // Another download is running; wait for it to finish
            while (downloadLock.get()) {
                try { Thread.sleep(300); } catch (InterruptedException e) { return false; }
            }
            return isRuntimeInstalled(ctx, version);
        }
        try {
            File targetDir = new File(ctx.getFilesDir(), "jre" + version);
            File tmpDir = new File(ctx.getFilesDir(), "jre" + version + "_tmp");
            deleteRecursive(tmpDir);
            tmpDir.mkdirs();

            File zipFile = new File(ctx.getCacheDir(), "jre" + version + ".zip");
            try {
                download(JRE21_URL, zipFile, listener);
                unzipSafe(zipFile, tmpDir);
            } finally {
                zipFile.delete();
            }

            // The zip may extract flat (bin/, lib/) or nested in a single folder
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
            return true;
        } catch (Exception e) {
            android.util.Log.e("RuntimeManager", "jre" + version + " provisioning failed", e);
            return false;
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

    /** Zip extraction with canonical zip-slip protection. */
    private static void unzipSafe(File zip, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zip))) {
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
