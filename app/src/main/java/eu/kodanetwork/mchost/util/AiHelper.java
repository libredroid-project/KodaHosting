package eu.kodanetwork.mchost.util;

import android.content.Context;
import eu.kodanetwork.mchost.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * "Ask AI" crash assistant. Sends the last 1000 log lines + device specs +
 * server config to OpenRouter (free tier) and returns a compact analysis:
 * cause, manual fix (ALWAYS), confidence, and — only when the model is
 * certain and the fix is an app setting — an auto_fix instruction
 * (whitelist: set_ram, set_java; the port is app-managed and never exposed).
 *
 * Guards: user consent (prefs), max 10 requests per day (rate limit).
 */
public class AiHelper {

    public static final String MODEL = "google/gemma-4-31b-it:free";
    // Free-tier pools are capacity-throttled UPSTREAM (429 "upstream_provider_shared_pool",
    // verified live: both Google Gemma free variants + several others saturate regularly).
    // Chain: preferred model first, then live-tested alternatives from DIFFERENT provider pools.
    private static okhttp3.OkHttpClient httpClient;

    // Free pools saturate independently — chain ordered by strength, ALL entries
    // live-tested 2026-09-06 (Gemma 431b/26b currently 429-throttled upstream).
    private static final String[] MODELS = {
            "nvidia/nemotron-3-super-120b-a12b:free",   // 120B, strongest working
            "minimax/minimax-m3:free",                   // strong, 1M ctx
            "nvidia/nemotron-3.5-lightning:free",        // fast
            "dots-studio/dots-3-note-preview:free",
            "inclusionai/ling-3.0-flash-sante:free",
            "google/gemma-4-31b-it:free",                // user's pick — kept for when the pool recovers
    };
    private static final int MAX_LOG_CHARS = 60000;
    private static final int DAILY_LIMIT = 10;

    public interface Callback {
        void onResult(AiResult result);
        void onError(String message);
    }

    public static class AiResult {
        public String cause;        // why it crashed (short)
        public String fix;          // manual fix steps (always present)
        public String confidence;   // NOT_CONFIDENT | CONFIDENT | HIGH_CONFIDENCE | CERTAIN
        public String autoFixAction;  // set_ram | set_java | null
        public long autoFixValue;     // MB for set_ram, java version for set_java
        public String raw;            // full model text fallback
    }

    public static class ConsentException extends Exception {}

    public static class RateLimitException extends Exception {
        public final int used;
        public RateLimitException(int used) { this.used = used; }
    }

    // ── guards ──────────────────────────────────────────────────────

    public static boolean hasConsent(Context ctx) {
        return App.getPrefs(ctx).getBoolean("ai_consent", false);
    }

    public static void setConsent(Context ctx, boolean granted) {
        App.getPrefs(ctx).edit().putBoolean("ai_consent", granted).apply();
    }

    private static int todayCount(Context ctx) {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        android.content.SharedPreferences p = App.getPrefs(ctx);
        if (!today.equals(p.getString("ai_count_day", ""))) return 0;
        return p.getInt("ai_count", 0);
    }

    private static void bumpCount(Context ctx) {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        App.getPrefs(ctx).edit()
                .putString("ai_count_day", today)
                .putInt("ai_count", todayCount(ctx) + 1)
                .apply();
    }

    // ── input gathering ─────────────────────────────────────────────

