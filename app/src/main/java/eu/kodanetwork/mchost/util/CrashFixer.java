package eu.kodanetwork.mchost.util;

import android.content.Context;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import java.io.*;
import java.util.Properties;
import java.util.Random;

/**
 * Executes concrete fix actions for known crash causes.
 * Each fix modifies server files or settings, then the user manually restarts.
 */
public class CrashFixer {

    public static class FixResult {
        public boolean success;
        public String  message;   // Human-readable result
        public FixResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /** Execute a fix action by key. Returns result with success flag and message. */
    public static FixResult executeFix(Context ctx, ServerInstance srv, String fixAction) {
        if (fixAction == null || srv == null) return new FixResult(false, "Invalid fix action");
        switch (fixAction) {
            case "ACCEPT_EULA":     return fixEula(srv);
            case "INCREASE_RAM":    return fixRam(ctx, srv);
            case "CHANGE_PORT":     return fixPort(ctx, srv);
            case "FIX_JAVA_VERSION":return fixJavaVersion(ctx, srv);
            case "FIX_PERMISSIONS":  return fixPermissions(srv);
            case "REDOWNLOAD_JAR":  return flagRedownloadJar(srv);
            case "REDOWNLOAD_JRE":  return flagRedownloadJre(ctx, srv);
            default:               return new FixResult(false, "Unknown fix: " + fixAction);
        }
    }

    /** Write eula=true to eula.txt */
    private static FixResult fixEula(ServerInstance srv) {
        try {
            File eulaFile = new File(srv.getServerDir(), "eula.txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(eulaFile))) {
                pw.println("#Accepted by KodaHosting CrashFixer");
                pw.println("eula=true");
            }
            return new FixResult(true, "EULA accepted");
        } catch (Exception e) {
            return new FixResult(false, "Failed to write eula.txt: " + e.getMessage());
        }
    }

    /** Increase RAM by 512 MB (max 8192) */
    private static FixResult fixRam(Context ctx, ServerInstance srv) {
        int oldRam = srv.getRamMB();
        int newRam = Math.min(oldRam + 512, 8192);
        if (newRam == oldRam) return new FixResult(false, "RAM already at maximum (8192 MB)");
        srv.setRamMB(newRam);
        ServerRepo.get(ctx).update(srv);
        return new FixResult(true, "RAM increased: " + oldRam + " → " + newRam + " MB");
    }

    /** Assign a random new port and update server.properties */
    private static FixResult fixPort(Context ctx, ServerInstance srv) {
        int newPort = 25566 + new Random().nextInt(34); // 25566-25599
        srv.setPort(newPort);
        ServerRepo.get(ctx).update(srv);
        // Also update server.properties if it exists
        File propsFile = new File(srv.getServerDir(), "server.properties");
        if (propsFile.exists()) {
            try {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(propsFile)) {
                    props.load(fis);
                }
                props.setProperty("server-port", String.valueOf(newPort));
                try (FileOutputStream fos = new FileOutputStream(propsFile)) {
                    props.store(fos, "Updated by KodaHosting CrashFixer");
                }
            } catch (Exception ignored) {}
        }
        return new FixResult(true, "Port changed to " + newPort);
    }

    /** Reset java runtime to Auto (0) and force-stop any running instance */
    private static FixResult fixJavaVersion(Context ctx, ServerInstance srv) {
        // Force-stop the server via Intent so there's no stale JVM connection
        if (srv.state != ServerInstance.State.OFFLINE) {
            try {
                android.content.Intent killIntent = new android.content.Intent(ctx, eu.kodanetwork.mchost.service.KodaServerService.class);
                killIntent.setAction("KILL");
                killIntent.putExtra("id", srv.getId());
                ctx.startService(killIntent);
                // Wait for server to reach OFFLINE state (max 5 seconds)
                for (int i = 0; i < 10; i++) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    if (srv.state == ServerInstance.State.OFFLINE) break;
                }
            } catch (Exception ignored) {}
        }
        
        srv.setJavaRuntime(0);
        int autoVer = RuntimeManager.resolveAutoVersion(srv);
        ServerRepo.get(ctx).update(srv);
        return new FixResult(true, "Java set to Auto (Java " + autoVer + ")");
    }

    /** Fix file permissions on the server directory */
    private static FixResult fixPermissions(ServerInstance srv) {
        File dir = new File(srv.getServerDir());
        if (!dir.exists()) return new FixResult(false, "Server directory not found");
        int fixed = fixPermsRecursive(dir, 0);
        return new FixResult(true, "Permissions fixed on " + fixed + " files");
    }

    private static int fixPermsRecursive(File f, int count) {
        if (f.isDirectory()) {
            f.setReadable(true, false);
            f.setWritable(true, false);
            f.setExecutable(true, false);
            count++;
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) count = fixPermsRecursive(c, count);
            }
        } else {
            f.setReadable(true, false);
            f.setWritable(true, false);
            count++;
        }
        return count;
    }

    /** Flag that the JAR needs to be re-downloaded (delete current JAR). */
    private static FixResult flagRedownloadJar(ServerInstance srv) {
        File dir = new File(srv.getServerDir());
        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        int deleted = 0;
        if (jars != null) {
            for (File j : jars) {
                if (j.delete()) deleted++;
            }
        }
        // Also check versions/ subfolder
        File versionsDir = new File(dir, "versions");
        if (versionsDir.isDirectory()) {
            File[] vJars = versionsDir.listFiles((d, name) -> name.endsWith(".jar"));
            if (vJars != null) {
                for (File j : vJars) {
                    if (j.delete()) deleted++;
                }
            }
        }
        return deleted > 0
            ? new FixResult(true, "Deleted " + deleted + " JAR(s). Server will re-download on next start.")
            : new FixResult(false, "No JAR files found to delete.");
    }

    /** Flag that the JRE needs re-download by deleting the JRE directory. */
    private static FixResult flagRedownloadJre(Context ctx, ServerInstance srv) {
        int ver = srv.getJavaRuntime() == 0 ? RuntimeManager.resolveAutoVersion(srv) : srv.getJavaRuntime();
        File jreDir = new File(ctx.getFilesDir(), "jre" + ver);
        if (jreDir.exists()) {
            deleteRecursive(jreDir);
            return new FixResult(true, "JRE " + ver + " deleted. Will re-download on next start.");
        }
        return new FixResult(false, "JRE directory not found.");
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
