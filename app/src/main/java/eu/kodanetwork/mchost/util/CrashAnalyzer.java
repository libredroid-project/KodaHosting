package eu.kodanetwork.mchost.util;

import eu.kodanetwork.mchost.model.ServerInstance;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Analyzes server logs and crash-reports to determine crash cause.
 * Used by KodaServerService when a server exits.
 */
public class CrashAnalyzer {

    public static class Result {
        public boolean isCrash = false;
        public String  category = "UNKNOWN";    // OOM, EULA, MOD_CRASH, PORT, PERMISSION, JAVA_VERSION, STACK_OVERFLOW, WORLD_CORRUPT, CONFIG_INVALID, OS_KILLED, NATIVE_LIB, MISSING_JAR, UNKNOWN
        public String  reason = null;            // Human-readable cause
        public String  fixDescription = null;    // What the fix does
        public String  fixAction = null;         // Internal key: ACCEPT_EULA, INCREASE_RAM, CHANGE_PORT, FIX_JAVA_VERSION, FIX_PERMISSIONS, REDOWNLOAD_JAR, REDOWNLOAD_JRE
        public String  stackTrace = null;        // Relevant stack trace lines
        public int     exitCode = 0;
    }

    private static final Pattern[] CRASH_PATTERNS = {
        // OOM
        Pattern.compile("(?i)java\\.lang\\.OutOfMemoryError"),
        Pattern.compile("(?i)insufficient memory"),
        Pattern.compile("(?i)GC overhead limit exceeded"),
        Pattern.compile("(?i)unable to create new native thread"),
        // EULA
        Pattern.compile("(?i)You need to agree to the EULA"),
        Pattern.compile("(?i)Go to eula\\.txt"),
        // Port
        Pattern.compile("(?i)Address already in use"),
        Pattern.compile("(?i)EADDRINUSE"),
        Pattern.compile("(?i)Failed to bind to port"),
        Pattern.compile("(?i)Cannot bind to port"),
        // Mod crash
        Pattern.compile("(?i)Mixin apply.*failed"),
        Pattern.compile("(?i)LoaderExceptionModCrash"),
        Pattern.compile("(?i)ModLoadingException"),
        Pattern.compile("(?i)FMLCommonSetupEvent.*error"),
        Pattern.compile("(?i)cpw\\.mods\\.fml.*crash"),
        // Missing JAR / Main class
        Pattern.compile("(?i)Could not find or load main class"),
        Pattern.compile("(?i)FileNotFoundException.*\\.jar"),
        Pattern.compile("(?i)Error: Unable to access jarfile"),
        // Java version
        Pattern.compile("(?i)UnsupportedClassVersionError"),
        Pattern.compile("(?i)has been compiled by a more recent version"),
        Pattern.compile("(?i)class file version \\d+\\.\\d+ .*this Java|(?i)requires Java \\d+.*but you are running Java|(?i)requires version \\d+.*java.*wrong version is present"),
        // Permission
        Pattern.compile("(?i)java\\.nio\\.file\\.AccessDeniedException"),
        Pattern.compile("(?i)Permission denied"),
        // Stack overflow
        Pattern.compile("(?i)java\\.lang\\.StackOverflowError"),
        // World corrupt
        Pattern.compile("(?i)Region file.*has invalid"),
        Pattern.compile("(?i)Chunk.*invalid biome"),
        Pattern.compile("(?i)Exception reading .*\\.mca"),
        // Config invalid
        Pattern.compile("(?i)Invalid server\\.properties"),
        Pattern.compile("(?i)server\\.properties.*NumberFormatException"),
        // Native lib
        Pattern.compile("(?i)UnsatisfiedLinkError"),
        Pattern.compile("(?i)Could not load.*libjvm"),
        Pattern.compile("(?i)dlopen failed:.*libjvm"),
        Pattern.compile("(?i)libjvm\\.so"),
    };

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

        // Combine log + crash report for scanning
        List<String> allLines = new ArrayList<>(logLines);
        if (crashReportContent != null) {
            allLines.addAll(Arrays.asList(crashReportContent.split("\n")));
        }