    /**
     * Consolidated log for the AI: console buffer (contains app lines like
     * "Using JDK 8 for ARM64" AND the crash, even when latest.log has not been
     * flushed yet) FIRST, then server.log and logs/latest.log tails as history.
     */
    public static String gatherLog(Context ctx, eu.kodanetwork.mchost.model.ServerInstance srv,
                                   List<String> serviceBuffer) {
        StringBuilder sb = new StringBuilder();
        if (serviceBuffer != null && !serviceBuffer.isEmpty()) {
            sb.append("=== CONSOLE (app + server, most recent) ===\n");
            for (String l : serviceBuffer) sb.append(stripAnsi(l)).append('\n');
        }
        if (srv != null && srv.getServerDir() != null) {
            File crashDump = new File(srv.getServerDir(), "logs/koda_console.log");
            if (crashDump.exists()) {
                // skip the dump when the live console buffer already covers this crash
                boolean bufferCoversCrash = serviceBuffer != null && !serviceBuffer.isEmpty()
                        && System.currentTimeMillis() - crashDump.lastModified() < 10 * 60 * 1000L;
                if (!bufferCoversCrash) appendFileTail(crashDump, 400, sb);
            }
            appendFileTail(new File(srv.getServerDir(), "logs/latest.log"), 400, sb);
            appendFileTail(new File(srv.getServerDir(), "server.log"), 400, sb);
        }
        String out = sb.toString();
        if (out.length() > MAX_LOG_CHARS) out = out.substring(out.length() - MAX_LOG_CHARS);
        return out;
    }

