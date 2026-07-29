package de.kodahosting.kodadash.auth;

import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import org.mindrot.jbcrypt.BCrypt;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages API token authentication, optional password, and rate limiting.
 */
public class AuthManager {
    private final KodaDash plugin;
    private final ConcurrentHashMap<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();

    private static class FailedAttempt {
        int count;
        long lastAttempt;
    }

    public AuthManager(KodaDash plugin) {
        this.plugin = plugin;
    }

    /**
     * Authenticate an HTTP request by checking token and optional password.
     * Supports Bearer header, X-API-Token header, and ?token= query param.
     */
    public boolean authenticate(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        cleanupOldAttempts();

        FailedAttempt attempt = failedAttempts.get(ip);
        int maxAttempts = plugin.getConfig().getInt("rate-limit.max-auth-attempts", 10);
        if (attempt != null && attempt.count >= maxAttempts
                && System.currentTimeMillis() - attempt.lastAttempt < 60000) {
            return false;
        }

        // Extract token from various sources
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String tokenHeader = exchange.getRequestHeaders().getFirst("X-API-Token");
        String queryToken = extractQueryParam(exchange.getRequestURI().getQuery(), "token");

        String providedToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            providedToken = authHeader.substring(7);
        } else if (tokenHeader != null) {
            providedToken = tokenHeader;
        } else if (queryToken != null) {
            providedToken = queryToken;
        }

        // Validate token with constant-time comparison
        String expectedToken = getToken();
        if (providedToken == null || expectedToken == null || expectedToken.isEmpty()
                || !MessageDigest.isEqual(providedToken.getBytes(), expectedToken.getBytes())) {
            recordFailure(ip);
            return false;
        }

        // Check optional password if configured
        String expectedHash = plugin.getConfig().getString("password-hash");
        if (expectedHash != null && !expectedHash.isEmpty()) {
            String providedPass = exchange.getRequestHeaders().getFirst("X-Dashboard-Password");
            if (providedPass == null || providedPass.isEmpty()) {
                providedPass = extractQueryParam(exchange.getRequestURI().getQuery(), "password");
            }
            if (providedPass == null || !BCrypt.checkpw(providedPass, expectedHash)) {
                recordFailure(ip);
                return false;
            }
        }

        // Success - reset failure count
        failedAttempts.remove(ip);
        return true;
    }

    /**
     * Alias for authenticate() - used by routes that override handle().
     */
    public boolean isAuthenticated(HttpExchange exchange) {
        return authenticate(exchange);
    }

    /**
     * Validate credentials directly (used by AuthRoute POST).
     *
     * @param token    The API token
     * @param password Optional password (null if not required)
     * @param ip       Client IP for rate limiting
     * @return true if valid
     */
    public boolean validate(String token, String password, String ip) {
        cleanupOldAttempts();

        FailedAttempt attempt = failedAttempts.get(ip);
        int maxAttempts = plugin.getConfig().getInt("rate-limit.max-auth-attempts", 10);
        if (attempt != null && attempt.count >= maxAttempts
                && System.currentTimeMillis() - attempt.lastAttempt < 60000) {
            return false;
        }

        String expectedToken = getToken();
        if (token == null || expectedToken == null || expectedToken.isEmpty()
                || !MessageDigest.isEqual(token.getBytes(), expectedToken.getBytes())) {
            recordFailure(ip);
            return false;
        }

        String expectedHash = plugin.getConfig().getString("password-hash");
        if (expectedHash != null && !expectedHash.isEmpty()) {
            if (password == null || !BCrypt.checkpw(password, expectedHash)) {
                recordFailure(ip);
                return false;
            }
        }

        failedAttempts.remove(ip);
        return true;
    }

    /**
     * Check if an optional password is configured.
     */
    public boolean hasPassword() {
        String hash = plugin.getConfig().getString("password-hash");
        return hash != null && !hash.isEmpty();
    }

    /**
     * Set a new dashboard password (BCrypt hashed).
     */
    public void setPassword(String plaintext) {
        String hash = BCrypt.hashpw(plaintext, BCrypt.gensalt(12));
        plugin.getConfig().set("password-hash", hash);
        plugin.saveConfig();
    }

    /**
     * Remove the optional password requirement.
     */
    public void removePassword() {
        plugin.getConfig().set("password-hash", "");
        plugin.saveConfig();
    }

    /**
     * Generate a new API token and save to config.
     *
     * @return The new token string
     */
    public String regenerateToken() {
        String newToken = generateRandomToken();
        plugin.getConfig().set("api-token", newToken);
        plugin.saveConfig();
        return newToken;
    }

    /**
     * Get the current API token.
     */
    public String getToken() {
        return plugin.getConfig().getString("api-token", "");
    }

    // --- Private helpers ---

    private void recordFailure(String ip) {
        FailedAttempt attempt = failedAttempts.computeIfAbsent(ip, k -> new FailedAttempt());
        attempt.count++;
        attempt.lastAttempt = System.currentTimeMillis();
    }

    private void cleanupOldAttempts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, FailedAttempt>> it = failedAttempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FailedAttempt> entry = it.next();
            if (now - entry.getValue().lastAttempt > 60000) {
                it.remove();
            }
        }
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length > 1 && key.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    private String generateRandomToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
