package de.kodahosting.kodadash.routes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles console reading, SSE streaming, and command execution.
 * Overrides handle() for sub-path routing (/stream, /command).
 */
public class ConsoleRoute extends RouteHandler {

    public ConsoleRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // CORS headers (must be set before any response)
            String origin = plugin.getConfig().getString("cors-origins", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token, X-Dashboard-Password");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Auth check
            if (!plugin.getAuthManager().authenticate(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.endsWith("/stream") && "GET".equalsIgnoreCase(method)) {
                handleStream(exchange);
            } else if (path.endsWith("/command") && "POST".equalsIgnoreCase(method)) {
                handleCommand(exchange);
            } else if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Console route error: " + e.getMessage());
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int since = -1;

        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length > 1 && "since".equals(pair[0])) {
                    try {
                        since = Integer.parseInt(pair[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        Object linesData;
        if (since >= 0) {
            linesData = plugin.getConsoleManager().getLinesSince(since);
        } else {
            linesData = plugin.getConsoleManager().getRecentLines();
        }

        JsonObject response = new JsonObject();
        response.add("lines", gson.toJsonTree(linesData));
        response.addProperty("total", plugin.getConsoleManager().getTotalLines());

        sendJson(exchange, 200, response);
    }

    /**
     * SSE endpoint for real-time console streaming.
     */
    private void handleStream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();

        // Send retry interval
        String retry = "retry: 1000\n\n";
        os.write(retry.getBytes(StandardCharsets.UTF_8));
        os.flush();

        // Register this stream for live updates
        plugin.getConsoleManager().registerSseListener(os, exchange);
        // Don't close the exchange - ConsoleManager manages the lifecycle
    }

    /**
     * Execute a server command via POST.
     */
    private void handleCommand(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        JsonObject json = new JsonParser().parse(body).getAsJsonObject();
        if (!json.has("command")) {
            sendError(exchange, 400, "Missing command parameter");
            return;
        }

        String command = json.get("command").getAsString().trim();
        if (command.isEmpty()) {
            sendError(exchange, 400, "Empty command");
            return;
        }

        // Check blocked commands
        List<String> blockedCommands = plugin.getConfig().getStringList("blocked-commands");
        String cmdBase = command.split("\\s+")[0].toLowerCase();
        for (String blocked : blockedCommands) {
            if (cmdBase.equalsIgnoreCase(blocked.trim())) {
                sendError(exchange, 403, "This command is blocked");
                return;
            }
        }

        plugin.getConsoleManager().executeCommand(command);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("command", command);
        sendJson(exchange, 200, response);
    }
}
