
import os

with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "r", encoding="utf-8") as f:
    code = f.read()

helpers = """
    public static java.io.File getPlayerDataFile(java.io.File serverDir, String uuid) {
        java.io.File f1 = new java.io.File(serverDir, "world/players/data/" + uuid + ".dat");
        if (f1.exists()) return f1;
        java.io.File f2 = new java.io.File(serverDir, "world/playerdata/" + uuid + ".dat");
        return f2.exists() ? f2 : f1;
    }

    public static java.io.File getStatsFile(java.io.File serverDir, String uuid) {
        java.io.File f1 = new java.io.File(serverDir, "world/players/stats/" + uuid + ".json");
        if (f1.exists()) return f1;
        java.io.File f2 = new java.io.File(serverDir, "world/stats/" + uuid + ".json");
        return f2.exists() ? f2 : f2;
    }
"""

if "getPlayerDataFile" not in code:
    code = code.replace("public class PlayerStatsParser {", "public class PlayerStatsParser {" + helpers)

code = code.replace("""File statsFile = new File(serverDir, "world/stats/" + uuid + ".json");""", """File statsFile = getStatsFile(serverDir, uuid);""")

with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "w", encoding="utf-8") as f:
    f.write(code)

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

code = code.replace("""new java.io.File(server.getServerDir(), "world/playerdata/" + uuid + ".dat")""", """eu.kodanetwork.mchost.util.PlayerStatsParser.getPlayerDataFile(new java.io.File(server.getServerDir()), uuid)""")
code = code.replace("""new java.io.File(server.getServerDir(), "world/stats/" + uuid + ".json")""", """eu.kodanetwork.mchost.util.PlayerStatsParser.getStatsFile(new java.io.File(server.getServerDir()), uuid)""")

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "w", encoding="utf-8") as f:
    f.write(code)

print("Paths fixed")

