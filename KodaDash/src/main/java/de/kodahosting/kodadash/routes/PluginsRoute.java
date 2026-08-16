package de.kodahosting.kodadash.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.File;

public class PluginsRoute extends RouteHandler {

    public PluginsRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String origin = plugin.getConfig().getString("cors-origins", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token, X-Dashboard-Password");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!plugin.getAuthManager().authenticate(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.endsWith("/enable")) {
                    handlePluginAction(exchange, true);
                } else if (path.endsWith("/disable")) {
                    handlePluginAction(exchange, false);
                } else if (path.endsWith("/install")) {
                    handlePluginInstall(exchange);
                } else {
                    sendError(exchange, 404, "Unknown plugin action");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Plugins route error: " + e.getMessage());
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin[] plugins = pm.getPlugins();

        JsonArray pluginsArray = new JsonArray();
        for (Plugin p : plugins) {
            JsonObject pJson = new JsonObject();
            pJson.addProperty("name", p.getName());
            pJson.addProperty("version", p.getDescription().getVersion());
            
            String authors = String.join(", ", p.getDescription().getAuthors());
            pJson.addProperty("authors", authors);
            
            pJson.addProperty("description", p.getDescription().getDescription());
            pJson.addProperty("enabled", p.isEnabled());
            
            pluginsArray.add(pJson);
        }

        JsonObject response = new JsonObject();
        response.add("plugins", pluginsArray);
        sendJson(exchange, 200, response);
    }

    private void handlePluginAction(HttpExchange exchange, boolean enable) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("plugin")) {
                sendError(exchange, 400, "Missing plugin name");
                return;
            }

            String pluginName = json.get("plugin").getAsString();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (targetPlugin != null) {
                    if (enable && !targetPlugin.isEnabled()) {
                        Bukkit.getPluginManager().enablePlugin(targetPlugin);
                    } else if (!enable && targetPlugin.isEnabled()) {
                        Bukkit.getPluginManager().disablePlugin(targetPlugin);
                    }
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format");
        }
    }

    private void handlePluginInstall(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("url") || !json.has("filename")) {
                sendError(exchange, 400, "Missing url or filename");
                return;
            }

            String urlStr = json.get("url").getAsString();
            String filename = json.get("filename").getAsString();
            
            // Validate filename for security
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\") || !filename.endsWith(".jar")) {
                sendError(exchange, 400, "Invalid filename");
                return;
            }

            File pluginsDir = new File(Bukkit.getServer().getWorldContainer(), "plugins");
            if (!pluginsDir.exists()) pluginsDir.mkdirs();
            
            File targetFile = new File(pluginsDir, filename);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "KodaDash-Plugin/1.0");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(15000);

                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    plugin.getLogger().info("Successfully installed plugin from Modrinth: " + filename);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to download plugin: " + e.getMessage());
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format or Error: " + e.getMessage());
        }
    }
}
