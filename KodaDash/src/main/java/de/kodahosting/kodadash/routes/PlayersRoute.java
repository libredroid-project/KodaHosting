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

import org.bukkit.Statistic;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
                } else if (path.endsWith("/heal")) {
                    handleSimpleAction(exchange, "heal");
                } else if (path.endsWith("/feed")) {
                    handleSimpleAction(exchange, "feed");
                } else if (path.endsWith("/starve")) {
                    handleSimpleAction(exchange, "starve");
                } else if (path.endsWith("/kill")) {
                    handleSimpleAction(exchange, "kill");
                } else if (path.endsWith("/op")) {
                    handleSimpleAction(exchange, "op");
                } else if (path.endsWith("/deop")) {
                    handleSimpleAction(exchange, "deop");
                } else if (path.endsWith("/wipe")) {
                    handleSimpleAction(exchange, "wipe");
                } else if (path.endsWith("/whitelist")) {
                    handleSimpleAction(exchange, "whitelist");
                } else if (path.endsWith("/unwhitelist")) {
                    handleSimpleAction(exchange, "unwhitelist");
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
                        
                        // Stats
                        JsonObject stats = new JsonObject();
                        try {
                            stats.addProperty("deaths", player.getStatistic(Statistic.DEATHS));
                            stats.addProperty("mobsKilled", player.getStatistic(Statistic.MOB_KILLS));
                            stats.addProperty("damageTaken", player.getStatistic(Statistic.DAMAGE_TAKEN));
                            
                            int playTime = 0;
                            try {
                                playTime = player.getStatistic(Statistic.valueOf("PLAY_ONE_MINUTE"));
                            } catch (IllegalArgumentException e) {
                                try {
                                    playTime = player.getStatistic(Statistic.valueOf("PLAY_ONE_TICK"));
                                } catch (IllegalArgumentException e2) {}
                            }
                            stats.addProperty("playTimeHours", playTime / (20 * 60 * 60)); // Ticks to hours
                        } catch (Exception e) {
                            // Fallback if statistics are disabled or error
                        }
                        pJson.add("stats", stats);
                        
                        // Inventory
                        JsonObject inv = new JsonObject();
                        PlayerInventory playerInv = player.getInventory();
                        inv.add("armor", serializeItems(playerInv.getArmorContents()));
                        inv.add("main", serializeItems(playerInv.getContents())); 
                        
                        String offhandStr = null;
                        try {
                            java.lang.reflect.Method m = playerInv.getClass().getMethod("getItemInOffHand");
                            ItemStack offhand = (ItemStack) m.invoke(playerInv);
                            if (offhand != null) offhandStr = offhand.getType().name();
                        } catch (Exception e) {}
                        
                        if (offhandStr != null) {
                            pJson.addProperty("offhand", offhandStr);
                        } else {
                            pJson.add("offhand", com.google.gson.JsonNull.INSTANCE);
                        }
                        pJson.add("inventory", inv);
                        
                        pJson.addProperty("isWhitelisted", player.isWhitelisted());
                        
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

    private JsonArray serializeItems(ItemStack[] items) {
        JsonArray array = new JsonArray();
        if (items != null) {
            for (ItemStack item : items) {
                if (item == null || item.getType().name().equals("AIR")) {
                    array.add(com.google.gson.JsonNull.INSTANCE);
                } else {
                    JsonObject iObj = new JsonObject();
                    iObj.addProperty("type", item.getType().name());
                    iObj.addProperty("amount", item.getAmount());
                    array.add(iObj);
                }
            }
        }
        return array;
    }

    private void handleSimpleAction(HttpExchange exchange, String action) throws IOException {
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
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) {
                    switch (action) {
                        case "heal":
                            p.setHealth(p.getMaxHealth());
                            p.setFoodLevel(20);
                            break;
                        case "feed":
                            p.setFoodLevel(20);
                            break;
                        case "starve":
                            p.setFoodLevel(0);
                            break;
                        case "kill":
                            p.setHealth(0);
                            break;
                        case "op":
                            p.setOp(true);
                            break;
                        case "deop":
                            p.setOp(false);
                            break;
                        case "wipe":
                            p.getInventory().clear();
                            p.getEnderChest().clear();
                            p.setExp(0);
                            p.setLevel(0);
                            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
                            p.kickPlayer("Your data has been wiped.");
                            break;
                        case "whitelist":
                            p.setWhitelisted(true);
                            break;
                        case "unwhitelist":
                            p.setWhitelisted(false);
                            break;
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
}
