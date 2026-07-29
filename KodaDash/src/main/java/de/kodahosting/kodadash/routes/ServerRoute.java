package de.kodahosting.kodadash.routes;

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
            
            // Assuming StatsManager provides these methods
            response.addProperty("tps", this.plugin.getStatsManager().getTps());
            response.addProperty("usedRam", this.plugin.getStatsManager().getUsedRam());
            response.addProperty("maxRam", this.plugin.getStatsManager().getMaxRam());
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
