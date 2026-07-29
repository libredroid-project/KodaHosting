package de.kodahosting.kodadash.managers;

import com.google.gson.JsonObject;
import de.kodahosting.kodadash.KodaDash;
import org.bukkit.Bukkit;

/**
 * Tracks server performance metrics: TPS, RAM, uptime.
 */
public class StatsManager {
    private final KodaDash plugin;
    private final long[] tickHistory = new long[20];
    private int tickIndex = 0;
    private final long startTime;

    public StatsManager(KodaDash plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();

        // Track tick timestamps for TPS calculation
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickHistory[tickIndex % 20] = System.currentTimeMillis();
            tickIndex++;
        }, 0, 20L);
    }

    /**
     * Get all stats as a JSON object.
     */
    public JsonObject getStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("tps", getTps());
        stats.addProperty("players", Bukkit.getOnlinePlayers().size());
        stats.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        stats.addProperty("usedRam", getUsedRam());
        stats.addProperty("maxRam", getMaxRam());
        stats.addProperty("uptime", getUptime());
        return stats;
    }

    /**
     * Get current TPS. Uses Paper's getTPS() if available, falls back to manual calculation.
     */
    public double getTps() {
        try {
            // Paper servers expose getTPS() - use reflection for Spigot API compatibility
            java.lang.reflect.Method getTPS = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(Bukkit.getServer());
            return Math.min(20.0, Math.round(tps[0] * 100.0) / 100.0);
        } catch (Exception e) {
            // Fallback for non-Paper servers: calculate from tick history
            if (tickIndex < 20) return 20.0;
            long timeSpent = System.currentTimeMillis() - tickHistory[tickIndex % 20];
            if (timeSpent == 0) return 20.0;
            double calculatedTps = 20.0 / (timeSpent / 1000.0);
            return Math.min(20.0, Math.round(calculatedTps * 100.0) / 100.0);
        }
    }

    /**
     * Get used RAM in MB.
     */
    public long getUsedRam() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    /**
     * Get max RAM in MB.
     */
    public long getMaxRam() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    /**
     * Get server uptime in milliseconds since plugin was enabled.
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
}
