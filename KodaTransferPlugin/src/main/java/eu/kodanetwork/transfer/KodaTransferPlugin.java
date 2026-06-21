package eu.kodanetwork.transfer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class KodaTransferPlugin extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        getLogger().info("KodaTransfer enabled - checking transfer configuration...");
        try {
            ensureTransfersEnabled();
        } catch (Exception e) {
            getLogger().warning("Could not update server.properties: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("khub") != null) {
            getCommand("khub").setExecutor(this);
        }
    }

    @EventHandler
    public void onPing(com.destroystokyo.paper.event.server.PaperServerListPingEvent e) {
        // Clear existing sample
        e.getPlayerSample().clear();
        // Add ALL online players to the ping sample so KodaLobbyPlugin can track them
        for (Player p : Bukkit.getOnlinePlayers()) {
            e.getPlayerSample().add(p.getPlayerProfile());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.sendMessage(ChatColor.GREEN + "Connecting to KodaNetwork Lobby...");
            try {
                p.transfer("kodanetwork.eu", 25565);
            } catch (Exception ex) {
                getLogger().warning("Transfer failed for " + p.getName() + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                p.sendMessage(ChatColor.RED + "Transfer failed: " + ex.getMessage());
                p.sendMessage(ChatColor.GRAY + "Make sure your client supports transfers (1.20.5+)");
                p.sendMessage(ChatColor.GRAY + "and the server has been restarted after KodaTransfer was installed.");
            }
        }
        return true;
    }

    private void ensureTransfersEnabled() throws IOException {
        File serverProps = new File(getServer().getWorldContainer(), "server.properties");
        if (!serverProps.exists()) {
            serverProps = new File("server.properties");
        }
        if (!serverProps.exists()) {
            getLogger().warning("server.properties not found!");
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean found = false;
        boolean alreadyEnabled = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(serverProps))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("accepts-transfers=")) {
                    found = true;
                    if (line.equals("accepts-transfers=true")) {
                        alreadyEnabled = true;
                        lines.add(line);
                    } else {
                        lines.add("accepts-transfers=true");
                    }
                } else {
                    lines.add(line);
                }
            }
        }

        if (alreadyEnabled) {
            getLogger().info("Transfer API is already enabled.");
            return;
        }

        if (!found) {
            lines.add("accepts-transfers=true");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(serverProps))) {
            for (String l : lines) {
                writer.println(l);
            }
        }

        getLogger().info("Set accepts-transfers=true in server.properties. Will take effect on next restart.");
    }
}
