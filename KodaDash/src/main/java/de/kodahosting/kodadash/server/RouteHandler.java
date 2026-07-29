package de.kodahosting.kodadash.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.kodahosting.kodadash.KodaDash;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all API route handlers.
 */
public abstract class RouteHandler implements HttpHandler {
    protected final KodaDash plugin;
    protected final Gson gson = new Gson();
    private static final ConcurrentHashMap<String, Long> rateLimits = new ConcurrentHashMap<>();

    public RouteHandler(KodaDash plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String origin = plugin.getConfig().getString("cors-origins", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token, X-Dashboard-Password");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            long now = System.currentTimeMillis();
            rateLimits.entrySet().removeIf(e -> now - e.getValue() > 60000);
            
            if (requireAuth()) {
                if (!plugin.getAuthManager().authenticate(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }
            }

            String method = exchange.getRequestMethod().toUpperCase();
            switch (method) {
                case "GET": handleGet(exchange); break;
                case "POST": handlePost(exchange); break;
                case "PUT": handlePut(exchange); break;
                case "DELETE": handleDelete(exchange); break;
                default: sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    protected boolean requireAuth() {
        return true;
    }

    protected void handleGet(HttpExchange exchange) throws IOException { sendError(exchange, 405, "Method Not Allowed"); }
    protected void handlePost(HttpExchange exchange) throws IOException { sendError(exchange, 405, "Method Not Allowed"); }
    protected void handlePut(HttpExchange exchange) throws IOException { sendError(exchange, 405, "Method Not Allowed"); }
    protected void handleDelete(HttpExchange exchange) throws IOException { sendError(exchange, 405, "Method Not Allowed"); }

    protected void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, statusCode, error);
    }

    protected String readBody(HttpExchange exchange) throws IOException {
        int bufferSize = 1024;
        char[] buffer = new char[bufferSize];
        StringBuilder out = new StringBuilder();
        try (Reader in = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            int charsRead;
            while ((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
                out.append(buffer, 0, charsRead);
            }
        }
        return out.toString();
    }

    protected String getPathParam(HttpExchange exchange, String basePath) {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith(basePath)) {
            String param = path.substring(basePath.length());
            if (param.startsWith("/")) param = param.substring(1);
            return param;
        }
        return "";
    }
}