    private static void appendFileTail(File log, int n, StringBuilder sb) {
        if (!log.exists() || log.length() == 0) return;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(log))) {
            LinkedList<String> tail = new LinkedList<>();
            String line;
            while ((line = br.readLine()) != null) {
                tail.add(line);
                if (tail.size() > n) tail.removeFirst();
            }
            String age = "";
            long ageMs = System.currentTimeMillis() - log.lastModified();
            if (ageMs > 30 * 60 * 1000L) {
                age = " (last written " + (ageMs / 60000) + " min ago — may be from a PREVIOUS run)";
            } else if (ageMs > 60 * 1000L) {
                age = " (last written " + (ageMs / 60000) + " min ago)";
            }
            sb.append("=== ").append(log.getName()).append(age).append(" ===\n");
            for (String l : tail) sb.append(stripAnsi(l)).append('\n');
        } catch (Exception ignored) {}
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("(?:\\x1B|\\u001B)\\[[;\\d]*[ -/]*[@-~]", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    /** Device + server facts for the prompt (mirrors the pingAppStatus payload). */
    public static String collectSpecs(Context ctx, eu.kodanetwork.mchost.model.ServerInstance srv) {
        StringBuilder sb = new StringBuilder();
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ')
              .append(android.os.Build.MODEL)
              .append(", Android ").append(android.os.Build.VERSION.RELEASE)
              .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            sb.append("RAM: device total ").append(mi.totalMem / 1048576)
              .append(" MB, free ").append(mi.availMem / 1048576)
              .append(" MB, app heap max ").append(Runtime.getRuntime().maxMemory() / 1048576).append(" MB\n");
            sb.append("CPU cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
            if (srv != null) {
                sb.append("Server: type ").append(srv.getType().name())
                  .append(", MC version ").append(srv.getVersion())
                  .append(", allocated RAM ").append(srv.getRamMB()).append(" MB")
                  .append(", Java runtime setting ").append(srv.getJavaRuntime() == 0 ? "auto" : srv.getJavaRuntime())
                  .append(", port ").append(srv.getPort()).append("\n");
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }

    // ── the call ────────────────────────────────────────────────────

    /** Blocking — call off the main thread. Throws Consent/RateLimit guards first. */
    public static AiResult askAiSync(Context ctx, eu.kodanetwork.mchost.model.ServerInstance srv,
                                     String logTail, String analyzerCategory) throws Exception {
        if ((analyzerCategory == null || analyzerCategory.isEmpty()) && srv != null) {
            analyzerCategory = srv.crashCategory; // fall back to the in-memory field
        }
        if (!hasConsent(ctx)) throw new ConsentException();
        int used = todayCount(ctx);
        if (used >= DAILY_LIMIT) throw new RateLimitException(used);

        String lang = ctx.getResources().getConfiguration().getLocales().get(0).getLanguage();
        String replyLang = lang.startsWith("de") ? "German" : (lang.startsWith("zh") ? "Simplified Chinese" : "English");

        String system = "You are the crash-diagnosis unit of the KodaHosting Android app (P.R.A.E.T.O.R. system). "
                + "The user's Minecraft server crashed. Reply AT MOST 100 words, in " + replyLang + ". "
                + "STRICT RULES: "
                + "(1) Base EVERY claim ONLY on what is actually visible in the log excerpt - never invent or assume "
                + "problems the log does not show (e.g. do NOT mention RAM, memory or storage unless the log "
                + "explicitly contains an OutOfMemoryError or memory error). "
                + "(2) In cause, QUOTE the exact log line that proves the cause, prefixed LOG:, then one sentence explaining it. "
                + "(3) In fix, give concrete steps the user can do inside this app. "
                + "(4) Respond ONLY with a JSON object (no markdown fences) with keys: "
                + "\"cause\" (LOG-quoted line + short explanation), "
                + "\"fix\" (concrete manual steps), "
                + "\"confidence\" (one of NOT_CONFIDENT, CONFIDENT, HIGH_CONFIDENCE, CERTAIN), "
                + "\"auto_fix\" (ONLY if CERTAIN the crash is caused by that app setting; null otherwise; "
                + "allowed actions ONLY: {\"action\":\"set_ram\",\"value_mb\":<1024-8192>} or "
                + "{\"action\":\"set_java\",\"value\":<8|17|21|25>}. "
                + "NEVER suggest port changes - the port is managed by the app. "
                + "If the cause is a broken mod/plugin/world file, auto_fix must be null and fix must explain which file to remove and where.";

        String analyzerContext = "";
        int analyzerJdk = 0;
        if (analyzerCategory != null && !"UNKNOWN".equals(analyzerCategory)) {
            analyzerContext = "\nBUILT-IN ANALYZER SUSPICION (verify against the log; correct it if wrong): "
                    + srv.crashCategory + " - " + (srv.crashReason != null ? srv.crashReason : "") + "\n";
        }
        // The app's runtime database knows the CORRECT JDK — the model must not guess
        // (e.g. MC 26.x needs JDK 25, not the 17/21 that generic models tend to suggest).
        if ("JAVA_VERSION".equals(analyzerCategory)) {
            try {
                analyzerJdk = eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(srv);
                if (analyzerJdk >= 8 && analyzerJdk <= 25) {
                    analyzerContext += "The app's runtime database: the CORRECT JDK for this server ("
                            + srv.getType().name() + " " + srv.getVersion() + ") is Java " + analyzerJdk
                            + ". If the log proves a wrong-Java crash, auto_fix MUST be {\"action\":\"set_java\",\"value\":" + analyzerJdk + "}.\n";
                } else analyzerJdk = 0;
            } catch (Exception ignored) { analyzerJdk = 0; }
        }

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        msgs.put(new JSONObject().put("role", "user").put("content",
                "DEVICE + SERVER INFO:\n" + collectSpecs(ctx, srv) + analyzerContext
                        + "\nLAST LOG LINES:\n" + logTail));
        body.put("messages", msgs);

        String key = eu.kodanetwork.mchost.security.PraetorSecurity.getOpenRouterKey();
        Exception lastErr = null;
        long deadline = System.currentTimeMillis() + 90_000L; // hard overall budget
        for (String model : MODELS) {
            if (System.currentTimeMillis() > deadline) break;
            try {
                if (httpClient == null) {
                    httpClient = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            // HARD cap per model: a dripping throttled upstream cannot
                            // stretch the call (readTimeout only bounds packet gaps)
                            .callTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                            .build();
                }
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .post(okhttp3.RequestBody.create(body.toString(),
                                okhttp3.MediaType.parse("application/json; charset=utf-8")))
                        .header("Authorization", "Bearer " + key)
                        .build();
                try (okhttp3.Response r = httpClient.newCall(req).execute()) {
                    int code = r.code();
                    String resp = r.body() != null ? r.body().string() : "";
                    if (code >= 200 && code < 300) {
                        JSONArray choices = new JSONObject(resp).optJSONArray("choices");
                        String content = choices != null && choices.length() > 0
                                ? choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                                : "";
                        bumpCount(ctx);
                        AiResult res = parse(content);
                        if ("JAVA_VERSION".equals(analyzerCategory) && analyzerJdk > 0) {
                            res.autoFixAction = "set_java";
                            res.autoFixValue = analyzerJdk;
                            // the model tends to guess 17/21 — correct the prose too
                            res.cause = res.cause == null ? null :
                                    res.cause.replaceAll("(?i)(JDK|Java)\\s*(8|11|16|17|18|19|20|21|22|23|24)",
                                            "$1 " + analyzerJdk);
                            res.fix = res.fix == null ? null :
                                    res.fix.replaceAll("(?i)(JDK|Java)\\s*(8|11|16|17|18|19|20|21|22|23|24)",
                                            "$1 " + analyzerJdk);
                        }
                        return res;
                    }
                    // surface the real reason (e.g. "No allowed providers are available")
                    String detail = extractErrorDetail(resp);
                    if (code == 401 || code == 403) throw new Exception("AI auth failed (" + code + ")");
                    lastErr = new Exception(detail + " (model " + model + ", " + code + ")");
                    if (code == 401 || code == 403) break; // do not burn the chain on auth errors
                }
            } catch (Exception e) {
                lastErr = e;
            }
        }
        throw lastErr != null ? lastErr : new Exception("AI request failed");
    }

    private static String extractErrorDetail(String resp) {
        try {
            JSONObject o = new JSONObject(resp);
            JSONObject err = o.optJSONObject("error");
            if (err != null && err.optString("message") != null) return err.optString("message");
        } catch (Exception ignored) {}
        return resp != null && resp.length() > 200 ? resp.substring(0, 200) : (resp == null ? "unknown" : resp);
    }

    /** Tolerant parsing: JSON block first, plain-text fallback with keyword confidence. */
    static AiResult parse(String content) {
        AiResult r = new AiResult();
        r.raw = content == null ? "" : content;
        String text = r.raw.trim();
        // strip markdown fences
        text = text.replaceAll("(?s)^```(json)?", "").replaceAll("(?s)```$", "").trim();

        // find first { ... last }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONObject o = new JSONObject(text.substring(start, end + 1));
                r.cause = o.optString("cause", null);
                r.fix = o.optString("fix", null);
                r.confidence = o.optString("confidence", "NOT_CONFIDENT").toUpperCase().replace(' ', '_');
                JSONObject af = o.optJSONObject("auto_fix");
                if (af != null) {
                    String action = af.optString("action", "");
                    // hard whitelist — never honor anything else
                    if ("set_ram".equals(action)) {
                        long v = af.optLong("value_mb", 0);
                        if (v >= 1024 && v <= 8192) { r.autoFixAction = "set_ram"; r.autoFixValue = v; }
                    } else if ("set_java".equals(action)) {
                        long v = af.optLong("value", 0);
                        if (v == 8 || v == 17 || v == 21 || v == 25) { r.autoFixAction = "set_java"; r.autoFixValue = v; }
                    }
                    // CERTAIN without a valid whitelisted action still shows no button
                }
            } catch (Exception ignored) {}
        }
        if (r.cause == null || r.fix == null) {
            // plain text fallback: whole text is the answer
            r.cause = text.isEmpty() ? "(empty AI response)" : text;
            r.fix = r.fix != null ? r.fix : "";
            String upper = r.raw.toUpperCase();
            if (upper.contains("CERTAIN")) r.confidence = "CERTAIN";
            else if (upper.contains("HIGH")) r.confidence = "HIGH_CONFIDENCE";
            else if (upper.contains("CONFIDENT")) r.confidence = "CONFIDENT";
            else r.confidence = "NOT_CONFIDENT";
        }
        if ("CERTAIN".equals(r.confidence) && r.autoFixAction == null) {
            // certain but nothing app-configurable: keep label, no button
        }
        return r;
    }
}
