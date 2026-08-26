package eu.kodanetwork.mchost.util;

import android.content.Context;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provisioning for the PumpkinMC server binary (Rust, native aarch64-linux-android).
 * Downloads the tar.xz from the Supabase artifacts bucket first (same pattern as
 * the JRE runtimes) and falls back to the GitHub Actions artifact mirror.
 */
public class PumpkinRuntime {

    private static final AtomicBoolean downloadLock = new AtomicBoolean(false);

    private static final String SUPABASE_URL =
            "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/artifacts/pumpkin/pumpkin-android-arm64.tar.xz";

    // Fallback: latest Actions artifact from the KodaNetwork repo (needs a public release)
    private static final String GITHUB_FALLBACK =
            "https://github.com/libredroid-project/KodaNetwork/releases/latest/download/pumpkin-android-arm64.tar.xz";

    public static File getBinaryFile(Context ctx) {
        return new File(ctx.getFilesDir(), "runtimes/pumpkin/pumpkin-android-arm64");
    }

    public static boolean isInstalled(Context ctx) {
        File f = getBinaryFile(ctx);
        return f.exists() && f.canExecute();
    }

    public static interface Progress {
        void onProgress(int percent, String message);
    }

    /** Blocking — call off the main thread. Returns true when the binary is ready. */
    public static boolean ensureBinarySync(Context ctx, Progress progress) {
        if (isInstalled(ctx)) return true;

        if (!downloadLock.compareAndSet(false, true)) {
            while (downloadLock.get()) {
                try { Thread.sleep(300); } catch (InterruptedException e) { return false; }
            }
            return isInstalled(ctx);
        }
        try {
            File target = getBinaryFile(ctx);
            target.getParentFile().mkdirs();

            String url = resolveUrl(SUPABASE_URL);
            if (url == null) url = resolveUrl(GITHUB_FALLBACK);
            if (url == null) {
                android.util.Log.e("PumpkinRuntime", "no download source reachable");
                return false;
            }

            File tmp = new File(target.getParentFile(), "pumpkin-android-arm64.tar.xz");
            download(url, tmp, progress);
            extractTarXz(tmp, target.getParentFile());
            tmp.delete();
            target.setExecutable(true, false);
            return isInstalled(ctx);
        } catch (Exception e) {
            android.util.Log.e("PumpkinRuntime", "binary provisioning failed", e);
            return false;
        } finally {
            downloadLock.set(false);
        }
    }

    /** Returns the URL when reachable (HTTP 2xx), null otherwise. */
    private static String resolveUrl(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(5000);
            int code = c.getResponseCode();
            c.disconnect();
            return (code >= 200 && code < 300) ? urlStr : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void download(String urlStr, File dest, Progress progress) throws Exception {
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
                if (total > 0 && progress != null) {
                    int pct = (int)(done * 100 / total);
                    if (pct != lastPct && pct % 5 == 0) {
                        lastPct = pct;
                        progress.onProgress(pct, done / 1048576 + "/" + total / 1048576 + " MB");
                    }
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private static void extractTarXz(File archive, File destDir) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(archive);
             XZCompressorInputStream xzIn = new XZCompressorInputStream(fis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {
            TarArchiveEntry entry;
            String canonicalDest = destDir.getCanonicalPath() + File.separator;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (!newFile.getCanonicalPath().startsWith(canonicalDest)) {
                    throw new IllegalStateException("tar slip blocked: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = tarIn.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                }
            }
        }
    }
}
