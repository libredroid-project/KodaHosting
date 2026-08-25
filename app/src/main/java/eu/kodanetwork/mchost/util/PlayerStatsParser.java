package eu.kodanetwork.mchost.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class PlayerStatsParser {
    public static String getWorldName(File serverDir) {
        File props = new File(serverDir, "server.properties");
        if (props.exists()) {
            try {
                java.util.Scanner sc = new java.util.Scanner(props);
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.startsWith("level-name=")) {
                        sc.close();
                        return line.substring("level-name=".length()).trim();
                    }
                }
                sc.close();
            } catch (Exception e) {}
        }
        return "world";
    }

    public static java.io.File getPlayerDataFile(java.io.File serverDir, String uuid) {
        String world = getWorldName(serverDir);
        java.io.File f1 = new java.io.File(serverDir, world + "/players/data/" + uuid + ".dat");
        if (f1.exists()) return f1;
        java.io.File f2 = new java.io.File(serverDir, world + "/playerdata/" + uuid + ".dat");
        return f2.exists() ? f2 : f1;
    }

    public static java.io.File getStatsFile(java.io.File serverDir, String uuid) {
        String world = getWorldName(serverDir);
        java.io.File f1 = new java.io.File(serverDir, world + "/players/stats/" + uuid + ".json");
        if (f1.exists()) return f1;
        java.io.File f2 = new java.io.File(serverDir, world + "/stats/" + uuid + ".json");
        return f2.exists() ? f2 : f1;
    }

    public static class PlayerStats {
        public long hoursPlayed = 0;
        public long deaths = 0;
        public long mobsKilled = 0;
        public long damageTaken = 0;
        public long blocksMined = 0;
    }

    public static String getUuidFromName(File serverDir, String playerName) {
        java.util.List<String> candidates = new java.util.ArrayList<>();

        // 1. usercache.json — robust against malformed entries
        File cacheFile = new File(serverDir, "usercache.json");
        if (cacheFile.exists()) {
            try {
                byte[] bytes = new byte[(int) cacheFile.length()];
                try (FileInputStream fis = new FileInputStream(cacheFile)) {
                    fis.read(bytes);
                }
                JSONArray arr = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String name = obj.optString("name", "").trim();
                    String uuid = obj.optString("uuid", "").trim();
                    if (!name.isEmpty() && !uuid.isEmpty() && name.equalsIgnoreCase(playerName)) {
                        candidates.add(uuid);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Online UUID via Mojang (offline-mode servers store the offline UUID,
        //    so this is only a candidate — the file check below picks the right one)
        try {
            java.net.URL url = new java.net.URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() == 200) {
                java.io.InputStream in = conn.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\A");
                String json = scanner.hasNext() ? scanner.next() : "";
                in.close();
                org.json.JSONObject obj = new org.json.JSONObject(json);
                String id = obj.getString("id");
                candidates.add(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Deterministic offline UUID
        candidates.add(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString());

        // Prefer whichever candidate actually has playerdata or stats files
        for (String uuid : candidates) {
            if (getPlayerDataFile(serverDir, uuid).exists() || getStatsFile(serverDir, uuid).exists()) {
                return uuid;
            }
        }
        return candidates.get(candidates.size() - 1);
    }


    public static java.io.File getLiveStatsFile(java.io.File serverDir, String uuid) {
        String world = getWorldName(serverDir);
        // Look in the custom plugin folder first
        java.io.File pluginFile = new java.io.File(serverDir, "plugins/KodaTransfer/playerdata/stats/" + uuid + "_live.json");
        if (pluginFile.exists()) return pluginFile;
        
        java.io.File f1 = new java.io.File(serverDir, world + "/players/stats/" + uuid + "_live.json");
        if (f1.exists()) return f1;
        java.io.File f2 = new java.io.File(serverDir, world + "/stats/" + uuid + "_live.json");
        return f2;
    }

    public static PlayerStats getStats(File serverDir, String uuid) {
        PlayerStats stats = new PlayerStats();
        File normalFile = getStatsFile(serverDir, uuid);
        File liveFile = getLiveStatsFile(serverDir, uuid);

        // KodaTransfer refreshes its live file every 2s while the player is
        // online — it is always at least as fresh as the vanilla stats file,
        // so prefer it whenever it exists (mtime comparison loses that race
        // whenever vanilla writes its stats between plugin ticks)
        File statsFile = normalFile;
        boolean isLiveFormat = false;

        if (liveFile.exists()) {
            statsFile = liveFile;
            isLiveFormat = true;
        }

        if (!statsFile.exists()) return stats;
        try {
            parseStatsInto(stats, statsFile, isLiveFormat);
        } catch (Exception e) {
            // KodaTransfer rewrites its live file every 2s; reading it mid-write
            // fails — fall back to the vanilla stats file instead of zeros
            if (isLiveFormat && normalFile.exists()) {
                try { parseStatsInto(stats, normalFile, false); } catch (Exception ignored) {}
            }
        }
        return stats;
    }

    private static void parseStatsInto(PlayerStats stats, File file, boolean isLiveFormat) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(jsonStr);

        if (isLiveFormat) {
            // Custom live format written by KodaTransfer every 2 seconds
            if (root.has("hoursPlayed")) stats.hoursPlayed = root.getLong("hoursPlayed");
            else if (root.has("playOneMinute")) stats.hoursPlayed = (root.getLong("playOneMinute") + 36000) / 72000; // round to nearest hour
            if (root.has("deaths")) stats.deaths = root.getLong("deaths");
            if (root.has("mobsKilled")) stats.mobsKilled = root.getLong("mobsKilled");
            if (root.has("damageTaken")) stats.damageTaken = root.getLong("damageTaken") / 10;
            if (root.has("blocksMined")) stats.blocksMined = root.getLong("blocksMined");
            return;
        }

        if (!root.has("stats")) return;
        JSONObject statsObj = root.getJSONObject("stats");

        if (statsObj.has("minecraft:custom")) {
            JSONObject custom = statsObj.getJSONObject("minecraft:custom");
            if (custom.has("minecraft:play_time")) {
                stats.hoursPlayed = (custom.getLong("minecraft:play_time") + 36000) / 72000; // round to nearest hour
            }
            if (custom.has("minecraft:deaths")) {
                stats.deaths = custom.getLong("minecraft:deaths");
            }
            if (custom.has("minecraft:mob_kills")) {
                stats.mobsKilled = custom.getLong("minecraft:mob_kills");
            }
            if (custom.has("minecraft:damage_taken")) {
                stats.damageTaken = custom.getLong("minecraft:damage_taken") / 10; // Minecraft stores damage in tenths of a heart
            }
        }

        if (statsObj.has("minecraft:mined")) {
            JSONObject mined = statsObj.getJSONObject("minecraft:mined");
            long totalMined = 0;
            java.util.Iterator<String> keys = mined.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                totalMined += mined.getLong(key);
            }
            stats.blocksMined = totalMined;
        }
    }
}
