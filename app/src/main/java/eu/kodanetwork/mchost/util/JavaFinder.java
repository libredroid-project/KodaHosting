package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.util.Log;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaFinder {
    private static final String TAG = "KodaHosting";
    private static String cachedPath = null;
    private static boolean checked   = false;

    public static String find(Context ctx) {
        if (checked && cachedPath != null && new File(cachedPath).exists()) return cachedPath;
        checked = true;

        // Primary: JDK 25 extracted by StartOrchestrator
        File jre25Bin = new File(ctx.getFilesDir(), "jre25/bin/java");
        if (jre25Bin.exists()) {
            jre25Bin.setExecutable(true, false);
            cachedPath = jre25Bin.getAbsolutePath();
            eu.kodanetwork.mchost.util.AppLogger.log(TAG, "[Native Setup] ✓ Java bereit: " + cachedPath);
            return cachedPath;
        }

        // Fallback: java_export in native_root (legacy)
        File nativeRoot = new File(ctx.getFilesDir(), "native_root");
        File javaBin = new File(nativeRoot, "usr/bin/java");
        if (javaBin.exists()) {
            javaBin.setExecutable(true, false);
            cachedPath = javaBin.getAbsolutePath();
            eu.kodanetwork.mchost.util.AppLogger.log(TAG, "[Native Setup] ✓ Java bereit (legacy): " + cachedPath);
            return cachedPath;
        }

        eu.kodanetwork.mchost.util.AppLogger.log(TAG, "[Native Setup] ✗ Kein Java gefunden. StartOrchestrator muss zuerst laufen.");
        return null;
    }

    private static void extractAssetZip(Context ctx, String assetName, File destDir) throws java.io.IOException {
        try (InputStream is = ctx.getAssets().open(assetName);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    if (entry.getName().contains("bin/")) {
                        file.setExecutable(true, false);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void applySymlinks(File nativeRoot) {
        File symlinkFile = new File(nativeRoot, "SYMLINKS.txt");
        if (!symlinkFile.exists()) return;

        try (BufferedReader br = new BufferedReader(new java.io.FileReader(symlinkFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.contains("←")) continue;
                String[] parts = line.split("←");
                if (parts.length != 2) continue;

                String targetPath = parts[0].trim();
                String linkPath = parts[1].trim();
                
                if (linkPath.startsWith("./")) linkPath = linkPath.substring(2);
                
                File linkFile = new File(nativeRoot, linkPath);
                linkFile.getParentFile().mkdirs();
                
                if (linkFile.exists() || isSymlink(linkFile)) {
                    linkFile.delete();
                }

                try {
                    // Always try native symlink first
                    Path link = linkFile.toPath();
                    Path target = Paths.get(targetPath);
                    Files.createSymbolicLink(link, target);
                } catch (Exception e) {
                    Log.e(TAG, "Symlink failed for " + linkPath + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            eu.kodanetwork.mchost.util.AppLogger.log(TAG, "Symlink Master Error: " + e.getMessage());
        }
    }

    private static boolean isSymlink(File file) {
        try {
            return Files.isSymbolicLink(file.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static void recursiveChmodX(File f) {
        try {
            if (f.getName().equals("bin") || f.getAbsolutePath().contains("/bin/")) {
                f.setExecutable(true, false);
            }
            f.setReadable(true, false);
            if (f.isDirectory()) {
                File[] subs = f.listFiles();
                if (subs != null) for (File s : subs) recursiveChmodX(s);
            }
        } catch (Exception ignored) {}
    }

    private static void copyAssetFolder(android.content.res.AssetManager assetManager, String fromAssetPath, String toPath) throws java.io.IOException {
        String[] files = assetManager.list(fromAssetPath);
        if (files == null || files.length == 0) return;
        new File(toPath).mkdirs();
        for (String file : files) {
            String assetSubPath = fromAssetPath + "/" + file;
            String destFilePath = toPath + "/" + file;
            if (assetManager.list(assetSubPath).length == 0) {
                // It's a file
                try (InputStream in = assetManager.open(assetSubPath);
                     java.io.OutputStream out = new FileOutputStream(destFilePath)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                File destFile = new File(destFilePath);
                if (destFilePath.contains("bin/") || destFilePath.endsWith(".so")) {
                    destFile.setExecutable(true, false);
                }
            } else {
                // It's a directory
                copyAssetFolder(assetManager, assetSubPath, destFilePath);
            }
        }
    }

    public static String version(Context ctx) { return "OpenJDK 25 (Native)"; }
    public static String getShellPrefix() { return ""; }
    public static boolean available(Context ctx) { return find(ctx) != null; }
    public static void clearCache() { checked = false; cachedPath = null; }
}
