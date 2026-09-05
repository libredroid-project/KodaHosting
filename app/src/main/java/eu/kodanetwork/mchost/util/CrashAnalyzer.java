package eu.kodanetwork.mchost.util;

import eu.kodanetwork.mchost.model.ServerInstance;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Analyzes server logs and crash-reports to determine the crash cause.
 * Used by KodaServerService when a server exits.
 *
 * Patterns live in an ORDERED list: first match across all lines wins, so
 * specific causes (EULA, libjvm) are checked before generic ones (Permission
 * denied). Categories are stable keys — the localized display strings live in
 * strings.xml (crash_category_* / crash_reason_*), consumed by
 * CrashAlertActivity and the crash banner.
 */
public class CrashAnalyzer {

    public static class Result {
        public boolean isCrash = false;
        public String  category = "UNKNOWN";
        public String  reason = null;            // English detail (fallback / debug)
        public String  fixDescription = null;    // What the automatic fix does (null = manual)
        public String  fixAction = null;         // CrashFixer key, null = manual fix only
        public String  stackTrace = null;
        public int     exitCode = 0;
    }

    private static class CrashPattern {
        final Pattern pattern;
        final String category;
        final String reason;
        final String fixDescription; // nullable
        final String fixAction;      // nullable
        CrashPattern(String regex, String category, String reason, String fixDescription, String fixAction) {
            this.pattern = Pattern.compile(regex);
            this.category = category;
            this.reason = reason;
            this.fixDescription = fixDescription;
            this.fixAction = fixAction;
        }
    }

    private static final List<CrashPattern> PATTERNS = buildPatterns();

