package de.kodahosting.kodadash.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Handles player management routes with sub-path routing.
 */
public class PlayersRoute extends RouteHandler {

    public PlayersRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // CORS headers
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

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method) && (path.equals("/api/players") || path.equals("/api/players/"))) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.endsWith("/kick")) {
                    handleKick(exchange);
                } else if (path.endsWith("/ban")) {
                    handleBan(exchange);
                } else if (path.endsWith("/unban")) {
                    handleUnban(exchange);
                } else if (path.endsWith("/message")) {
                    handleMessage(exchange);
                } else {
                    sendError(exchange, 404, "Unknown player action");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Players route error: " + e.getMessage());
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        try {
            Future<JsonArray> future = Bukkit.getScheduler().callSyncMethod(plugin, new Callable<JsonArray>() {
                @Override
                public JsonArray call() throws Exception {
                    JsonArray playersArray = new JsonArray();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        JsonObject pJson = new JsonObject();
                        pJson.addProperty("name", player.getName());
                        pJson.addProperty("uuid", player.getUniqueId().toString());
                        pJson.addProperty("health", player.getHealth());
                        pJson.addProperty("gamemode", player.getGameMode().name());
                        pJson.addProperty("world", player.getWorld().getName());
                        pJson.addProperty("isOp", player.isOp());
                        playersArray.add(pJson);
                    }
                    return playersArray;
                }
            });

            JsonArray players = future.get();
            JsonObject response = new JsonObject();
            response.add("players", players);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get players: " + e.getMessage());
            sendError(exchange, 500, "Failed to get players");
        }
    }

    private void handleKick(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("player")) {
                sendError(exchange, 400, "Missing player name");
                return;
            }

            String targetPlayer = json.get("player").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Kicked from dashboard";

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) {
                    p.kickPlayer(ChatColor.translateAlternateColorCodes('&', reason));
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format");
        }
    }

    private void handleBan(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("player")) {
                sendError(exchange, 400, "Missing player name");
                return;
            }

            String targetPlayer = json.get("player").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Banned from dashboard";

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetPlayer,
                        ChatColor.translateAlternateColorCodes('&', reason), null, "KodaDash");
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) {
                    p.kickPlayer(ChatColor.translateAlternateColorCodes('&', reason));
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format");
        }
    }

    private void handleUnban(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("player")) {
                sendError(exchange, 400, "Missing player name");
                return;
            }

            String targetPlayer = json.get("player").getAsString();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getBanList(BanList.Type.NAME).pardon(targetPlayer);
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format");
        }
    }

    private void handleMessage(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("player") || !json.has("message")) {
                sendError(exchange, 400, "Missing player or message");
                return;
            }

            String targetPlayer = json.get("player").getAsString();
            String message = json.get("message").getAsString();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format");
        }
    }
}
