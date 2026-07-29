package de.kodahosting.kodadash.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;

/**
 * Returns comprehensive server information.
 */
public class ServerRoute extends RouteHandler {

    public ServerRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    public void handleGet(HttpExchange exchange) throws IOException {
        try {
            JsonObject response = new JsonObject();
            
            response.addProperty("name", Bukkit.getServer().getName());
            response.addProperty("version", Bukkit.getServer().getVersion());
            
            String motd = Bukkit.getServer().getMotd();
            if (motd != null) {
                motd = ChatColor.stripColor(motd);
            } else {
                motd = "A Minecraft Server";
            }
            response.addProperty("motd", motd);
            
            response.addProperty("onlinePlayers", Bukkit.getServer().getOnlinePlayers().size());
            response.addProperty("maxPlayers", Bukkit.getServer().getMaxPlayers());
            response.addProperty("usedRam", this.plugin.getStatsManager().getUsedRam());
            response.addProperty("maxRam", this.plugin.getStatsManager().getMaxRam());
            
            JsonArray tpsArray = new JsonArray();
            tpsArray.add(new com.google.gson.JsonPrimitive(this.plugin.getStatsManager().getTps()));
            response.add("tps", tpsArray);
            
            JsonObject ramObj = new JsonObject();
            ramObj.addProperty("max", this.plugin.getStatsManager().getMaxRam());
            long usedRam = this.plugin.getStatsManager().getUsedRam();
            long maxRam = this.plugin.getStatsManager().getMaxRam();
            ramObj.addProperty("allocated", usedRam);
            ramObj.addProperty("free", 0); // simplification for the UI calculation
            response.add("ram", ramObj);
            
            JsonObject playersObj = new JsonObject();
            playersObj.addProperty("online", Bukkit.getServer().getOnlinePlayers().size());
            playersObj.addProperty("max", Bukkit.getServer().getMaxPlayers());
            response.add("players", playersObj);
            
            response.addProperty("uptime", this.plugin.getStatsManager().getUptime());
            
            response.addProperty("port", Bukkit.getServer().getPort());
            response.addProperty("onlineMode", Bukkit.getServer().getOnlineMode());
            
            if (Bukkit.getServer().getWorlds().size() > 0) {
                response.addProperty("worldName", Bukkit.getServer().getWorlds().get(0).getName());
            } else {
                response.addProperty("worldName", "world");
            }
            
            response.addProperty("gamemode", Bukkit.getServer().getDefaultGameMode().name());
            
            // Difficult enum check
            if (Bukkit.getServer().getWorlds().size() > 0) {
                response.addProperty("difficulty", Bukkit.getServer().getWorlds().get(0).getDifficulty().name());
            } else {
                response.addProperty("difficulty", "NORMAL");
            }
            
            response.addProperty("hasIcon", Bukkit.getServer().getServerIcon() != null);

            sendJson(exchange, 200, response);
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
}
