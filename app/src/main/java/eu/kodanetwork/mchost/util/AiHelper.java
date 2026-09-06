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
    private static final String[] MODELS = {
            MODEL,
            "nvidia/nemotron-3.5-lightning:free",
            "inclusionai/ling-3.0-flash-sante:free",
            "liquid/lfm-2.5-2.6b:free",
            "google/gemma-4-26b-a4b-it:free",
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
            appendFileTail(new File(srv.getServerDir(), "logs/latest.log"), 600, sb);
            appendFileTail(new File(srv.getServerDir(), "server.log"), 600, sb);
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
            sb.append("=== ").append(log.getName()).append(" ===\n");
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
                                     String logTail) throws Exception {
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
        if (srv != null && srv.crashCategory != null && !"UNKNOWN".equals(srv.crashCategory)) {
            analyzerContext = "\nBUILT-IN ANALYZER SUSPICION (verify against the log; correct it if wrong): "
                    + srv.crashCategory + " - " + (srv.crashReason != null ? srv.crashReason : "") + "\n";
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
        for (String model : MODELS) {
            for (int attempt = 0; attempt < 1; attempt++) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("https://openrouter.ai/api/v1/chat/completions").openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(90000);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    body.put("model", model);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = c.getResponseCode();
                    InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    String resp = "";
                    if (is != null) {
                        Scanner sc = new Scanner(is).useDelimiter("\\A");
                        resp = sc.hasNext() ? sc.next() : "";
                    }
                    c.disconnect();
                    if (code >= 200 && code < 300) {
                        JSONArray choices = new JSONObject(resp).optJSONArray("choices");
                        String content = choices != null && choices.length() > 0
                                ? choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                                : "";
                        bumpCount(ctx);
                        return parse(content);
                    }
                    // surface the real reason (e.g. "No allowed providers are available")
                    String detail = extractErrorDetail(resp);
                    if (code == 401 || code == 403) throw new Exception("AI auth failed (" + code + ")");
                    lastErr = new Exception(detail + " (model " + model + ", " + code + ")");
                    if (code == 429) continue; // upstream pool saturated — next model in chain
                    if (code < 500) break; // non-transient for this model — try next model
                } catch (java.io.IOException e) {
                    lastErr = e;
                }
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
