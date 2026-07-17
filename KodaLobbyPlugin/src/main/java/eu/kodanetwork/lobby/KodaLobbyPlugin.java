package eu.kodanetwork.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KodaLobbyPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private String menuTitle;
    private final List<String> knownServers = new ArrayList<>();
    private final Map<String, ServerStatus> serverStatuses = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> searchingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> pending2faMap = new ConcurrentHashMap<>();
    private final List<CustomItem> customItems = new ArrayList<>();
    private final Map<String, String> userUuidMap = new ConcurrentHashMap<>(); // app_uuid -> mc_username (main account)
    private final Map<String, String> playerToAppUuidMap = new ConcurrentHashMap<>(); // mc_username -> app_uuid
    private final Map<String, Boolean> user2faEnabledMap = new ConcurrentHashMap<>();
    private final Map<String, String> user2faPasswordMap = new ConcurrentHashMap<>();
    private final Map<String, String> lastNotifiedActive = new ConcurrentHashMap<>();
    private final Map<UUID, String> pluginSearchServer = new ConcurrentHashMap<>(); // player UUID -> server they're managing plugins for
    private final Map<UUID, String> consoleInputServer = new ConcurrentHashMap<>(); // player UUID -> server console
    private final Map<String, com.google.gson.JsonObject> playerPermissionsMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isMainMap = new ConcurrentHashMap<>();

    private static class ServerStatus {
        boolean online;
        int players = 0;
        int maxPlayers = 20;
        String version = "";
        String owner = "";
        String ownerUuid = "";
        String baseDomain = "kodanetwork.eu";
        List<String> samplePlayers = new ArrayList<>();
    }

    private static class CustomItem {
        String id;
        Material material;
        int slot;
        String displayName;
        List<String> lore = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<ItemFlag> itemFlags = new ArrayList<>();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("server") != null) {
            getCommand("server").setExecutor(this);
            getCommand("server").setTabCompleter(this);
        }
        if (getCommand("join") != null) {
            getCommand("join").setExecutor(this);
            getCommand("join").setTabCompleter(this);
        }
        if (getCommand("link") != null) {
            getCommand("link").setExecutor(this);
        }
        if (getCommand("lobbyadmin") != null) {
            getCommand("lobbyadmin").setExecutor(this);
        }
        if (getCommand("myservers") != null) {
            getCommand("myservers").setExecutor(this);
        }

        getLogger().info("KodaLobbyPlugin enabled! Transfer API active.");

        // Fetch server data from Supabase every 30 seconds
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::fetchServersFromSupabase, 20L, 20L * 30L);

        // Update GUI every 10 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTitle().equals(menuTitle)) {
                    updateServerGui(p.getOpenInventory().getTopInventory(), p);
                }
            }
        }, 200L, 200L);

        // Every 5 seconds, ensure slot 4 and 0 have the items
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isNavigator(p.getInventory().getItem(4))) {
                    p.getInventory().setItem(4, getNavigatorItem());
                }
                if (!isMyServersItem(p.getInventory().getItem(0))) {
                    p.getInventory().setItem(0, getMyServersItem());
                }
                if (isMyServersItem(p.getInventory().getItem(8))) {
                    p.getInventory().setItem(8, null); // Clear old slot 8
                }
            }
        }, 100L, 100L);
    }

    private void loadConfiguration() {
        reloadConfig();
        menuTitle = ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("menu_title", "&8Server Browser"));

        // Load custom items
        customItems.clear();
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection sec = itemsSection.getConfigurationSection(key);
                if (sec == null) continue;
                CustomItem ci = new CustomItem();
                ci.id = key;
                try {
                    ci.material = Material.valueOf(sec.getString("material", "STONE").toUpperCase());
                } catch (Exception e) {
                    ci.material = Material.STONE;
                }
                ci.slot = sec.getInt("slot", 0);
                ci.displayName = parseColors(sec.getString("display_name", key));
                List<String> loreRaw = sec.getStringList("lore");
                for (String l : loreRaw) {
                    ci.lore.add(parseColors(l));
                }
                ci.actions = sec.getStringList("actions");
                List<String> flags = sec.getStringList("item_flags");
                for (String f : flags) {
                    try { ci.itemFlags.add(ItemFlag.valueOf(f)); } catch (Exception ignored) {}
                }
                customItems.add(ci);
            }
        }
        getLogger().info("Loaded " + customItems.size() + " custom items from config.");
    }

    private String parseColors(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<#([A-Fa-f0-9]{6})>|&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(sb, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // ─── Server Fetching from Supabase ───────────────────────────────

    private void fetchServersFromSupabase() {
        try {
            // Fetch all servers from koda_servers table, excluding deleted ones
            URL url = new URL(eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?select=host,base_domain,online_players,server_version,owner_app_uuid&server_version=neq.DELETED");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey());
            conn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey());
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    int c;
                    while ((c = reader.read()) != -1) sb.append((char) c);
                }
                String json = sb.toString();
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
                List<String> newServers = new ArrayList<>();
                // Clear old statuses and replace with fresh data
                serverStatuses.clear();
                for (com.google.gson.JsonElement el : arr) {
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    String host = obj.has("host") && !obj.get("host").isJsonNull() ? obj.get("host").getAsString() : "";
                    String version = obj.has("server_version") && !obj.get("server_version").isJsonNull() ? obj.get("server_version").getAsString() : "";
                    String baseDomain = obj.has("base_domain") && !obj.get("base_domain").isJsonNull() ? obj.get("base_domain").getAsString() : "kodanetwork.eu";
                    
                    if (host.isEmpty() || host.startsWith("deleted_") || "DELETED".equals(version) || "HIBERNATED".equals(version)) continue;
                    
                    if (!newServers.contains(host)) newServers.add(host);

                    ServerStatus status = new ServerStatus();
                    status.baseDomain = baseDomain;
                    status.players = obj.has("online_players") && !obj.get("online_players").isJsonNull()
                        ? obj.get("online_players").getAsInt() : 0;
                    status.version = obj.has("server_version") && !obj.get("server_version").isJsonNull()
                        ? obj.get("server_version").getAsString() : "";
                    
                    if (status.version.contains(" | ")) {
                        String[] parts = status.version.split(" \\| ", 2);
                        status.version = parts[0];
                        for (String p : parts[1].split(",")) {
                            if (!p.trim().isEmpty()) status.samplePlayers.add(p.trim());
                        }
                    }

                    status.owner = obj.has("owner_app_uuid") && !obj.get("owner_app_uuid").isJsonNull()
                        ? obj.get("owner_app_uuid").getAsString() : "";
                    status.ownerUuid = status.owner; // save raw UUID before mapping
                    
                    status.online = status.players > 0 || (!status.version.isEmpty() && !status.version.startsWith("CMD:") && !status.version.startsWith("OFFLINE|"));
                    if (status.version.isEmpty() || status.version.startsWith("CMD:") || status.version.startsWith("OFFLINE|")) status.online = false;
                    
                    if (status.version.startsWith("OFFLINE|")) {
                        status.version = status.version.substring(8);
                    }
                    
                    serverStatuses.put(host, status);
                }
                // Fetch users to map app_uuid to mc_username
                java.util.Map<String, String> userMap = new java.util.HashMap<>();
                java.util.Map<String, String> reverseMap = new java.util.HashMap<>();
                java.util.Map<String, Boolean> faEnabledMap = new java.util.HashMap<>();
                java.util.Map<String, String> faPasswordMap = new java.util.HashMap<>();
                java.util.Map<String, com.google.gson.JsonObject> tempPermsMap = new java.util.HashMap<>();
                java.util.Map<String, Boolean> tempIsMainMap = new java.util.HashMap<>();
                try {
                    URL uUrl = new URL(eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?select=app_uuid,mc_username,two_fa_enabled,two_fa_password,last_active,is_main,permissions");
                    HttpURLConnection uConn = (HttpURLConnection) uUrl.openConnection();
                    uConn.setRequestMethod("GET");
                    uConn.setRequestProperty("apikey", eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey());
                    uConn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey());
                    if (uConn.getResponseCode() == 200) {
                        StringBuilder usb = new StringBuilder();
                        try (InputStreamReader ur = new InputStreamReader(uConn.getInputStream())) {
                            int c; while ((c = ur.read()) != -1) usb.append((char) c);
                        }
                        com.google.gson.JsonArray uArr = com.google.gson.JsonParser.parseString(usb.toString()).getAsJsonArray();
                        for (com.google.gson.JsonElement el : uArr) {
                            com.google.gson.JsonObject obj = el.getAsJsonObject();
                            if (obj.has("app_uuid") && !obj.get("app_uuid").isJsonNull() &&
                                obj.has("mc_username") && !obj.get("mc_username").isJsonNull()) {
                                
                                String lastActiveStr = obj.has("last_active") && !obj.get("last_active").isJsonNull() ? obj.get("last_active").getAsString() : null;
                                if (lastActiveStr != null) {
                                    try {
                                        java.time.Instant lastActive = java.time.Instant.parse(lastActiveStr);
                                        if (java.time.Duration.between(lastActive, java.time.Instant.now()).toDays() > 7) {
                                            continue; // Inactive > 7 days, skip this user mapping
                                        }
                                        
                                        // Check if recently active (e.g. just logged in)
                                        if (java.time.Duration.between(lastActive, java.time.Instant.now()).getSeconds() < 40) {
                                            String mcN = obj.get("mc_username").getAsString();
                                            String lastStr = lastNotifiedActive.get(mcN);
                                            boolean shouldNotify = false;
                                            
                                            if (lastStr == null) {
                                                shouldNotify = true;
                                            } else if (!lastActiveStr.equals(lastStr)) {
                                                try {
                                                    java.time.Instant lastAct = java.time.Instant.parse(lastStr);
                                                    // Only notify if there has been a gap of at least 3 minutes (180 seconds)
                                                    if (java.time.Duration.between(lastAct, lastActive).getSeconds() > 180) {
                                                        shouldNotify = true;
                                                    }
                                                } catch (Exception e) {
                                                    shouldNotify = true;
                                                }
                                            }
                                            
                                            // Save latest timestamp
                                            lastNotifiedActive.put(mcN, lastActiveStr);
                                            
                                            if (shouldNotify) {
                                                Bukkit.getScheduler().runTask(eu.kodanetwork.lobby.KodaLobbyPlugin.this, () -> {
                                                    org.bukkit.entity.Player p = Bukkit.getPlayer(mcN);
                                                    if (p != null && p.isOnline()) {
                                                        p.sendMessage(org.bukkit.ChatColor.GREEN + "[App] Du bist nun in der App online! Server synchronisiert.");
                                                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                                    }
                                                });
                                            }
                                        }
                                    } catch(Exception ignored) {}
                                }

                                String appUuid = obj.get("app_uuid").getAsString();
                                String mcName = obj.get("mc_username").getAsString();
                                boolean isMain = obj.has("is_main") && !obj.get("is_main").isJsonNull() && obj.get("is_main").getAsBoolean();
                                
                                reverseMap.put(mcName, appUuid);
                                if (isMain || !userMap.containsKey(appUuid)) {
                                    userMap.put(appUuid, mcName);
                                }
                                
                                if (obj.has("two_fa_enabled") && !obj.get("two_fa_enabled").isJsonNull()) {
                                    faEnabledMap.put(appUuid, obj.get("two_fa_enabled").getAsBoolean());
                                }
                                if (obj.has("two_fa_password") && !obj.get("two_fa_password").isJsonNull()) {
                                    faPasswordMap.put(appUuid, obj.get("two_fa_password").getAsString());
                                }
                                
                                tempIsMainMap.put(mcName, isMain);
                                if (obj.has("permissions") && !obj.get("permissions").isJsonNull()) {
                                    tempPermsMap.put(mcName, obj.get("permissions").getAsJsonObject());
                                } else {
                                    tempPermsMap.put(mcName, new com.google.gson.JsonObject());
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                synchronized (knownServers) {
                    knownServers.clear();
                    knownServers.addAll(newServers);
                }
                userUuidMap.clear();
                userUuidMap.putAll(userMap);
                playerToAppUuidMap.clear();
                playerToAppUuidMap.putAll(reverseMap);
                user2faEnabledMap.clear();
                user2faEnabledMap.putAll(faEnabledMap);
                user2faPasswordMap.clear();
                user2faPasswordMap.putAll(faPasswordMap);
                playerPermissionsMap.clear();
                playerPermissionsMap.putAll(tempPermsMap);
                isMainMap.clear();
                isMainMap.putAll(tempIsMainMap);
                
                // Now map owners
                for (ServerStatus s : serverStatuses.values()) {
                    if (s.owner != null && userMap.containsKey(s.owner)) {
                        s.owner = userMap.get(s.owner); // replace UUID with MC name
                    } else {
                        s.owner = "Unknown";
                    }
                }
            } else {
                getLogger().warning("Failed to fetch servers: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            getLogger().warning("Error fetching servers: " + e.getMessage());
        }
    }

    // ─── Commands ───────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lobbyadmin")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "KodaLobbyPlugin config reloaded!");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /lobbyadmin reload");
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (command.getName().equalsIgnoreCase("myservers")) {
            String playerUuid = null;
            for (Map.Entry<String, String> ue : playerToAppUuidMap.entrySet()) {
                if (ue.getKey().equalsIgnoreCase(p.getName())) {
                    playerUuid = ue.getValue();
                    break;
                }
            }

            if (playerUuid != null && user2faEnabledMap.getOrDefault(playerUuid, false)) {
                String expectedPass = user2faPasswordMap.get(playerUuid);
                if (expectedPass != null && !expectedPass.isEmpty()) {
                    pending2faMap.put(p.getUniqueId(), expectedPass);
                    p.sendMessage(ChatColor.GOLD + "2-Factor Authentication is enabled for your account.");
                    p.sendMessage(ChatColor.YELLOW + "Please type your 2FA password in chat to continue.");
                    p.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
                    return true;
                }
            }

            openMyServersGui(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("link")) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /link <code>");
                return true;
            }
            String code = args[0];
            p.sendMessage(ChatColor.YELLOW + "Linking account...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    String jsonBody = "{\"mc_username\": \"" + p.getName() + "\"}";
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?code=eq." + code))
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .header("apikey", eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey())
                        .header("Authorization", "Bearer " + eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey())
                        .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                        
                    java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            p.sendMessage(ChatColor.GREEN + "[✓] Account successfully linked to KodaNetwork App!");
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                        } else {
                            getLogger().warning("Link error for " + p.getName() + ": HTTP " + response.statusCode() + " " + response.body());
                            p.sendMessage(ChatColor.RED + "[!] Fehler beim Linken (HTTP " + response.statusCode() + ").");
                        }
                    });
                } catch (Exception e) {
                    getLogger().warning("Link exception: " + e.getMessage());
                    Bukkit.getScheduler().runTask(this, () ->
                        p.sendMessage(ChatColor.RED + "Error linking account: " + e.getMessage()));
                }
            });
            return true;
        }

        if (args.length == 1) {
            String target = args[0].toLowerCase();

            if (command.getName().equalsIgnoreCase("join")) {
                // First check if it's a player name via Supabase koda_users
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    String foundServer = findPlayerServer(target);
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (foundServer != null) {
                            p.sendMessage(ChatColor.GREEN + "Found player " + target + " on server " + foundServer + "!");
                            sendToServer(p, foundServer);
                        } else {
                            // Fallback to server name
                            boolean known;
                            synchronized (knownServers) {
                                known = knownServers.stream().anyMatch(s -> s.equalsIgnoreCase(target));
                            }
                            if (!known) {
                                p.sendMessage(ChatColor.RED + "Unknown server or player not found: " + ChatColor.WHITE + args[0]);
                                p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/" + label + " <TAB>" + ChatColor.GRAY + " to see available servers.");
                            } else {
                                sendToServer(p, target);
                            }
                        }
                    });
                });
                return true;
            }

            // /server command - only server names
            boolean known;
            synchronized (knownServers) {
                known = knownServers.stream().anyMatch(s -> s.equalsIgnoreCase(target));
            }
            if (!known) {
                p.sendMessage(ChatColor.RED + "Unknown server: " + ChatColor.WHITE + args[0]);
                p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/" + label + " <TAB>" + ChatColor.GRAY + " to see available servers.");
                return true;
            }
            sendToServer(p, target);
            return true;
        }
        p.sendMessage(ChatColor.RED + "Usage: /" + label + " <target>");
        return true;
    }

    /**
     * Query Supabase to find which server a player is on.
     * Queries koda_users by mc_username, gets owner's app_uuid,
     * then finds the server host from koda_servers.
     * Returns null if not found.
     */
    private String findPlayerServer(String playerName) {
        // Check sample players from ping data first
        for (Map.Entry<String, ServerStatus> entry : serverStatuses.entrySet()) {
            // Also check if the player is the owner!
            if (entry.getValue().owner != null && entry.getValue().owner.equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
            
            for (String sp : entry.getValue().samplePlayers) {
                if (sp.equalsIgnoreCase(playerName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            synchronized (knownServers) {
                for (String server : knownServers) {
                    if (server.toLowerCase().startsWith(partial)) completions.add(server);
                }
            }
            return completions;
        }
        return new ArrayList<>();
    }

    // ─── Events ─────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.setJoinMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "+" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY + p.getName());
        // Only set slot 4 and 0, do NOT clear inventory (DeluxeHub compatibility)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            p.getInventory().setItem(4, getNavigatorItem());
            p.getInventory().setItem(0, getMyServersItem());
        }, 5L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isNavigator(e.getItemDrop().getItemStack()) || isMyServersItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (isNavigator(e.getOffHandItem()) || isNavigator(e.getMainHandItem()) || isMyServersItem(e.getOffHandItem()) || isMyServersItem(e.getMainHandItem())) e.setCancelled(true);
    }
    private boolean hasPermission(Player p, String permKey) {
        String mcName = p.getName();
        Boolean isMain = isMainMap.get(mcName);
        if (Boolean.TRUE.equals(isMain)) return true; // Main account has all perms
        com.google.gson.JsonObject perms = playerPermissionsMap.get(mcName);
        if (perms != null && perms.has(permKey)) {
            return perms.get(permKey).getAsBoolean();
        }
        return false; // Default false if missing
    }

    private void sendNoPerm(Player p) {
        p.sendMessage(ChatColor.RED + "You do not have permission to do this!");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        // Lock navigator and myservers items
        if (isNavigator(e.getCurrentItem()) || isNavigator(e.getCursor()) || e.getSlot() == 4 ||
            isMyServersItem(e.getCurrentItem()) || isMyServersItem(e.getCursor()) || e.getSlot() == 0) {
            if (!e.getView().getTitle().equals(menuTitle)) {
                e.setCancelled(true);
            }
        }
        // Server GUI clicks
        if (e.getView().getTitle().equals(menuTitle)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();

            // Check if it's a custom item by slot
            for (CustomItem ci : customItems) {
                if (e.getSlot() == ci.slot) {
                    executeActions(p, ci.actions);
                    return;
                }
            }

            // Dynamic server click (GRASS_BLOCK)
            if (e.getCurrentItem().getType() == Material.GRASS_BLOCK) {
                String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                p.closeInventory();
                sendToServer(p, name);
            } else if (e.getCurrentItem().getType() == Material.NAME_TAG) {
                p.closeInventory();
                searchingPlayers.put(p.getUniqueId(), true);
                p.sendMessage(ChatColor.GOLD + "Please type the name of the server or player in chat:");
                p.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            }
        }
        
        // MyServers GUI clicks
        if (e.getView().getTitle().equals("Your Servers")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            if (e.getCurrentItem().getType() == Material.COMMAND_BLOCK || e.getCurrentItem().getType() == Material.RED_TERRACOTTA) {
                String serverName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                
                // Fetch the app_uuid of the player to check for 2FA before control panel
                String playerUuid = null;
                for (Map.Entry<String, String> ue : playerToAppUuidMap.entrySet()) {
                    if (ue.getKey().equalsIgnoreCase(p.getName())) {
                        playerUuid = ue.getValue();
                        break;
                    }
                }
                openControlPanelGui(p, serverName);
            }
        }
        
        // Control Panel clicks
        if (e.getView().getTitle().startsWith("Control Panel: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            String serverName = e.getView().getTitle().replace("Control Panel: ", "");
            
            Material type = e.getCurrentItem().getType();
            if (type == Material.LIME_DYE) {
                if (!hasPermission(p, "perm_start")) { sendNoPerm(p); return; }
                sendRemoteCommand(serverName, "START");
                p.sendMessage(ChatColor.GREEN + "Starting server " + serverName + "...");
                p.closeInventory();
            } else if (type == Material.RED_DYE) {
                if (!hasPermission(p, "perm_stop")) { sendNoPerm(p); return; }
                sendRemoteCommand(serverName, "STOP");
                p.sendMessage(ChatColor.RED + "Stopping server " + serverName + "...");
                p.closeInventory();
            } else if (type == Material.ORANGE_DYE) {
                if (!hasPermission(p, "perm_start") || !hasPermission(p, "perm_stop")) { sendNoPerm(p); return; }
                sendRemoteCommand(serverName, "RESTART");
                p.sendMessage(ChatColor.GOLD + "Restarting server " + serverName + "...");
                p.closeInventory();
            } else if (type == Material.PAPER) {
                if (!hasPermission(p, "perm_settings")) { sendNoPerm(p); return; }
                if (e.getCurrentItem().getItemMeta().getDisplayName().contains("ON")) {
                    sendRemoteCommand(serverName, "WHITELIST_ON");
                    p.sendMessage(ChatColor.YELLOW + "Whitelist enabled for " + serverName);
                } else {
                    sendRemoteCommand(serverName, "WHITELIST_OFF");
                    p.sendMessage(ChatColor.YELLOW + "Whitelist disabled for " + serverName);
                }
                p.closeInventory();
            } else if (type == Material.PLAYER_HEAD) {
                if (!hasPermission(p, "perm_players")) { sendNoPerm(p); return; }
                openPlayerListGui(p, serverName);
            } else if (type == Material.CHEST) {
                if (!hasPermission(p, "perm_plugins")) { sendNoPerm(p); return; }
                openPluginManagerGui(p, serverName);
            } else if (type == Material.COMMAND_BLOCK) {
                if (!hasPermission(p, "perm_console")) { sendNoPerm(p); return; }
                p.closeInventory();
                consoleInputServer.put(p.getUniqueId(), serverName);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Please type the command to send to " + serverName + "'s console:");
                p.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            } else if (type == Material.REPEATER) {
                if (!hasPermission(p, "perm_settings")) { sendNoPerm(p); return; }
                openServerSettingsGui(p, serverName);
            } else if (type == Material.COMPASS) {
                openMyServersGui(p);
            }
        }
        
        // Player List GUI clicks
        if (e.getView().getTitle().startsWith("Players: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            String serverName = e.getView().getTitle().replace("Players: ", "");
            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String targetPlayer = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                openPlayerActionsGui(p, serverName, targetPlayer);
            } else if (e.getCurrentItem().getType() == Material.COMPASS) {
                openControlPanelGui(p, serverName);
            }
        }

        // Player Actions GUI clicks
        if (e.getView().getTitle().startsWith("Action: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            // Title format: Action: <player>@<server>
            String rawTitle = e.getView().getTitle().replace("Action: ", "");
            String[] parts = rawTitle.split("@");
            if (parts.length != 2) return;
            String targetPlayer = parts[0];
            String serverName = parts[1];
            
            Material type = e.getCurrentItem().getType();
            if (type != Material.COMPASS && !hasPermission(p, "perm_players")) { sendNoPerm(p); return; }
            if (type == Material.IRON_BOOTS) {
                sendRemoteCommand(serverName, "EXEC_kick " + targetPlayer);
                p.sendMessage(ChatColor.GOLD + "Kicked " + targetPlayer + " from " + serverName);
                p.closeInventory();
            } else if (type == Material.IRON_SWORD) {
                sendRemoteCommand(serverName, "EXEC_ban " + targetPlayer);
                p.sendMessage(ChatColor.RED + "Banned " + targetPlayer + " on " + serverName);
                p.closeInventory();
            } else if (type == Material.BARRIER) {
                sendRemoteCommand(serverName, "EXEC_pardon " + targetPlayer);
                p.sendMessage(ChatColor.GREEN + "Unbanned " + targetPlayer + " on " + serverName);
                p.closeInventory();
            } else if (type == Material.DIAMOND_HELMET) {
                sendRemoteCommand(serverName, "EXEC_op " + targetPlayer);
                p.sendMessage(ChatColor.AQUA + "Gave OP to " + targetPlayer + " on " + serverName);
                p.closeInventory();
            } else if (type == Material.REDSTONE) {
                sendRemoteCommand(serverName, "EXEC_deop " + targetPlayer);
                p.sendMessage(ChatColor.YELLOW + "Removed OP from " + targetPlayer + " on " + serverName);
                p.closeInventory();
            } else if (type == Material.GRASS_BLOCK) {
                sendRemoteCommand(serverName, "EXEC_gamemode survival " + targetPlayer);
                p.sendMessage(ChatColor.GREEN + "Set " + targetPlayer + " to Survival on " + serverName);
                p.closeInventory();
            } else if (type == Material.COMMAND_BLOCK) {
                sendRemoteCommand(serverName, "EXEC_gamemode creative " + targetPlayer);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Set " + targetPlayer + " to Creative on " + serverName);
                p.closeInventory();
            } else if (type == Material.COMPASS) {
                openPlayerListGui(p, serverName);
            }
        }
        
        // Settings GUI clicks
        if (e.getView().getTitle().startsWith("Settings: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            String serverName = e.getView().getTitle().replace("Settings: ", "");
            Material type = e.getCurrentItem().getType();
            
            if (type == Material.SUNFLOWER) {
                sendRemoteCommand(serverName, "EXEC_time set day");
                p.sendMessage(ChatColor.YELLOW + "Set time to day on " + serverName);
            } else if (type == Material.CLOCK) {
                sendRemoteCommand(serverName, "EXEC_time set night");
                p.sendMessage(ChatColor.BLUE + "Set time to night on " + serverName);
            } else if (type == Material.GLASS) {
                sendRemoteCommand(serverName, "EXEC_weather clear");
                p.sendMessage(ChatColor.WHITE + "Set weather to clear on " + serverName);
            } else if (type == Material.WATER_BUCKET) {
                sendRemoteCommand(serverName, "EXEC_weather rain");
                p.sendMessage(ChatColor.BLUE + "Set weather to rain on " + serverName);
            } else if (type == Material.FEATHER) {
                sendRemoteCommand(serverName, "EXEC_difficulty peaceful");
                p.sendMessage(ChatColor.GREEN + "Set difficulty to peaceful on " + serverName);
            } else if (type == Material.IRON_SWORD) {
                sendRemoteCommand(serverName, "EXEC_difficulty hard");
                p.sendMessage(ChatColor.RED + "Set difficulty to hard on " + serverName);
            } else if (type == Material.COMMAND_BLOCK) {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Advanced server settings are available in the KodaHosting Android app.");
                p.sendMessage(ChatColor.GRAY + "Please open the app to manage settings like server version, RAM allocation, etc.");
            } else if (type == Material.COMPASS) {
                openControlPanelGui(p, serverName);
            }
        }

        // Plugin Manager GUI clicks
        if (e.getView().getTitle().startsWith("Plugins: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            String serverName = e.getView().getTitle().replace("Plugins: ", "");
            
            Material type = e.getCurrentItem().getType();
            if (type != Material.COMPASS && !hasPermission(p, "perm_plugins")) { sendNoPerm(p); return; }

            if (type == Material.NAME_TAG) {
                // Search button clicked
                p.closeInventory();
                pluginSearchServer.put(p.getUniqueId(), serverName);
                p.sendMessage(ChatColor.GOLD + "\u2500\u2500\u2500\u2500\u2500\u2500 Plugin Search \u2500\u2500\u2500\u2500\u2500\u2500");
                p.sendMessage(ChatColor.YELLOW + "Type the name of the plugin you want to search for:");
                p.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            } else if (type == Material.COMPASS) {
                openControlPanelGui(p, serverName);
            } else if (e.getCurrentItem().hasItemMeta() && e.getCurrentItem().getItemMeta().getLore() != null) {
                // Check if item has a modrinth ID stored in lore
                List<String> lore = e.getCurrentItem().getItemMeta().getLore();
                String modrinthId = null;
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line);
                    if (stripped.startsWith("ID: ")) {
                        modrinthId = stripped.substring(4);
                        break;
                    }
                }
                if (modrinthId != null) {
                    String pluginName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                    if (e.isRightClick()) {
                        NamespacedKey descKey = new NamespacedKey(this, "plugin_desc");
                        if (e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(descKey, PersistentDataType.STRING)) {
                            String fullDesc = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(descKey, PersistentDataType.STRING);
                            p.sendMessage(ChatColor.GOLD + "\u2500\u2500\u2500\u2500\u2500\u2500 Plugin Info: " + pluginName + " \u2500\u2500\u2500\u2500\u2500\u2500");
                            p.sendMessage(ChatColor.GRAY + fullDesc);
                        } else {
                            p.sendMessage(ChatColor.RED + "No detailed description available.");
                        }
                    } else {
                        p.sendMessage(ChatColor.GREEN + "Installing " + pluginName + " on " + serverName + "...");
                        p.sendMessage(ChatColor.GRAY + "The server will download the plugin. Restart to activate.");
                        sendRemoteCommand(serverName, "INSTALL_PLUGIN_" + modrinthId);
                        p.closeInventory();
                    }
                }
            }
        }

        // Plugin Search Results GUI clicks
        if (e.getView().getTitle().startsWith("Search: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Player p = (Player) e.getWhoClicked();
            
            if (e.getCurrentItem().getType() == Material.COMPASS) {
                // Get server name from the search state or just go back
                String srv = pluginSearchServer.get(p.getUniqueId());
                if (srv != null) {
                    openPluginManagerGui(p, srv);
                } else {
                    p.closeInventory();
                }
                return;
            }
            
            if (e.getCurrentItem().hasItemMeta() && e.getCurrentItem().getItemMeta().getLore() != null) {
                List<String> lore = e.getCurrentItem().getItemMeta().getLore();
                String modrinthId = null;
                String serverName = null;
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line);
                    if (stripped.startsWith("ID: ")) {
                        modrinthId = stripped.substring(4);
                    }
                    if (stripped.startsWith("Server: ")) {
                        serverName = stripped.substring(8);
                    }
                }
                if (modrinthId != null && serverName != null) {
                    String pluginName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                    if (e.isRightClick()) {
                        NamespacedKey descKey = new NamespacedKey(this, "plugin_desc");
                        if (e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(descKey, PersistentDataType.STRING)) {
                            String fullDesc = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(descKey, PersistentDataType.STRING);
                            p.sendMessage(ChatColor.GOLD + "\u2500\u2500\u2500\u2500\u2500\u2500 Plugin Info: " + pluginName + " \u2500\u2500\u2500\u2500\u2500\u2500");
                            p.sendMessage(ChatColor.GRAY + fullDesc);
                        } else {
                            p.sendMessage(ChatColor.RED + "No detailed description available.");
                        }
                    } else {
                        p.sendMessage(ChatColor.GREEN + "Installing " + pluginName + " on " + serverName + "...");
                        p.sendMessage(ChatColor.GRAY + "The server will download the plugin. Restart to activate.");
                        sendRemoteCommand(serverName, "INSTALL_PLUGIN_" + modrinthId);
                        p.closeInventory();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        if (pending2faMap.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String expected = pending2faMap.get(e.getPlayer().getUniqueId());
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("cancel")) {
                pending2faMap.remove(e.getPlayer().getUniqueId());
                e.getPlayer().sendMessage(ChatColor.YELLOW + "2FA cancelled.");
                return;
            }
            if (query.equals(expected)) {
                pending2faMap.remove(e.getPlayer().getUniqueId());
                e.getPlayer().sendMessage(ChatColor.GREEN + "2FA successful!");
                Bukkit.getScheduler().runTask(this, () -> openMyServersGui(e.getPlayer()));
            } else {
                e.getPlayer().sendMessage(ChatColor.RED + "Incorrect 2FA password. Try again or type 'cancel'.");
            }
            return;
        }
        
        // Console command handler
        if (consoleInputServer.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String serverName = consoleInputServer.remove(e.getPlayer().getUniqueId());
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("cancel")) {
                e.getPlayer().sendMessage(ChatColor.YELLOW + "Console input cancelled.");
                return;
            }
            e.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE + "Command sent to " + serverName + ": /" + query);
            sendRemoteCommand(serverName, "EXEC_" + query);
            return;
        }

        // Plugin search handler
        if (pluginSearchServer.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String serverName = pluginSearchServer.remove(e.getPlayer().getUniqueId());
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("cancel")) {
                e.getPlayer().sendMessage(ChatColor.YELLOW + "Plugin search cancelled.");
                return;
            }
            e.getPlayer().sendMessage(ChatColor.YELLOW + "Searching Modrinth for '" + query + "'...");
            // Perform Modrinth search asynchronously
            final String searchQuery = query;
            try {
                String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
                String facets = java.net.URLEncoder.encode("[[\"project_type:plugin\"],[\"categories:paper\"]]", "UTF-8");
                URL url = new URL("https://api.modrinth.com/v2/search?query=" + encodedQuery + "&facets=" + facets + "&limit=21");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                        int c; while ((c = reader.read()) != -1) sb.append((char) c);
                    }
                    com.google.gson.JsonObject res = com.google.gson.JsonParser.parseString(sb.toString()).getAsJsonObject();
                    com.google.gson.JsonArray hits = res.getAsJsonArray("hits");
                    
                    List<String[]> results = new ArrayList<>(); // [title, description, projectId]
                    for (com.google.gson.JsonElement el : hits) {
                        com.google.gson.JsonObject hit = el.getAsJsonObject();
                        String title = hit.get("title").getAsString();
                        String desc = hit.get("description").getAsString();
                        String projectId = hit.get("project_id").getAsString();
                        results.add(new String[]{title, desc, projectId});
                    }
                    
                    Bukkit.getScheduler().runTask(this, () -> openPluginSearchResultsGui(e.getPlayer(), serverName, searchQuery, results));
                } else {
                    int respCode = conn.getResponseCode();
                    Bukkit.getScheduler().runTask(this, () ->
                        e.getPlayer().sendMessage(ChatColor.RED + "Search failed: HTTP " + respCode));
                }
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(this, () ->
                    e.getPlayer().sendMessage(ChatColor.RED + "Search error: " + ex.getMessage()));
            }
            return;
        }

        if (searchingPlayers.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            searchingPlayers.remove(e.getPlayer().getUniqueId());
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("cancel")) {
                e.getPlayer().sendMessage(ChatColor.YELLOW + "Search cancelled.");
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> e.getPlayer().performCommand("join " + query));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isNavigator(e.getItem())) {
                openServerGui(e.getPlayer());
                e.setCancelled(true);
            }
            if (isMyServersItem(e.getItem())) {
                e.getPlayer().performCommand("myservers");
                e.setCancelled(true);
            }
        }
    }

    // ─── GUI ────────────────────────────────────────────────────────

    private void openServerGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, menuTitle);
        updateServerGui(inv, p);
        p.openInventory(inv);
    }

    private void updateServerGui(Inventory inv, Player p) {
        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        // Orange & Magenta glass pane border
        ItemStack border1 = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta bm1 = border1.getItemMeta(); bm1.setDisplayName(" "); border1.setItemMeta(bm1);

        ItemStack border2 = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta bm2 = border2.getItemMeta(); bm2.setDisplayName(" "); border2.setItemMeta(bm2);

        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? border1 : border2);
        for (int i = 45; i < 54; i++) inv.setItem(i, i % 2 == 0 ? border1 : border2);

        // Search item in top row center
        ItemStack search = new ItemStack(Material.NAME_TAG);
        ItemMeta searchMeta = search.getItemMeta();
        searchMeta.setDisplayName(ChatColor.GOLD + "Search Server / Player");
        List<String> sLore = new ArrayList<>();
        sLore.add(ChatColor.GRAY + "Click to search by name.");
        searchMeta.setLore(sLore);
        search.setItemMeta(searchMeta);
        inv.setItem(4, search);

        // Place custom items from config
        for (CustomItem ci : customItems) {
            ItemStack item = new ItemStack(ci.material);
            ItemMeta meta = item.getItemMeta();
            String dn = ci.displayName;
            if (papi) dn = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, dn);
            meta.setDisplayName(dn);
            List<String> rLore = new ArrayList<>();
            for (String l : ci.lore) {
                if (papi) l = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, l);
                rLore.add(l);
            }
            meta.setLore(rLore);
            for (ItemFlag flag : ci.itemFlags) {
                meta.addItemFlags(flag);
            }
            item.setItemMeta(meta);
            if (ci.slot >= 0 && ci.slot < 54) {
                inv.setItem(ci.slot, item);
            }
        }

        // Clear dynamic slots before refilling
        for (int i = 19; i < 44; i++) {
            if (i != 26 && i != 27 && i != 35 && i != 36) {
                // Check if a custom item is already in this slot
                boolean isCustom = false;
                for (CustomItem ci : customItems) if (ci.slot == i) isCustom = true;
                if (!isCustom) inv.setItem(i, null);
            }
        }

        // Place dynamic KodaHosting servers in the middle rows, skipping occupied slots
        int slot = 19; // Start at row 3
        synchronized (knownServers) {
            for (String server : knownServers) {
                // Find next empty slot
                while (slot < 44 && inv.getItem(slot) != null) slot++;
                if (slot >= 44) break;
                if (slot == 26) slot = 28;
                if (slot == 35) slot = 37;
                while (slot < 44 && inv.getItem(slot) != null) slot++;
                if (slot >= 44) break;

                ServerStatus status = serverStatuses.get(server);
                ItemStack item;
                if (status != null && status.online) {
                    item = new ItemStack(Material.GRASS_BLOCK);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(ChatColor.GREEN + server);
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.GRAY + "Owner: " + ChatColor.AQUA + status.owner);
                    lore.add(ChatColor.GRAY + "Version: " + ChatColor.WHITE + status.version);
                    lore.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + status.players + "/" + status.maxPlayers);
                    if (!status.samplePlayers.isEmpty()) {
                        lore.add("");
                        lore.add(ChatColor.GRAY + "Online:");
                        for (String sp : status.samplePlayers) {
                            lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + sp);
                        }
                    }
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Click to connect");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                } else {
                    item = new ItemStack(Material.RED_TERRACOTTA);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(ChatColor.RED + server);
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.DARK_GRAY + "Server offline");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot++, item);
            }
        }
    }

    // ─── Custom Item Actions ──────────────────────────────────────────

    private void executeActions(Player p, List<String> actions) {
        for (String action : actions) {
            String act = ChatColor.translateAlternateColorCodes('&', action);
            if (act.startsWith("[CLOSE]")) {
                p.closeInventory();
            } else if (act.startsWith("[MESSAGE]")) {
                String msg = act.substring("[MESSAGE]".length()).trim();
                p.sendMessage(msg);
            } else if (act.startsWith("[SERVER]")) {
                String srv = act.substring("[SERVER]".length()).trim();
                p.closeInventory();
                sendToServer(p, srv);
            } else if (act.startsWith("[CONSOLE]")) {
                String cmd = act.substring("[CONSOLE]".length()).trim()
                    .replace("%player%", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else if (act.startsWith("[PROXY]")) {
                String cmd = act.substring("[PROXY]".length()).trim()
                    .replace("%player%", p.getName());
                p.performCommand(cmd);
            } else if (act.startsWith("[PLAYER]")) {
                String cmd = act.substring("[PLAYER]".length()).trim()
                    .replace("%player%", p.getName());
                p.performCommand(cmd);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────
    
    private void openMyServersGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "Your Servers");
        int slot = 0;
        // Find the player's app_uuid from userUuidMap
        String playerUuid = null;
        for (Map.Entry<String, String> ue : userUuidMap.entrySet()) {
            if (ue.getValue() != null && ue.getValue().equalsIgnoreCase(p.getName())) {
                playerUuid = ue.getKey();
                break;
            }
        }
        for (Map.Entry<String, ServerStatus> entry : serverStatuses.entrySet()) {
            boolean isOwner = entry.getValue().owner != null && !entry.getValue().owner.isEmpty()
                && !entry.getValue().owner.equals("Unknown")
                && entry.getValue().owner.equalsIgnoreCase(p.getName());
            if (!isOwner && playerUuid != null && !playerUuid.isEmpty()
                && entry.getValue().ownerUuid != null && !entry.getValue().ownerUuid.isEmpty()) {
                isOwner = entry.getValue().ownerUuid.equals(playerUuid);
            }
            if (isOwner) {
                ServerStatus status = entry.getValue();
                ItemStack item = new ItemStack(status.online ? Material.COMMAND_BLOCK : Material.RED_TERRACOTTA);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.AQUA + entry.getKey());
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GRAY + "Status: " + (status.online ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
                if (status.online) {
                    lore.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + status.players + "/" + status.maxPlayers);
                }
                lore.add("");
                lore.add(ChatColor.YELLOW + "Click to manage");
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(slot++, item);
            }
        }
        if (slot == 0) {
            if (playerUuid == null) {
                p.sendMessage(ChatColor.RED + "Your Minecraft account is not linked to the app or you are not logged in!");
                p.sendMessage(ChatColor.YELLOW + "Generate a code in the app settings and type /link <code>");
            } else {
                p.sendMessage(ChatColor.RED + "You don't own any servers yet.");
                p.sendMessage(ChatColor.YELLOW + "Create one in the KodaHosting app!");
            }
            return;
        }
        p.openInventory(inv);
    }
    
    private void openControlPanelGui(Player p, String serverName) {
        Inventory inv = Bukkit.createInventory(null, 36, "Control Panel: " + serverName);
        
        ItemStack start = new ItemStack(Material.LIME_DYE);
        ItemMeta startM = start.getItemMeta();
        startM.setDisplayName(ChatColor.GREEN + "Start Server");
        List<String> startLore = new ArrayList<>();
        startLore.add(ChatColor.GRAY + "Start your server");
        startM.setLore(startLore);
        start.setItemMeta(startM);
        inv.setItem(10, start);
        
        ItemStack stop = new ItemStack(Material.RED_DYE);
        ItemMeta stopM = stop.getItemMeta();
        stopM.setDisplayName(ChatColor.RED + "Stop Server");
        List<String> stopLore = new ArrayList<>();
        stopLore.add(ChatColor.GRAY + "Gracefully stop your server");
        stopM.setLore(stopLore);
        stop.setItemMeta(stopM);
        inv.setItem(12, stop);
        
        ItemStack restart = new ItemStack(Material.ORANGE_DYE);
        ItemMeta restartM = restart.getItemMeta();
        restartM.setDisplayName(ChatColor.GOLD + "Restart Server");
        List<String> restartLore = new ArrayList<>();
        restartLore.add(ChatColor.GRAY + "Stop and restart your server");
        restartM.setLore(restartLore);
        restart.setItemMeta(restartM);
        inv.setItem(14, restart);
        
        ItemStack wl = new ItemStack(Material.PAPER);
        ItemMeta wlM = wl.getItemMeta();
        wlM.setDisplayName(ChatColor.YELLOW + "Whitelist ON");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to enable whitelist");
        wlM.setLore(lore);
        wl.setItemMeta(wlM);
        inv.setItem(16, wl);

        ItemStack wlOff = new ItemStack(Material.PAPER);
        ItemMeta wlOffM = wlOff.getItemMeta();
        wlOffM.setDisplayName(ChatColor.YELLOW + "Whitelist OFF");
        List<String> loreOff = new ArrayList<>();
        loreOff.add(ChatColor.GRAY + "Click to disable whitelist");
        wlOffM.setLore(loreOff);
        wlOff.setItemMeta(wlOffM);
        inv.setItem(34, wlOff);
        
        ItemStack players = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playersM = players.getItemMeta();
        playersM.setDisplayName(ChatColor.AQUA + "Manage Players");
        List<String> pLore = new ArrayList<>();
        pLore.add(ChatColor.GRAY + "View and manage");
        pLore.add(ChatColor.GRAY + "online players");
        playersM.setLore(pLore);
        players.setItemMeta(playersM);
        inv.setItem(20, players);
        
        // Plugin Manager button
        ItemStack plugins = new ItemStack(Material.CHEST);
        ItemMeta pluginsM = plugins.getItemMeta();
        pluginsM.setDisplayName(ChatColor.GREEN + "Manage Plugins");
        List<String> plLore = new ArrayList<>();
        plLore.add(ChatColor.GRAY + "Search & install plugins");
        plLore.add(ChatColor.GRAY + "from Modrinth");
        pluginsM.setLore(plLore);
        plugins.setItemMeta(pluginsM);
        inv.setItem(24, plugins);
        
        ItemStack console = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta consoleM = console.getItemMeta();
        consoleM.setDisplayName(ChatColor.LIGHT_PURPLE + "Send Console Command");
        List<String> cLore = new ArrayList<>();
        cLore.add(ChatColor.GRAY + "Send a command to the");
        cLore.add(ChatColor.GRAY + "server console");
        consoleM.setLore(cLore);
        console.setItemMeta(consoleM);
        inv.setItem(22, console);
        
        ItemStack settings = new ItemStack(Material.REPEATER);
        ItemMeta settingsM = settings.getItemMeta();
        settingsM.setDisplayName(ChatColor.GOLD + "Server Settings");
        List<String> sLore = new ArrayList<>();
        sLore.add(ChatColor.GRAY + "Advanced server settings");
        settingsM.setLore(sLore);
        settings.setItemMeta(settingsM);
        inv.setItem(26, settings);
        
        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backM);
        inv.setItem(31, back);
        
        p.openInventory(inv);
    }

    private void openServerSettingsGui(Player p, String serverName) {
        Inventory inv = Bukkit.createInventory(null, 27, "Settings: " + serverName);

        ItemStack timeDay = new ItemStack(Material.SUNFLOWER);
        ItemMeta timeDayM = timeDay.getItemMeta();
        timeDayM.setDisplayName(ChatColor.YELLOW + "Set Time: Day");
        timeDay.setItemMeta(timeDayM);
        inv.setItem(10, timeDay);

        ItemStack timeNight = new ItemStack(Material.CLOCK);
        ItemMeta timeNightM = timeNight.getItemMeta();
        timeNightM.setDisplayName(ChatColor.BLUE + "Set Time: Night");
        timeNight.setItemMeta(timeNightM);
        inv.setItem(11, timeNight);

        ItemStack weatherClear = new ItemStack(Material.GLASS);
        ItemMeta weatherClearM = weatherClear.getItemMeta();
        weatherClearM.setDisplayName(ChatColor.WHITE + "Weather: Clear");
        weatherClear.setItemMeta(weatherClearM);
        inv.setItem(13, weatherClear);

        ItemStack weatherRain = new ItemStack(Material.WATER_BUCKET);
        ItemMeta weatherRainM = weatherRain.getItemMeta();
        weatherRainM.setDisplayName(ChatColor.BLUE + "Weather: Rain");
        weatherRain.setItemMeta(weatherRainM);
        inv.setItem(14, weatherRain);

        ItemStack difficultyPeaceful = new ItemStack(Material.FEATHER);
        ItemMeta difficultyPeacefulM = difficultyPeaceful.getItemMeta();
        difficultyPeacefulM.setDisplayName(ChatColor.GREEN + "Difficulty: Peaceful");
        difficultyPeaceful.setItemMeta(difficultyPeacefulM);
        inv.setItem(16, difficultyPeaceful);

        ItemStack difficultyHard = new ItemStack(Material.IRON_SWORD);
        ItemMeta difficultyHardM = difficultyHard.getItemMeta();
        difficultyHardM.setDisplayName(ChatColor.RED + "Difficulty: Hard");
        difficultyHard.setItemMeta(difficultyHardM);
        inv.setItem(17, difficultyHard);
        
        ItemStack appSettings = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta appSettingsM = appSettings.getItemMeta();
        appSettingsM.setDisplayName(ChatColor.LIGHT_PURPLE + "Advanced Settings");
        List<String> aLore = new ArrayList<>();
        aLore.add(ChatColor.GRAY + "Open KodaHosting App for:");
        aLore.add(ChatColor.GRAY + "• RAM Allocation");
        aLore.add(ChatColor.GRAY + "• Server Version");
        aLore.add(ChatColor.GRAY + "• Delete / Hibernate");
        appSettingsM.setLore(aLore);
        appSettings.setItemMeta(appSettingsM);
        inv.setItem(22, appSettings);

        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backM);
        inv.setItem(26, back);

        p.openInventory(inv);
    }

    private void openPlayerListGui(Player p, String serverName) {
        Inventory inv = Bukkit.createInventory(null, 54, "Players: " + serverName);
        ServerStatus status = serverStatuses.get(serverName);
        int slot = 0;
        if (status != null && status.online) {
            for (String sp : status.samplePlayers) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                meta.setDisplayName(ChatColor.WHITE + sp);
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(sp));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.YELLOW + "Click to manage player");
                meta.setLore(lore);
                head.setItemMeta(meta);
                inv.setItem(slot++, head);
            }
        }
        
        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backM);
        inv.setItem(53, back);
        
        p.openInventory(inv);
    }
    
    private void openPlayerActionsGui(Player p, String serverName, String targetPlayer) {
        Inventory inv = Bukkit.createInventory(null, 36, "Action: " + targetPlayer + "@" + serverName);
        
        // Row 2: Moderation
        ItemStack kick = new ItemStack(Material.IRON_BOOTS);
        ItemMeta kickM = kick.getItemMeta();
        kickM.setDisplayName(ChatColor.GOLD + "Kick Player");
        List<String> kickLore = new ArrayList<>();
        kickLore.add(ChatColor.GRAY + "Remove from server");
        kickM.setLore(kickLore);
        kick.setItemMeta(kickM);
        inv.setItem(10, kick);
        
        ItemStack ban = new ItemStack(Material.IRON_SWORD);
        ItemMeta banM = ban.getItemMeta();
        banM.setDisplayName(ChatColor.RED + "Ban Player");
        List<String> banLore = new ArrayList<>();
        banLore.add(ChatColor.GRAY + "Permanently ban");
        banM.setLore(banLore);
        ban.setItemMeta(banM);
        inv.setItem(11, ban);
        
        ItemStack unban = new ItemStack(Material.BARRIER);
        ItemMeta unbanM = unban.getItemMeta();
        unbanM.setDisplayName(ChatColor.GREEN + "Unban Player");
        List<String> unbanLore = new ArrayList<>();
        unbanLore.add(ChatColor.GRAY + "Remove ban (pardon)");
        unbanM.setLore(unbanLore);
        unban.setItemMeta(unbanM);
        inv.setItem(12, unban);
        
        // Row 2: Permissions
        ItemStack op = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta opM = op.getItemMeta();
        opM.setDisplayName(ChatColor.AQUA + "OP Player");
        List<String> opLore = new ArrayList<>();
        opLore.add(ChatColor.GRAY + "Give operator permissions");
        opM.setLore(opLore);
        op.setItemMeta(opM);
        inv.setItem(14, op);
        
        ItemStack deop = new ItemStack(Material.REDSTONE);
        ItemMeta deopM = deop.getItemMeta();
        deopM.setDisplayName(ChatColor.YELLOW + "De-OP Player");
        List<String> deopLore = new ArrayList<>();
        deopLore.add(ChatColor.GRAY + "Remove operator permissions");
        deopM.setLore(deopLore);
        deop.setItemMeta(deopM);
        inv.setItem(15, deop);
        
        // Row 3: Gamemode
        ItemStack survival = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta survM = survival.getItemMeta();
        survM.setDisplayName(ChatColor.GREEN + "Gamemode Survival");
        List<String> survLore = new ArrayList<>();
        survLore.add(ChatColor.GRAY + "Set player to survival");
        survM.setLore(survLore);
        survival.setItemMeta(survM);
        inv.setItem(21, survival);
        
        ItemStack creative = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta creatM = creative.getItemMeta();
        creatM.setDisplayName(ChatColor.LIGHT_PURPLE + "Gamemode Creative");
        List<String> creatLore = new ArrayList<>();
        creatLore.add(ChatColor.GRAY + "Set player to creative");
        creatM.setLore(creatLore);
        creative.setItemMeta(creatM);
        inv.setItem(23, creative);
        
        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backM);
        inv.setItem(31, back);
        
        p.openInventory(inv);
    }

    private void openPluginManagerGui(Player p, String serverName) {
        Inventory inv = Bukkit.createInventory(null, 54, "Plugins: " + serverName);
        
        // Search button at top center
        ItemStack search = new ItemStack(Material.NAME_TAG);
        ItemMeta searchMeta = search.getItemMeta();
        searchMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Search Plugins");
        List<String> searchLore = new ArrayList<>();
        searchLore.add("");
        searchLore.add(ChatColor.GRAY + "Search Modrinth for any plugin");
        searchLore.add(ChatColor.YELLOW + "Click to search!");
        searchMeta.setLore(searchLore);
        search.setItemMeta(searchMeta);
        inv.setItem(4, search);
        
        // Suggested plugins
        String[][] suggested = {
            {"EssentialsX", "Core commands, homes, warps, kits & economy", "fRQlnZcg"},
            {"WorldEdit", "In-game map editor for building & terrain", "1u6JkXh5"},
            {"Vault", "Economy & permissions framework", "w7ThoJFB"},
            {"WorldGuard", "Region protection & flags", "Bq2fR68s"},
            {"GriefPrevention", "Claim-based land protection", "FXjrXGKj"},
            {"Multiverse-Core", "Multiple world management", "3wmN97b8"},
            {"ViaVersion", "Cross-version player support", "P1OZGk5p"},
            {"CoreProtect", "Block logging & rollback tool", "Lu3KuzdV"},
            {"LuckPerms", "Advanced permissions system", "Vebnzrzj"},
            {"Chunky", "Fast world pre-generation", "fALzjamp"},
            {"spark", "Performance profiler & TPS monitor", "l6YH9Als"},
            {"Citizens", "NPC creation & management", "2FKOM3Ms"},
        };
        
        Material[] mats = {
            Material.DIAMOND, Material.GOLDEN_AXE, Material.GOLD_INGOT,
            Material.IRON_CHESTPLATE, Material.OAK_FENCE, Material.ENDER_PEARL,
            Material.SPYGLASS, Material.BOOK, Material.EMERALD,
            Material.GRASS_BLOCK, Material.CAMPFIRE, Material.ARMOR_STAND
        };
        
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        
        for (int i = 0; i < suggested.length && i < slots.length; i++) {
            ItemStack item = new ItemStack(mats[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + suggested[i][0]);
            List<String> lore = new ArrayList<>();
            lore.add("");
            // Word-wrap description
            String desc = suggested[i][1];
            if (desc.length() > 40) {
                int splitAt = desc.lastIndexOf(' ', 40);
                if (splitAt == -1) splitAt = 40;
                lore.add(ChatColor.GRAY + desc.substring(0, splitAt));
                lore.add(ChatColor.GRAY + desc.substring(splitAt).trim());
            } else {
                lore.add(ChatColor.GRAY + desc);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-Click: Install");
            lore.add(ChatColor.YELLOW + "Right-Click: Read full description");
            lore.add(ChatColor.DARK_GRAY + "ID: " + suggested[i][2]);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            
            NamespacedKey descKey = new NamespacedKey(this, "plugin_desc");
            meta.getPersistentDataContainer().set(descKey, PersistentDataType.STRING, desc);
            
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
        
        // Back button
        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backM);
        inv.setItem(49, back);
        
        p.openInventory(inv);
    }
    
    private void openPluginSearchResultsGui(Player p, String serverName, String query, List<String[]> results) {
        String title = "Search: " + (query.length() > 20 ? query.substring(0, 20) : query);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        // Info item
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoM = info.getItemMeta();
        infoM.setDisplayName(ChatColor.GOLD + "Results for: " + ChatColor.WHITE + query);
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "" + results.size() + " plugins found");
        infoLore.add(ChatColor.GRAY + "Server: " + serverName);
        infoM.setLore(infoLore);
        info.setItemMeta(infoM);
        inv.setItem(4, info);
        
        // Category-based materials for results
        Material[] resultMats = {
            Material.CHEST, Material.COMPARATOR, Material.GOLD_INGOT,
            Material.IRON_CHESTPLATE, Material.GRASS_BLOCK, Material.BOOKSHELF,
            Material.BREWING_STAND, Material.ANVIL, Material.BEACON,
            Material.ENCHANTING_TABLE, Material.HOPPER, Material.DROPPER,
            Material.DISPENSER, Material.OBSERVER, Material.PISTON,
            Material.DAYLIGHT_DETECTOR, Material.TNT, Material.BELL,
            Material.LODESTONE, Material.RESPAWN_ANCHOR, Material.TARGET
        };
        
        int slot = 9;
        for (int i = 0; i < results.size() && slot < 45; i++) {
            String[] result = results.get(i);
            String pTitle = result[0];
            String pDesc = result[1];
            String pId = result[2];
            
            ItemStack item = new ItemStack(resultMats[i % resultMats.length]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + pTitle);
            List<String> lore = new ArrayList<>();
            lore.add("");
            // Word-wrap description at ~38 chars
            if (pDesc.length() > 38) {
                int line1End = pDesc.lastIndexOf(' ', 38);
                if (line1End == -1) line1End = 38;
                lore.add(ChatColor.GRAY + pDesc.substring(0, line1End));
                String rest = pDesc.substring(line1End).trim();
                if (rest.length() > 38) {
                    int line2End = rest.lastIndexOf(' ', 38);
                    if (line2End == -1) line2End = 38;
                    lore.add(ChatColor.GRAY + rest.substring(0, line2End));
                    if (rest.length() > line2End) {
                        lore.add(ChatColor.GRAY + rest.substring(line2End).trim());
                    }
                } else {
                    lore.add(ChatColor.GRAY + rest);
                }
            } else {
                lore.add(ChatColor.GRAY + pDesc);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-Click: Install");
            lore.add(ChatColor.YELLOW + "Right-Click: Read full description");
            lore.add(ChatColor.DARK_GRAY + "ID: " + pId);
            lore.add(ChatColor.DARK_GRAY + "Server: " + serverName);
            
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            
            NamespacedKey descKey = new NamespacedKey(this, "plugin_desc");
            meta.getPersistentDataContainer().set(descKey, PersistentDataType.STRING, pDesc);
            
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        
        if (results.isEmpty()) {
            ItemStack noResults = new ItemStack(Material.BARRIER);
            ItemMeta nrM = noResults.getItemMeta();
            nrM.setDisplayName(ChatColor.RED + "No plugins found");
            List<String> nrLore = new ArrayList<>();
            nrLore.add(ChatColor.GRAY + "Try a different search query");
            nrM.setLore(nrLore);
            noResults.setItemMeta(nrM);
            inv.setItem(22, noResults);
        }
        
        // Back button
        ItemStack back = new ItemStack(Material.COMPASS);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.GRAY + "Back to Plugins");
        back.setItemMeta(backM);
        inv.setItem(49, back);
        
        p.openInventory(inv);
    }

    private void sendRemoteCommand(String host, String cmd) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String jsonBody = "{\"server_version\": \"CMD:" + cmd + "\"}";
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?host=eq." + host))
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .header("apikey", eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey())
                    .header("Authorization", "Bearer " + eu.kodanetwork.lobby.security.PraetorSecurity.getSupabaseKey())
                    .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
                client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                getLogger().warning("Failed to send remote command: " + e.getMessage());
            }
        });
    }

    private boolean isNavigator(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
            && item.getItemMeta().getDisplayName().contains("KodaNetwork");
    }

    private boolean isMyServersItem(ItemStack item) {
        return item != null && item.getType() == Material.COMMAND_BLOCK && item.hasItemMeta()
            && item.getItemMeta().getDisplayName().contains("My Servers");
    }

    private ItemStack getNavigatorItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "KodaNetwork " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "Right-Click");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getMyServersItem() {
        ItemStack item = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "My Servers " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "Right-Click");
        item.setItemMeta(meta);
        return item;
    }

    private void sendToServer(Player player, String serverName) {
        player.sendMessage(ChatColor.GREEN + "Connecting to " + ChatColor.WHITE + serverName + ChatColor.GREEN + "...");
        try {
            String baseDomain = "kodanetwork.eu";
            ServerStatus status = serverStatuses.get(serverName);
            if (status != null && status.baseDomain != null && !status.baseDomain.isEmpty()) {
                baseDomain = status.baseDomain;
            }
            String host = serverName + "." + baseDomain;
            player.transfer(host, 25565);
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Transfer failed. Your client may not support transfers (requires 1.20.5+).");
        }
    }
}
