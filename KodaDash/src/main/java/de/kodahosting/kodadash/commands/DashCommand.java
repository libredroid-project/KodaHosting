package de.kodahosting.kodadash.commands;

import de.kodahosting.kodadash.KodaDash;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * In-game command handler for /kodadash.
 */
public class DashCommand implements CommandExecutor {
    private final KodaDash plugin;

    public DashCommand(KodaDash plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kodadash.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "KodaDash Commands:");
            sender.sendMessage(ChatColor.GRAY + "/kodadash status" + ChatColor.WHITE + " - Show dashboard status");
            sender.sendMessage(ChatColor.GRAY + "/kodadash token" + ChatColor.WHITE + " - Show API token");
            sender.sendMessage(ChatColor.GRAY + "/kodadash regenerate" + ChatColor.WHITE + " - Regenerate API token");
            sender.sendMessage(ChatColor.GRAY + "/kodadash setpassword <pass>" + ChatColor.WHITE + " - Set dashboard password");
            sender.sendMessage(ChatColor.GRAY + "/kodadash removepassword" + ChatColor.WHITE + " - Remove password");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status":
                boolean hasPass = plugin.getConfig().contains("password-hash") && plugin.getConfig().getString("password-hash") != null;
                sender.sendMessage(ChatColor.GREEN + "KodaDash Status:");
                sender.sendMessage(ChatColor.GRAY + "Port: " + ChatColor.WHITE + plugin.getConfig().getInt("port", 7867));
                sender.sendMessage(ChatColor.GRAY + "Password Protected: " + ChatColor.WHITE + (hasPass ? "Yes" : "No"));
                break;
            case "token":
                sender.sendMessage(ChatColor.GREEN + "Current API Token: " + ChatColor.WHITE + plugin.getAuthManager().getToken());
                break;
            case "regenerate":
                String newToken = plugin.getAuthManager().regenerateToken();
                sender.sendMessage(ChatColor.GREEN + "Token regenerated: " + ChatColor.WHITE + newToken);
                break;
            case "setpassword":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /kodadash setpassword <password>");
                    return true;
                }
                plugin.getAuthManager().setPassword(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Dashboard password set successfully.");
                break;
            case "removepassword":
                plugin.getAuthManager().removePassword();
                sender.sendMessage(ChatColor.GREEN + "Dashboard password removed.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        }
        return true;
    }
}
