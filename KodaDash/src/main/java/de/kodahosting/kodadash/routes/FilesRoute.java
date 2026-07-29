package de.kodahosting.kodadash.routes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;

import java.io.IOException;

/**
 * Handles file management via the API with sub-path routing.
 */
public class FilesRoute extends RouteHandler {

    public FilesRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // CORS headers
            String origin = plugin.getConfig().getString("cors-origins", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
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

            // Extract file path from URL
            String filePath = getPathParam(exchange, "/api/files");
            if (filePath == null) {
                filePath = "";
            }

            String method = exchange.getRequestMethod().toUpperCase();

            switch (method) {
                case "GET":
                    handleFileGet(exchange, filePath);
                    break;
                case "POST":
                    handleFilePost(exchange, filePath);
                    break;
                case "PUT":
                    handleFilePut(exchange, filePath);
                    break;
                case "DELETE":
                    handleFileDelete(exchange, filePath);
                    break;
                default:
                    sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (SecurityException e) {
            sendError(exchange, 403, "Access denied: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Files route error: " + e.getMessage());
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    private void handleFileGet(HttpExchange exchange, String path) throws IOException {
        try {
            JsonObject result = plugin.getFileManager().getFileOrDirectory(path);
            if (result == null) {
                sendError(exchange, 404, "File or directory not found");
            } else {
                sendJson(exchange, 200, result);
            }
        } catch (SecurityException e) {
            sendError(exchange, 403, "Access denied: " + e.getMessage());
        } catch (IOException e) {
            sendError(exchange, 500, "Failed to read: " + e.getMessage());
        }
    }

    private void handleFilePost(HttpExchange exchange, String path) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("content")) {
                sendError(exchange, 400, "Missing 'content' parameter");
                return;
            }

            String content = json.get("content").getAsString();
            boolean success = plugin.getFileManager().writeFile(path, content);

            if (success) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("path", path);
                sendJson(exchange, 200, response);
            } else {
                sendError(exchange, 500, "Failed to write file");
            }
        } catch (SecurityException e) {
            sendError(exchange, 403, "Access denied: " + e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private void handleFilePut(HttpExchange exchange, String path) throws IOException {
        // PUT is used for creating directories
        boolean success = plugin.getFileManager().createDirectory(path);
        if (success) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("path", path);
            sendJson(exchange, 200, response);
        } else {
            sendError(exchange, 500, "Failed to create directory");
        }
    }

    private void handleFileDelete(HttpExchange exchange, String path) throws IOException {
        boolean success = plugin.getFileManager().deleteFileOrDirectory(path);
        if (success) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);
        } else {
            sendError(exchange, 500, "Failed to delete. Make sure directory is empty.");
        }
    }
}
