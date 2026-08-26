package eu.kodanetwork.mchost.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ServerInstance {

    public enum Type       { PAPER, PURPUR, FORGE, FABRIC, VANILLA, NEOFORGE, VELOCITY, FOLIA, MARIADB, REDIS, MONGODB, POSTGRESQL, PUMPKIN }
    public enum State      { OFFLINE, STARTING, ONLINE, STOPPING, CRASHED, INSTALLING, RESTARTING, SETTING_UP, HIBERNATED }
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
    private long      lastActive;
    private String    dbUsername = "admin";
    private String    dbPassword = java.util.UUID.randomUUID().toString().substring(0, 12);

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
    public transient float currentTps = 20.0f;
    public transient java.util.List<String> onlinePlayerNames = new java.util.ArrayList<>();

    // Crash diagnostics (transient — not saved to JSON)
    public transient String  crashReason    = null;   // Human-readable cause
    public transient String  crashCategory  = null;   // Category key: OOM, EULA, MOD_CRASH, etc.
    public transient int     crashExitCode  = 0;      // Process exit code
    public transient long    crashTime      = 0;      // System.currentTimeMillis() of crash
    public transient String  crashFix       = null;   // Fix description if known
    public transient String  crashFixAction = null;   // Internal fix action key
    public transient String  crashStackTrace = null;  // Relevant stacktrace lines
    public java.util.List<String> knownPlayers = new java.util.ArrayList<>();

    private String    playitAddress = "";
    private String    domainLink    = "";
    private String    velocitySecret = "";
    private boolean   useNative     = true;  // Always use native flow (Termux removed)
    private String    themeColor    = "#FF6B00"; 
    private boolean   autoSetup     = true;
    private String    aiPrompt      = "";
    private String    modpackName   = "";
    private int       javaRuntime   = 0; // 0 = Auto (Fabric→21, sonst 25)
    private boolean   bedrockSupport = false;
    private int       bedrockPort    = 0;
    private boolean   voicechat      = false;
    private int       voicechatPort  = 0;
    private boolean   kodadashSupport = false;
    private int       kodadashPort   = 0;
    
    public java.util.Map<String, String> pluginVersions = new java.util.HashMap<>();
    private String lastBackupTime;
    
    private String customDomain = "";
    private String baseDomain = "kodanetwork.eu";
    
    // Runtime state (not saved in json)
    private transient boolean isUpdating = false;

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
        return subdomain + "." + getBaseDomain();
    }

    public boolean isRunning() {
        return state == State.ONLINE || state == State.STARTING;
    }

    public boolean isDatabase() {
        return type == Type.MARIADB || type == Type.REDIS || type == Type.MONGODB || type == Type.POSTGRESQL;
    }

    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String dbUsername) { this.dbUsername = dbUsername; }
    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }
    public void setType(Type t) { this.type = t; }
    public void setPort(int port) { this.port = port; }

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
        j.put("aiPrompt", aiPrompt);
        j.put("modpackName", modpackName);
        j.put("javaRuntime", javaRuntime);
        j.put("bedrockSupport", bedrockSupport);
        j.put("bedrockPort", bedrockPort);
        j.put("voicechat", voicechat);
        j.put("voicechatPort", voicechatPort);
        j.put("kodadashSupport", kodadashSupport);
        j.put("kodadashPort", kodadashPort);
        j.put("lastActive", lastActive);
        j.put("customDomain", customDomain);
        j.put("baseDomain", baseDomain);
        if (state == State.HIBERNATED) {
            j.put("isHibernated", true);
        }
        
        JSONObject pv = new JSONObject();
        for (java.util.Map.Entry<String, String> entry : pluginVersions.entrySet()) {
            pv.put(entry.getKey(), entry.getValue());
        }
        j.put("pluginVersions", pv);
        
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
        s.aiPrompt       = j.optString("aiPrompt", "");
        s.modpackName    = j.optString("modpackName", "");
        s.javaRuntime    = j.optInt("javaRuntime", 0);
        s.bedrockSupport = j.optBoolean("bedrockSupport", false);
        s.bedrockPort    = j.optInt("bedrockPort", 0);
        s.voicechat      = j.optBoolean("voicechat", false);
        s.voicechatPort  = j.optInt("voicechatPort", 0);
        s.kodadashSupport = j.optBoolean("kodadashSupport", false);
        s.kodadashPort   = j.optInt("kodadashPort", 0);
        s.lastActive     = j.optLong("lastActive", System.currentTimeMillis());
        s.customDomain   = j.optString("customDomain", "");
        s.baseDomain     = j.optString("baseDomain", "kodanetwork.eu");
        if (j.optBoolean("isHibernated", false)) {
            s.state = State.HIBERNATED;
        }

        JSONArray kp = j.optJSONArray("knownPlayers");
        if (kp != null) {
            for (int i=0; i<kp.length(); i++) s.knownPlayers.add(kp.getString(i));
        }
        
        JSONObject pv = j.optJSONObject("pluginVersions");
        if (pv != null) {
            java.util.Iterator<String> keys = pv.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                s.pluginVersions.put(key, pv.getString(key));
            }
        }
        
        return s;
    }

    public String getId()                      { return id; }
    public void   setId(String v)              { id = v; }
    public String getName()                    { return name; }
    // Renaming only affects the local display name; the subdomain is the
    // DNS identity and must only change via setSubdomain() (e.g. at creation).
    public void   setName(String v)            { name = v; }
    public Type   getType()                    { return type; }
    public String getVersion()                 { return version; }
    public void   setVersion(String v)         { version = v; }
    public long   getLastActive()              { return lastActive; }
    public void   setLastActive(long v)        { lastActive = v; }
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
    public String getAiPrompt()                { return aiPrompt == null ? "" : aiPrompt; }
    public void   setAiPrompt(String v)        { aiPrompt = v; }
    public String getModpackName()             { return modpackName == null ? "" : modpackName; }
    public void   setModpackName(String v)     { modpackName = v; }
    public int    getJavaRuntime()             { return javaRuntime; }
    public void   setJavaRuntime(int v)        { javaRuntime = v; }
    public boolean isBedrockSupport()          { return bedrockSupport; }
    public void   setBedrockSupport(boolean v) { bedrockSupport = v; }
    public int    getBedrockPort()             { return bedrockPort; }
    public void   setBedrockPort(int v)        { bedrockPort = v; }
    public boolean isVoicechat()               { return voicechat; }
    public void   setVoicechat(boolean v)      { voicechat = v; }
    public int    getVoicechatPort()           { return voicechatPort; }
    public void   setVoicechatPort(int v)      { voicechatPort = v; }
    
    public boolean isKodadashSupport()         { return kodadashSupport; }
    public void   setKodadashSupport(boolean v){ kodadashSupport = v; }
    public int    getKodadashPort()            { return kodadashPort; }
    public void   setKodadashPort(int v)       { kodadashPort = v; }
    
    public String getLastBackupTime()          { return lastBackupTime; }
    public void   setLastBackupTime(String v)  { lastBackupTime = v; }
    
    public String getCustomDomain()            { return customDomain == null ? "" : customDomain; }
    public void   setCustomDomain(String v)    { customDomain = v; }
    
    public String getBaseDomain()              { return baseDomain == null ? "kodanetwork.eu" : baseDomain; }
    public void   setBaseDomain(String v)      { baseDomain = v; }
    
    public boolean isUpdating()                { return isUpdating; }
    public void   setUpdating(boolean v)       { isUpdating = v; }
}