    private static List<CrashPattern> buildPatterns() {
        List<CrashPattern> p = new ArrayList<>();

        // ── EULA (very specific, first) ─────────────────────────────
        p.add(new CrashPattern("(?i)You need to agree to the EULA", "EULA",
                "EULA not accepted", "Accept the Minecraft EULA automatically", "ACCEPT_EULA"));
        p.add(new CrashPattern("(?i)Go to eula\\.txt", "EULA",
                "EULA not accepted", "Accept the Minecraft EULA automatically", "ACCEPT_EULA"));

        // ── Native library (JRE broken) ─────────────────────────────
        p.add(new CrashPattern("(?i)UnsatisfiedLinkError", "NATIVE_LIB",
                "Missing or incompatible native library (libjvm.so)",
                "Re-download the Java Runtime", "REDOWNLOAD_JRE"));
        p.add(new CrashPattern("(?i)Could not load.*libjvm", "NATIVE_LIB",
                "Missing or incompatible native library (libjvm.so)",
                "Re-download the Java Runtime", "REDOWNLOAD_JRE"));
        p.add(new CrashPattern("(?i)dlopen failed:.*libjvm", "NATIVE_LIB",
                "Missing or incompatible native library (libjvm.so)",
                "Re-download the Java Runtime", "REDOWNLOAD_JRE"));
        p.add(new CrashPattern("(?i)libjvm\\.so", "NATIVE_LIB",
                "Missing or incompatible native library (libjvm.so)",
                "Re-download the Java Runtime", "REDOWNLOAD_JRE"));

        // ── Java version mismatches ─────────────────────────────────
        p.add(new CrashPattern("(?i)UnsupportedClassVersionError", "JAVA_VERSION",
                "Jar compiled for a newer Java than the runtime",
                "Auto-select correct Java version", "FIX_JAVA_VERSION"));
        p.add(new CrashPattern("(?i)has been compiled by a more recent version", "JAVA_VERSION",
                "Jar compiled for a newer Java than the runtime",
                "Auto-select correct Java version", "FIX_JAVA_VERSION"));
        p.add(new CrashPattern("(?i)unsupported major\\.minor version", "JAVA_VERSION",
                "Jar compiled for a newer Java than the runtime",
                "Auto-select correct Java version", "FIX_JAVA_VERSION"));
        p.add(new CrashPattern("(?i)requires Java \\d+.*(but|running)|(?i)class file version \\d+\\.\\d+.*this version", "JAVA_VERSION",
                "Server or mod requires a different Java version",
                "Auto-select correct Java version", "FIX_JAVA_VERSION"));

        // ── Out of memory ───────────────────────────────────────────
        p.add(new CrashPattern("(?i)java\\.lang\\.OutOfMemoryError: Metaspace", "OOM",
                "OutOfMemoryError: Metaspace (too many classes, usually heavy mods)", null, null));
        p.add(new CrashPattern("(?i)java\\.lang\\.OutOfMemoryError", "OOM",
                "OutOfMemoryError — heap too small for this server/mods",
                "Increase RAM by 512 MB", "INCREASE_RAM"));
        p.add(new CrashPattern("(?i)insufficient memory", "OOM",
                "Insufficient memory", "Increase RAM by 512 MB", "INCREASE_RAM"));
        p.add(new CrashPattern("(?i)GC overhead limit exceeded", "OOM",
                "GC overhead limit exceeded — heap too small, server thrashing in garbage collection",
                "Increase RAM by 512 MB", "INCREASE_RAM"));
        p.add(new CrashPattern("(?i)unable to create new native thread", "OOM",
                "Unable to create native threads — process/system thread or memory limit reached", null, null));

        // ── Class not found (missing mod/plugin library) ────────────
        p.add(new CrashPattern("(?i)java\\.lang\\.ClassNotFoundException", "CLASS_NOT_FOUND",
                "ClassNotFoundException — a mod or plugin references a missing library", null, null));
        p.add(new CrashPattern("(?i)java\\.lang\\.NoClassDefFoundError", "CLASS_NOT_FOUND",
                "NoClassDefFoundError — a mod or plugin is missing a dependency library", null, null));

        // ── Mod loader crashes ──────────────────────────────────────
        p.add(new CrashPattern("(?i)Missing or unsupported mandatory dependencies", "MOD_CRASH",
                "Mod dependencies missing or wrong version — install/update the listed mods", null, null));
        p.add(new CrashPattern("(?i)Missing Mods?:|(?i)missing mods.*\\[", "MOD_CRASH",
                "Required mods missing — install the mods listed in the log", null, null));
        p.add(new CrashPattern("(?i)Duplicate mods found", "MOD_CRASH",
                "Duplicate mods — the same mod exists twice in the mods folder; remove one copy", null, null));
        p.add(new CrashPattern("(?i)mods\\.toml", "MOD_CRASH",
                "Forge/NeoForge mod file is invalid (missing or broken mods.toml) — re-download the mod", null, null));
        p.add(new CrashPattern("(?i)MixinApplyError|(?i)Mixin apply.*failed", "MOD_CRASH",
                "Mixin failed to apply — mod incompatible with this server version; remove/update it", null, null));
        p.add(new CrashPattern("(?i)LoaderExceptionModCrash|(?i)ModLoadingException", "MOD_CRASH",
                "Mod crashed during load — remove/update the mod named in the stack trace", null, null));
        p.add(new CrashPattern("(?i)FMLCommonSetupEvent.*error|(?i)cpw\\.mods\\.fml.*crash", "MOD_CRASH",
                "Forge mod loading crashed — remove/update the mod named in the stack trace", null, null));
        p.add(new CrashPattern("(?i)Incompatible mod set", "MOD_CRASH",
                "Fabric reports an incompatible mod set — check mod versions against the loader", null, null));
        p.add(new CrashPattern("(?i)not a valid (mod|jar) file", "MOD_CRASH",
                "A file in the mods folder is not a valid mod — remove it", null, null));

        // ── Plugin configuration (Bukkit/Spigot/Paper) ──────────────
        p.add(new CrashPattern("(?i)Error occurred while enabling", "PLUGIN_CONFIG",
                "Plugin crashed in onEnable — remove/update the plugin named above", null, null));
        p.add(new CrashPattern("(?i)Invalid plugin\\.yml|(?i)plugin\\.yml", "PLUGIN_CONFIG",
                "A plugin has an invalid plugin.yml — re-download or remove that plugin", null, null));
        p.add(new CrashPattern("(?i)duplicate plugin", "PLUGIN_CONFIG",
                "Duplicate plugin — the same plugin is loaded twice; remove one copy", null, null));
        p.add(new CrashPattern("(?i)YAMLException|(?i)org\\.yaml\\.snakeyaml", "PLUGIN_CONFIG",
                "Broken YAML config (config.yml/plugin.yml) — fix the syntax error reported in the log", null, null));

        // ── World corruption ────────────────────────────────────────
        p.add(new CrashPattern("(?i)Ticking entity", "WORLD_CORRUPT",
                "Crash while ticking an entity — a corrupted entity breaks the world; restore a backup or remove the entity", null, null));
        p.add(new CrashPattern("(?i)Ticking block entity", "WORLD_CORRUPT",
                "Crash while ticking a block entity (e.g. a broken tile entity); restore a backup", null, null));
        p.add(new CrashPattern("(?i)Exception loading.*NBT|(?i)Loading NBT data", "WORLD_CORRUPT",
                "World data corrupted (NBT) — restore the world from a backup", null, null));
        p.add(new CrashPattern("(?i)Region file.*has invalid|(?i)Chunk.*invalid biome|(?i)Exception reading .*\\.mca", "WORLD_CORRUPT",
                "Region/chunk file corrupted — restore the world from a backup or delete the reported region file", null, null));
        p.add(new CrashPattern("(?i)Failed to write chunk|(?i)corrupt.*chunk|(?i)chunk.*corrupt", "WORLD_CORRUPT",
                "Chunk read/write failed — check storage space and world backup", null, null));

        // ── Storage ─────────────────────────────────────────────────
        p.add(new CrashPattern("(?i)No space left on device", "STORAGE",
                "Device storage full — free up space to let the server write world data", null, null));
        p.add(new CrashPattern("(?i)Read-only file system", "STORAGE",
                "Filesystem is read-only — the app's storage is unavailable; remount/restart the device", null, null));
        p.add(new CrashPattern("(?i)Failed to create (directories|directory)", "STORAGE",
                "Server directory could not be created — check storage permissions and free space", null, null));

        // ── Missing / broken server JAR ─────────────────────────────
        p.add(new CrashPattern("(?i)Invalid or corrupt jarfile|(?i)zip END header not found|(?i)error in opening zip file", "MISSING_JAR",
                "Server JAR is corrupted — re-download it",
                "Re-download the server JAR", "REDOWNLOAD_JAR"));
        p.add(new CrashPattern("(?i)no main manifest attributes", "MISSING_JAR",
                "The selected JAR is not a server jar (no Main-Class manifest) — download the correct server jar",
                "Re-download the server JAR", "REDOWNLOAD_JAR"));
        p.add(new CrashPattern("(?i)Could not find or load main class", "MISSING_JAR",
                "Main class missing — JAR broken or wrong file",
                "Re-download the server JAR", "REDOWNLOAD_JAR"));
        p.add(new CrashPattern("(?i)FileNotFoundException.*\\.jar|(?i)Error: Unable to access jarfile", "MISSING_JAR",
                "Server JAR missing — download it in the Settings tab",
                "Re-download the server JAR", "REDOWNLOAD_JAR"));

        // ── Port conflicts ──────────────────────────────────────────
        p.add(new CrashPattern("(?i)Address already in use|(?i)EADDRINUSE", "PORT",
                "Port already in use by another process or server", null, null));
        p.add(new CrashPattern("(?i)Failed to bind to port|(?i)Cannot bind to port|(?i)Bind failed", "PORT",
                "Could not bind the server port", null, null));

        // ── Config errors ───────────────────────────────────────────
        p.add(new CrashPattern("(?i)Invalid server\\.properties|(?i)server\\.properties.*NumberFormatException|(?i)NumberFormatException.*server\\.properties", "CONFIG_INVALID",
                "server.properties contains an invalid value — fix or reset the reported key", null, null));
        p.add(new CrashPattern("(?i)Failed to parse (config|configuration)", "CONFIG_INVALID",
                "A config file could not be parsed — fix the syntax error reported in the log", null, null));

        // ── Stack overflow ──────────────────────────────────────────
        p.add(new CrashPattern("(?i)java\\.lang\\.StackOverflowError", "STACK_OVERFLOW",
                "StackOverflowError — infinite recursion, usually a broken mod or plugin", null, null));

        // ── OS / system kills ───────────────────────────────────────
        p.add(new CrashPattern("(?i)phantom process", "OS_KILLED",
                "Android's phantom-process limiter killed the server process — apply the RAM-limiter ADB bypass", null, null));
        p.add(new CrashPattern("(?i)\\bSIGKILL\\b|(?i)process.*killed.*signal", "OS_KILLED",
                "Process killed by the OS (SIGKILL) — Android terminated the server", null, null));
        p.add(new CrashPattern("(?i)^Killed$|(?i)\\bKilled\\b", "OS_KILLED",
                "Process killed by the OS — usually the memory limiter; apply the RAM-limiter ADB bypass", null, null));

        // ── Permission (generic — intentionally last) ───────────────
        p.add(new CrashPattern("(?i)java\\.nio\\.file\\.AccessDeniedException", "PERMISSION",
                "File access denied — server files lack read/write permission",
                "Fix file permissions", "FIX_PERMISSIONS"));
        p.add(new CrashPattern("(?i)Permission denied", "PERMISSION",
                "Permission denied — a file or port is not accessible",
                "Fix file permissions", "FIX_PERMISSIONS"));

        return p;
    }

