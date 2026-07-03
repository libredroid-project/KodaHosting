package eu.kodanetwork.mchost.orchestration;

import android.content.Context;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import eu.kodanetwork.mchost.util.AppLogger;

public class DatabaseOrchestrator {
    private static final String TAG = "KodaHosting";

    public static void ensureDatabasesExtracted(Context context) {
        new Thread(() -> {
            try {
                extractArchive(context, "mariadb-android-arm64.tar.xz", "mariadb");
                extractArchive(context, "redis-android-arm64.tar.xz", "redis");
            } catch (Exception e) {
                AppLogger.log(TAG, "[DB Setup] ✗ Failed to extract databases: " + e.getMessage());
            }
        }).start();
    }

    private static void extractArchive(Context context, String assetName, String destFolder) {
        File destDir = new File(context.getFilesDir(), destFolder);
        File readyMarker = new File(destDir, ".symlinks_fixed_2");
        
        if (readyMarker.exists()) {
            AppLogger.log(TAG, "[DB Setup] " + assetName + " already extracted properly.");
            return;
        }

        AppLogger.log(TAG, "[DB Setup] Extracting " + assetName + " to " + destDir.getAbsolutePath() + "...");
        
        // Delete old broken directory
        if (destDir.exists()) {
            deleteRecursive(destDir);
        }
        
        // We use system tar to extract tar.xz as it's the most reliable way on Android.
        // First we copy the .tar.xz to cache, then use tar -xf.
        File tempTar = new File(context.getCacheDir(), assetName);
        try {
            try (InputStream is = context.getAssets().open(assetName);
                 FileOutputStream fos = new FileOutputStream(tempTar)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            destDir.mkdirs();
            
            try {
                eu.kodanetwork.mchost.util.TarXzUtil.extract(tempTar.getAbsolutePath(), destDir.getAbsolutePath());
                // chmod all files recursively to avoid permission denied
                makeExecutableRecursive(destDir);
                
                // create the .ready marker
                new File(destDir, ".symlinks_fixed_2").createNewFile();
                AppLogger.log(TAG, "[DB Setup] ✓ " + assetName + " extracted successfully.");
            } catch (Exception ex) {
                AppLogger.log(TAG, "[DB Setup] ✗ extraction failed for " + assetName + ": " + ex.getMessage());
            } finally {
                tempTar.delete();
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "[DB Setup] ✗ Exception extracting " + assetName + ": " + e.getMessage());
        } finally {
            if (tempTar.exists()) tempTar.delete();
        }
    }

    private static void deleteRecursive(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private static void makeExecutableRecursive(File file) {
        if (!file.exists()) return;
        file.setExecutable(true, false);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    makeExecutableRecursive(child);
                }
            }
        }
    }
}
