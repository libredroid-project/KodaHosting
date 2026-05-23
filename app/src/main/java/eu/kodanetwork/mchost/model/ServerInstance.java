package eu.kodanetwork.mchost.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ServerInstance {

    public enum Type       { PAPER, PURPUR, FORGE, FABRIC, VANILLA, NEOFORGE, VELOCITY, FOLIA }
    public enum State      { OFFLINE, STARTING, ONLINE, STOPPING, CRASHED, INSTALLING }
    public enum Gamemode    { survival, creative, adventure, spectator }
    public enum Difficulty  { peaceful, easy, normal, hard }

    private String    id;
    private String    name;
    private Type      type;
    private String    version;
    private int       ramMB;
    private int       port;
    private String    subdomain;
    private String    serverDir;

    private int       maxPlayers = 20;
    private Gamemode   gamemode   = Gamemode.survival;
    private Difficulty difficulty = Difficulty.normal;
    private boolean    pvp        = true;
    private boolean    whitelist  = false;
    private String     motd       = "A KodaNetwork Server";

    public State      state      = State.OFFLINE;
    public long       startTime  = 0;
    public int        onlinePlayers = 0;
    public transient int ramUsageMB = 0;
    public transient java.util.List<String> onlinePlayerNames = new java.util.ArrayList<>();
    public java.util.List<String> knownPlayers = new java.util.ArrayList<>();

    private String    playitAddress = "";
    private String    domainLink    = "";
    private String    velocitySecret = "";
    private boolean   useNative     = true;  // Always use native flow (Termux removed)
    private String    themeColor    = "#FF6B00"; 
    private boolean   autoSetup     = true;
    private boolean   bedrockSupport = false;
    private int       bedrockPort    = 0;
    private boolean   voicechat      = false;
    private int       voicechatPort  = 0;

    public ServerInstance() {}

    public ServerInstance(String id, String name, Type type, String version, int ramMB, int port, String serverDir) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.version = version;
        this.ramMB = ramMB;
        this.port = port;
        this.serverDir = serverDir;
        this.subdomain = sanitize(name);
    }

    private static String sanitize(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
    }

    public String getJoinAddress() {
        return subdomain + ".kodanetwork.eu";
    }

    public boolean isRunning() {
        return state == State.ONLINE || state == State.STARTING;
    }

    public String getFormattedUptime() {
        if (startTime == 0 || !isRunning()) return "--:--:--";
        long s = (System.currentTimeMillis() - startTime) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("name", name); j.put("type", type.name());
        j.put("version", version); j.put("ramMB", ramMB); j.put("port", port);
        j.put("subdomain", subdomain); j.put("maxPlayers", maxPlayers);
        j.put("gamemode", gamemode.name()); j.put("difficulty", difficulty.name());
        j.put("pvp", pvp); j.put("whitelist", whitelist); j.put("motd", motd);
        j.put("velocitySecret", velocitySecret); j.put("serverDir", serverDir);
        j.put("playitAddress", playitAddress); j.put("domainLink", domainLink);
        j.put("useNative", useNative);
        j.put("themeColor", themeColor);
        j.put("autoSetup", autoSetup);
        
        JSONArray kp = new JSONArray();
        for (String p : knownPlayers) kp.put(p);
        j.put("knownPlayers", kp);
        
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
        s.useNative      = j.optBoolean("useNative", true);  // default true
        s.themeColor     = j.optString("themeColor", "#FF6B00");
        s.autoSetup      = j.optBoolean("autoSetup", true);
        s.bedrockSupport = j.optBoolean("bedrockSupport", false);
        s.voicechat      = j.optBoolean("voicechat", false);

        JSONArray kp = j.optJSONArray("knownPlayers");
        if (kp != null) {
            for (int i=0; i<kp.length(); i++) s.knownPlayers.add(kp.getString(i));
        }
        
        return s;
    }

    public String getId()                      { return id; }
    public void   setId(String v)              { id = v; }
    public String getName()                    { return name; }
    public void   setName(String v)            { name = v; subdomain = sanitize(v); }
    public Type   getType()                    { return type; }
    public String getVersion()                 { return version; }
    public int    getRamMB()                   { return ramMB; }
    public void   setRamMB(int ramMB)          { this.ramMB = ramMB; }
    public int    getPort()                    { return port; }
    public String getSubdomain()               { return subdomain == null ? "" : subdomain; }
    public void   setSubdomain(String v)       { subdomain = sanitize(v); }
    public int    getMaxPlayers()              { return maxPlayers; }
    public void   setMaxPlayers(int v)         { maxPlayers = v; }
    public Gamemode getGamemode()              { return gamemode; }
    public void   setGamemode(Gamemode v)      { gamemode = v; }
    public Difficulty getDifficulty()          { return difficulty; }
    public void   setDifficulty(Difficulty v)  { difficulty = v; }
    public boolean isPvp()                     { return pvp; }
    public void   setPvp(boolean v)            { pvp = v; }
    public boolean isWhitelist()               { return whitelist; }
    public void   setWhitelist(boolean v)      { whitelist = v; }
    public String getMotd()                    { return motd; }
    public void   setMotd(String v)            { motd = v; }
    public String getVelocitySecret()          { return velocitySecret; }
    public void   setVelocitySecret(String v)  { velocitySecret = v; }
    public String getPlayitAddress()           { return playitAddress == null ? "" : playitAddress; }
    public void   setPlayitAddress(String v)   { playitAddress = v; }
    public String getDomainLink()              { return domainLink == null ? "" : domainLink; }
    public void   setDomainLink(String v)      { domainLink = v; }
    public String getServerDir()               { return serverDir; }
    public void   setServerDir(String v)       { serverDir = v; }
    public boolean isUseNative()               { return useNative; }
    public void   setUseNative(boolean v)      { useNative = v; }
    public String getThemeColor()              { return themeColor; }
    public void   setThemeColor(String v)      { themeColor = v; }
    public boolean isAutoSetup()               { return autoSetup; }
    public void   setAutoSetup(boolean v)      { autoSetup = v; }
    public boolean isBedrockSupport()          { return bedrockSupport; }
    public void   setBedrockSupport(boolean v) { bedrockSupport = v; }
    public int    getBedrockPort()             { return bedrockPort; }
    public void   setBedrockPort(int v)        { bedrockPort = v; }
    public boolean isVoicechat()               { return voicechat; }
    public void   setVoicechat(boolean v)      { voicechat = v; }
    public int    getVoicechatPort()           { return voicechatPort; }
    public void   setVoicechatPort(int v)      { voicechatPort = v; }
}
