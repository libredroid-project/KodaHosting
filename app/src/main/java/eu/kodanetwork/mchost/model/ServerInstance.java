package eu.kodanetwork.mchost.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ServerInstance {

    public enum Type       { PAPER, PURPUR, FABRIC, VANILLA }
    public enum State      { OFFLINE, INSTALLING, STARTING, ONLINE, STOPPING, CRASHED }
    public enum Gamemode   { survival, creative, adventure, spectator }
    public enum Difficulty { peaceful, easy, normal, hard }

    private String id;
    private String name;
    private Type   type    = Type.PAPER;
    private String version = "1.21.4";
    private int    ramMB   = 1024;
    private int    port    = 25565;

    // The subdomain is derived from name: "MySrv" → "mysrv.kodanetwork.eu"
    private String subdomain;

    private int        maxPlayers = 20;
    private Gamemode   gamemode   = Gamemode.survival;
    private Difficulty difficulty = Difficulty.normal;
    private boolean    pvp        = true;
    private boolean    whitelist  = false;
    private String     motd       = "A KodaNetwork Server";

    // Velocity forwarding secret (set by network admin)
    private String velocitySecret = "";
    private String playitAddress = "";
    private String domainLink = "";

    // Where server files live on the device
    private String serverDir;

    // Runtime (not persisted)
    public transient State state         = State.OFFLINE;
    public transient int   onlinePlayers = 0;
    public transient long  startTime     = 0;

    public ServerInstance() {}

    public ServerInstance(String id, String name, Type type, String version, int ramMB, int port, String serverDir) {
        this.id        = id;
        this.name      = name;
        this.type      = type;
        this.version   = version;
        this.ramMB     = ramMB;
        this.port      = port;
        this.serverDir = serverDir;
        this.subdomain = sanitize(name);
    }

    /** e.g. "My Server" → "my-server" */
    private static String sanitize(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
    }

    /** Full joinable address players use: mysrv.kodanetwork.eu */
    public String getJoinAddress() {
        return subdomain + ".kodanetwork.eu";
    }

    /** Startup command run inside Termux shell */
    public String buildStartCommand() {
        // Runs as: am startservice -n com.termux/.app.TermuxService --es command "..."
        String javaCmd = String.format(
            "cd %s && java -Xmx%dM -Xms%dM " +
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
            "-jar server.jar --nogui",
            serverDir, ramMB, ramMB / 2
        );
        return javaCmd;
    }

    public boolean isRunning() {
        return state == State.ONLINE || state == State.STARTING;
    }

    public String getFormattedUptime() {
        if (startTime == 0 || !isRunning()) return "--:--:--";
        long s = (System.currentTimeMillis() - startTime) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("name", name); j.put("type", type.name());
        j.put("version", version); j.put("ramMB", ramMB); j.put("port", port);
        j.put("subdomain", subdomain); j.put("maxPlayers", maxPlayers);
        j.put("gamemode", gamemode.name()); j.put("difficulty", difficulty.name());
        j.put("pvp", pvp); j.put("whitelist", whitelist); j.put("motd", motd);
        j.put("velocitySecret", velocitySecret); j.put("serverDir", serverDir);
        j.put("playitAddress", playitAddress); j.put("domainLink", domainLink);
        return j;
    }

    public static ServerInstance fromJson(JSONObject j) throws JSONException {
        ServerInstance s = new ServerInstance();
        s.id             = j.getString("id");
        s.name           = j.getString("name");
        s.type           = Type.valueOf(j.getString("type"));
        s.version        = j.getString("version");
        s.ramMB          = j.getInt("ramMB");
        s.port           = j.getInt("port");
        s.subdomain      = j.optString("subdomain", sanitize(s.name));
        s.maxPlayers     = j.optInt("maxPlayers", 20);
        s.gamemode       = Gamemode.valueOf(j.optString("gamemode", "survival"));
        s.difficulty     = Difficulty.valueOf(j.optString("difficulty", "normal"));
        s.pvp            = j.optBoolean("pvp", true);
        s.whitelist      = j.optBoolean("whitelist", false);
        s.motd           = j.optString("motd", "A KodaNetwork Server");
        s.velocitySecret = j.optString("velocitySecret", "");
        s.serverDir      = j.getString("serverDir");
        s.playitAddress  = j.optString("playitAddress", "");
        s.domainLink     = j.optString("domainLink", "");
        return s;
    }

    // ── Getters/Setters ───────────────────────────────────────────────────────

    public String getId()                      { return id; }
    public void   setId(String v)              { id = v; }
    public String getName()                    { return name; }
    public void   setName(String v)            { name = v; subdomain = sanitize(v); }
    public Type   getType()                    { return type; }
    public void   setType(Type v)              { type = v; }
    public String getVersion()                 { return version; }
    public void   setVersion(String v)         { version = v; }
    public int    getRamMB()                   { return ramMB; }
    public void   setRamMB(int v)              { ramMB = v; }
    public int    getPort()                    { return port; }
    public void   setPort(int v)               { port = v; }
    public String getSubdomain()               { return subdomain; }
    public void   setSubdomain(String v)       { subdomain = v; }
    public int    getMaxPlayers()              { return maxPlayers; }
    public void   setMaxPlayers(int v)         { maxPlayers = v; }
    public Gamemode   getGamemode()            { return gamemode; }
    public void       setGamemode(Gamemode v)  { gamemode = v; }
    public Difficulty getDifficulty()          { return difficulty; }
    public void       setDifficulty(Difficulty v){ difficulty = v; }
    public boolean    isPvp()                  { return pvp; }
    public void       setPvp(boolean v)        { pvp = v; }
    public boolean    isWhitelist()            { return whitelist; }
    public void       setWhitelist(boolean v)  { whitelist = v; }
    public String     getMotd()               { return motd; }
    public void       setMotd(String v)        { motd = v; }
    public String     getVelocitySecret()      { return velocitySecret; }
    public void       setVelocitySecret(String v){ velocitySecret = v; }
    public String     getPlayitAddress()       { return playitAddress; }
    public void       setPlayitAddress(String v){ playitAddress = v == null ? "" : v; }
    public String     getDomainLink()          { return domainLink; }
    public void       setDomainLink(String v)  { domainLink = v == null ? "" : v; }
    public String     getServerDir()           { return serverDir; }
    public void       setServerDir(String v)   { serverDir = v; }
    public java.io.File getServerDirFile()         { return new java.io.File(serverDir); }
}