        // Check each line against patterns
        for (String line : allLines) {
            String stripped = stripAnsi(line);

            // ── EULA ──────────────────────────────────────────────
            if (matches(stripped, 4, 5)) {
                r.isCrash = true;
                r.category = "EULA";
                r.reason = "EULA not accepted";
                r.fixDescription = "Accept the Minecraft EULA automatically";
                r.fixAction = "ACCEPT_EULA";
                return r;
            }
            // ── OOM ───────────────────────────────────────────────
            if (matches(stripped, 0, 3)) {
                r.isCrash = true;
                r.category = "OOM";
                r.reason = "OutOfMemoryError";
                r.fixDescription = "Increase RAM by 512 MB";
                r.fixAction = "INCREASE_RAM";
                r.stackTrace = extractStackTrace(allLines, line);
                return r;
            }
            // ── PORT ──────────────────────────────────────────────
            if (matches(stripped, 6, 9)) {
                r.isCrash = true;
                r.category = "PORT";
                r.reason = "Port already in use";
                r.fixDescription = "Assign a different port";
                r.fixAction = "CHANGE_PORT";
                return r;
            }
            // ── MOD CRASH ─────────────────────────────────────────
            if (matches(stripped, 10, 14)) {
                r.isCrash = true;
                r.category = "MOD_CRASH";
                r.reason = "Mod compatibility error";
                r.stackTrace = extractStackTrace(allLines, line);
                return r;
            }
            // ── MISSING JAR ───────────────────────────────────────
            if (matches(stripped, 15, 17)) {
                r.isCrash = true;
                r.category = "MISSING_JAR";
                r.reason = "Server JAR missing or corrupted";
                r.fixDescription = "Re-download the server JAR";
                r.fixAction = "REDOWNLOAD_JAR";
                return r;
            }
            // ── JAVA VERSION ──────────────────────────────────────
            if (matches(stripped, 18, 20)) {
                r.isCrash = true;
                r.category = "JAVA_VERSION";
                r.reason = "Wrong Java version";
                r.fixDescription = "Auto-select correct Java version";
                r.fixAction = "FIX_JAVA_VERSION";
                r.stackTrace = extractStackTrace(allLines, line);
                return r;
            }
            // ── PERMISSION ────────────────────────────────────────
            if (matches(stripped, 21, 22)) {
                r.isCrash = true;
                r.category = "PERMISSION";
                r.reason = "File permission denied";
                r.fixDescription = "Fix file permissions";
                r.fixAction = "FIX_PERMISSIONS";
                return r;
            }
            // ── STACK OVERFLOW ─────────────────────────────────────
            if (matches(stripped, 23, 23)) {
                r.isCrash = true;
                r.category = "STACK_OVERFLOW";
                r.reason = "StackOverflowError (infinite loop in mod/plugin)";
                r.stackTrace = extractStackTrace(allLines, line);
                return r;
            }
            // ── WORLD CORRUPT ─────────────────────────────────────
            if (matches(stripped, 24, 26)) {
                r.isCrash = true;
                r.category = "WORLD_CORRUPT";
                r.reason = "World data corrupted";
                return r;
            }
            // ── CONFIG INVALID ────────────────────────────────────
            if (matches(stripped, 27, 28)) {
                r.isCrash = true;
                r.category = "CONFIG_INVALID";
                r.reason = "Invalid server.properties";
                return r;
            }
            // ── NATIVE LIB ────────────────────────────────────────
            if (matches(stripped, 29, 32)) {
                r.isCrash = true;
                r.category = "NATIVE_LIB";
                r.reason = "Missing native library (libjvm.so)";
                r.fixDescription = "Re-download the Java Runtime";
                r.fixAction = "REDOWNLOAD_JRE";
                r.stackTrace = extractStackTrace(allLines, line);
                return r;
            }
        }

        // If exit code is non-zero but we didn't match any pattern
        if (exitCode != 0 && exitCode != -1) {
            r.isCrash = true;
            r.category = "UNKNOWN";
            r.reason = "Process exited with code " + exitCode;
            r.stackTrace = lastNLines(logLines, 20);
        }
        // If exit code is 0 or -1 but state was STARTING (never reached 'Done'), also a crash
        if ((exitCode == 0 || exitCode == -1) && srv.state == ServerInstance.State.STARTING) {
            r.isCrash = true;
            r.category = "UNKNOWN";
            r.reason = "Server exited before finishing startup";
            r.stackTrace = lastNLines(logLines, 20);
        }

        return r;
    }

    private static boolean matches(String line, int fromIdx, int toIdx) {
        for (int i = fromIdx; i <= toIdx && i < CRASH_PATTERNS.length; i++) {
            if (CRASH_PATTERNS[i].matcher(line).find()) return true;
        }
        return false;
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
        // Sort by last modified, newest first
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        File newest = files[0];
        // Only consider crash reports from the last 5 minutes
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
