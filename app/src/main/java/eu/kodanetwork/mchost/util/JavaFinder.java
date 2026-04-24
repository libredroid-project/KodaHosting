package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Finds Java by extracting the bundled jre17.tar.gz from assets.
 */
public class JavaFinder {
    private static final String TAG = "KodaHosting";

    private static String cachedPath = null;
    private static boolean checked   = false;

    public static String find(Context ctx) {
        if (checked) return cachedPath;
        checked = true;

        File termuxJava = new File("/data/data/com.termux/files/usr/bin/java");
        if (termuxJava.exists() && termuxJava.canExecute()) {
            cachedPath = termuxJava.getAbsolutePath();
            Log.i(TAG, "Termux Java found at " + cachedPath);
            return cachedPath;
        }

        // Bundled fallback
        File jreDir = new File(ctx.getFilesDir(), "jre17");
        File javaBin = new File(jreDir, "bin/java");

        if (javaBin.exists() && javaBin.canExecute()) {
            cachedPath = javaBin.getAbsolutePath();
            Log.i(TAG, "Bundled Java found at " + cachedPath);
            return cachedPath;
        }

        Log.w(TAG, "Java not found anywhere");
        return null;
    }

    public static String getShellPrefix() {
        if (cachedPath != null && cachedPath.contains("jre17")) {
            File jreDir = new File(cachedPath).getParentFile().getParentFile();
            String libPath = new File(jreDir, "lib").getAbsolutePath() + ":" + 
                             new File(jreDir, "lib/server").getAbsolutePath() + ":" +
                             new File(jreDir, "lib/jli").getAbsolutePath();
            return "export LD_LIBRARY_PATH=\"" + libPath + ":$LD_LIBRARY_PATH\" && ";
        }
        return "";
    }

    public static boolean available(Context ctx) {
        return find(ctx) != null;
    }

    public static void clearCache() { checked = false; cachedPath = null; }

    public static String version(Context ctx) {
        try {
            String j = find(ctx);
            if (j == null) return "unknown";
            String cmd = getShellPrefix() + "java -version 2>&1";
            Process p = new ProcessBuilder("sh", "-c", cmd).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null ? line : "unknown";
        } catch (Exception e) { return "unknown"; }
    }

    private static boolean runShell(String cmd) {
        try {
            eu.kodanetwork.mchost.util.AppLogger.log("JavaFinder", "Testing shell cmd: " + cmd);
            Process p = new ProcessBuilder("sh", "-c", cmd).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append(" ");
                eu.kodanetwork.mchost.util.AppLogger.log("JavaFinder", "Shell Out: " + line);
            }
            int code = p.waitFor();
            eu.kodanetwork.mchost.util.AppLogger.log("JavaFinder", "Shell exit code: " + code);
            return code == 0;
        } catch (Exception e) {
            eu.kodanetwork.mchost.util.AppLogger.log("JavaFinder", "Shell error: " + e.getMessage());
            return false;
        }
    }
}