    /** Analyze a server's logs and crash-reports after it exited. */
    public static Result analyze(ServerInstance srv) {
        return analyze(srv, 0);
    }

    public static Result analyze(ServerInstance srv, int exitCode) {
        Result r = new Result();
        r.exitCode = exitCode;
        if (srv == null || srv.getServerDir() == null) return r;

        File serverDir = new File(srv.getServerDir());
        List<String> logLines = readLastLines(serverDir, 200);
        String crashReportContent = readLatestCrashReport(serverDir);

        List<String> allLines = new ArrayList<>(logLines);
        if (crashReportContent != null) {
            allLines.addAll(Arrays.asList(crashReportContent.split("\n")));
        }

        // Priority order: pattern list order wins over line order
        for (CrashPattern cp : PATTERNS) {
            for (String line : allLines) {
                String stripped = stripAnsi(line);
                if (cp.pattern.matcher(stripped).find()) {
                    r.isCrash = true;
                    r.category = cp.category;
                    r.reason = cp.reason;
                    r.fixDescription = cp.fixDescription;
                    r.fixAction = cp.fixAction;
                    r.stackTrace = extractStackTrace(allLines, line);
                    return r;
                }
            }
        }

        // Exit-code heuristics (no pattern matched)
        if (exitCode == 137) {
            r.isCrash = true;
            r.category = "OOM";
            r.reason = "Killed by SIGKILL (exit 137) — the OS terminated the server, usually out of memory";
            r.stackTrace = lastNLines(logLines, 20);
            return r;
        }
        if (exitCode == 139) {
            r.isCrash = true;
            r.category = "NATIVE_LIB";
            r.reason = "Native crash (SIGSEGV, exit 139) — the Java runtime or a native library crashed";
            r.fixDescription = "Re-download the Java Runtime";
            r.fixAction = "REDOWNLOAD_JRE";
            r.stackTrace = lastNLines(logLines, 20);
            return r;
        }
        if (exitCode != 0 && exitCode != -1) {
            r.isCrash = true;
            r.category = "UNKNOWN";
            r.reason = "Process exited with code " + exitCode;
            r.stackTrace = lastNLines(logLines, 20);
        }
        if ((exitCode == 0 || exitCode == -1) && srv.state == ServerInstance.State.STARTING) {
            r.isCrash = true;
            r.category = "UNKNOWN";
            r.reason = "Server exited before finishing startup";
            r.stackTrace = lastNLines(logLines, 20);
        }

        return r;
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("(?:\\x1B|\\u001B)\\[[;\\d]*[a-zA-Z]", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    private static String extractStackTrace(List<String> lines, String matchLine) {
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        int count = 0;
        for (String l : lines) {
            if (!found && l.contains(matchLine.length() > 60 ? matchLine.substring(0, 60) : matchLine)) {
                found = true;
            }
            if (found) {
                sb.append(stripAnsi(l)).append("\n");
                if (++count >= 15) break;
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    private static String lastNLines(List<String> lines, int n) {
        if (lines.isEmpty()) return null;
        int start = Math.max(0, lines.size() - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            sb.append(stripAnsi(lines.get(i))).append("\n");
        }
        return sb.toString().trim();
    }

    /** Read last N lines from server.log or logs/latest.log. */
    private static List<String> readLastLines(File serverDir, int n) {
        File[] candidates = {
            new File(serverDir, "server.log"),
            new File(serverDir, "logs/latest.log"),
        };
        for (File f : candidates) {
            if (f.exists() && f.length() > 0) {
                return readTailLines(f, n);
            }
        }
        return new ArrayList<>();
    }

    private static List<String> readTailLines(File f, int n) {
        LinkedList<String> lines = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                if (lines.size() > n) lines.removeFirst();
            }
        } catch (Exception ignored) {}
        return lines;
    }

    /** Read the newest crash-report from crash-reports/ if any. */
    private static String readLatestCrashReport(File serverDir) {
        File crashDir = new File(serverDir, "crash-reports");
        if (!crashDir.isDirectory()) return null;
        File[] files = crashDir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        File newest = files[0];
        if (System.currentTimeMillis() - newest.lastModified() > 5 * 60 * 1000) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(newest))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null && count < 100) {
                sb.append(line).append("\n");
                count++;
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }
}
