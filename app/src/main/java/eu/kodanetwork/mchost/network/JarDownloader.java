package eu.kodanetwork.mchost.network;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.R;
import android.content.Context;

public class JarDownloader {

    public interface Cb {
        void onProgress(int pct, String msg);
        void onDone(File jar);
        void onError(String err);
    }

    private final ExecutorService ex   = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());
    private final Context         context;

    public JarDownloader(Context context) {
        this.context = context;
    }

    public void download(ServerInstance srv, Cb cb) {
        ex.submit(() -> {
            try {
                post(cb, 0, context.getString(R.string.dl_resolving_url));
                String url = resolve(srv, cb);
                if (url == null) return;
                post(cb, 5, context.getString(R.string.dl_connecting));
                new File(srv.getServerDir()).mkdirs();
                
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".jar") || fileName.contains("?")) {
                    fileName = "server.jar";
                }
                if (srv.getType() == ServerInstance.Type.FABRIC) {
                    fileName = "fabric-server.jar";
                }
                
                // Optional: Clean up old jars to avoid clutter
                File[] oldJars = new File(srv.getServerDir()).listFiles((d, name) -> name.endsWith(".jar"));
                if (oldJars != null) {
                    for (File old : oldJars) old.delete();
                }

                File out = new File(srv.getServerDir(), fileName);
                dl(url, out, cb);
                main.post(() -> cb.onDone(out));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage()));
            }
        });
    }

    private String resolve(ServerInstance srv, Cb cb) throws Exception {
        switch (srv.getType()) {
            case PAPER:
                post(cb,2,context.getString(R.string.dl_checking_api, "PaperMC"));
                return paper(srv.getVersion(), cb);
            case PURPUR:
                post(cb,2,context.getString(R.string.dl_checking_api, "Purpur"));
                return "https://api.purpurmc.org/v2/purpur/"+srv.getVersion()+"/latest/download";
            case VANILLA:
                post(cb,2,context.getString(R.string.dl_checking_mojang));
                return vanilla(srv.getVersion(), cb);
            case FABRIC:
                post(cb,2,context.getString(R.string.dl_building_url, "Fabric"));
                return "https://meta.fabricmc.net/v2/versions/loader/"+srv.getVersion()+"/stable/stable/server/jar";
            case FORGE:
                post(cb,2,context.getString(R.string.dl_building_url, "Forge"));
                return forge(srv.getVersion(), cb);
            case NEOFORGE:
                post(cb,2,context.getString(R.string.dl_building_url, "NeoForge"));
                return neoforge(srv.getVersion(), cb);
            case FOLIA:
                post(cb,2,context.getString(R.string.dl_checking_api, "Folia"));
                return folia(srv.getVersion(), cb);
            case VELOCITY:
                post(cb,2,context.getString(R.string.dl_checking_api, "Velocity"));
                return velocity(srv.getVersion(), cb);
            default:
                main.post(() -> cb.onError(context.getString(R.string.dl_manual_required, srv.getType().name(), srv.getServerDir())));
                return null;
        }
    }

    private String paper(String ver, Cb cb) throws Exception {
        return fetchPaperMcApiV3("paper", ver, cb);
    }

    private String folia(String ver, Cb cb) throws Exception {
        return fetchPaperMcApiV3("folia", ver, cb);
    }

    private String velocity(String ver, Cb cb) throws Exception {
        return fetchPaperMcApiV3("velocity", ver, cb);
    }

    private String fetchPaperMcApiV3(String project, String ver, Cb cb) throws Exception {
        String j = fetch("https://fill.papermc.io/v3/projects/" + project + "/versions/" + ver + "/builds");
        org.json.JSONArray builds = new org.json.JSONArray(j);
        if (builds.length() == 0) throw new Exception(context.getString(R.string.dl_error_no_builds, project, ver));
        org.json.JSONObject latestBuildObj = builds.getJSONObject(0);
        int build = latestBuildObj.getInt("id");
        post(cb, 4, context.getString(R.string.dl_found_build, project, String.valueOf(build)));
        return latestBuildObj.getJSONObject("downloads").getJSONObject("server:default").getString("url");
    }

    private String forge(String mcVer, Cb cb) throws Exception {
        String xml = fetch("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
        // Forge versions are like 1.20.1-47.3.0. We search for mcVer-[digits]
        Pattern p = Pattern.compile("<version>(" + mcVer.replace(".", "\\.") + "-[0-9.]+)</version>");
        java.util.regex.Matcher m = p.matcher(xml);
        String latest = null;
        while (m.find()) latest = m.group(1);
        
        if (latest == null) throw new Exception(context.getString(R.string.dl_error_no_builds, "Forge", mcVer));
        post(cb, 4, context.getString(R.string.dl_found_version, "Forge", latest));
        
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/" + latest + "/forge-" + latest + "-installer.jar";
    }

    private String vanilla(String ver, Cb cb) throws Exception {
        String manifest = fetch("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        int p = manifest.indexOf("\"id\":\""+ver+"\"");
        if (p<0) throw new Exception(context.getString(R.string.dl_error_mojang, ver));
        int us = manifest.indexOf("\"url\":\"",p)+7;
        String vUrl = manifest.substring(us, manifest.indexOf("\"",us));
        String vj = fetch(vUrl);
        int sp2 = vj.indexOf("\"server\"");
        int ss = vj.indexOf("\"url\":\"",sp2)+7;
        return vj.substring(ss, vj.indexOf("\"",ss));
    }

    private String neoforge(String mcVer, Cb cb) throws Exception {
        // NeoForge usually follows mcVer.build format. We need to find the latest build for that MC version.
        // For simplicity, we use the maven metadata or a search API.
        // This is a complex one, let's try a direct approach for common versions if possible.
        // For now, let's use the official maven metadata.
        String xml = fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
        Pattern p = Pattern.compile("<version>(" + mcVer.replace(".", "\\.") + "\\.[0-9]+)</version>");
        java.util.regex.Matcher m = p.matcher(xml);
        String latest = null;
        while (m.find()) latest = m.group(1); // Get the last one in the list (usually latest)
        
        if (latest == null) throw new Exception(context.getString(R.string.dl_error_no_builds, "NeoForge", mcVer));
        post(cb, 4, context.getString(R.string.dl_found_version, "NeoForge", latest));
        
        // Return the installer jar URL
        return "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + latest + "/neoforge-" + latest + "-installer.jar";
    }

    private void dl(String urlStr, File dest, Cb cb) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000); c.setReadTimeout(180000);
        c.setRequestProperty("User-Agent","KodaNetwork/3.0");
        c.connect();
        if (c.getResponseCode()>=300) throw new Exception(context.getString(R.string.dl_error_http, c.getResponseCode()));
        long total = c.getContentLengthLong();
        try (InputStream is=c.getInputStream(); FileOutputStream fo=new FileOutputStream(dest)) {
            byte[] buf=new byte[16384]; long done=0; int r;
            long lastPostTime = 0;
            while((r=is.read(buf))!=-1){
                fo.write(buf,0,r); done+=r;
                if(total>0){
                    long now = System.currentTimeMillis();
                    if (now - lastPostTime > 250 || done == total) {
                        lastPostTime = now;
                        int pct=(int)(done*100/total);
                        String msg=context.getString(R.string.dl_downloading, String.valueOf(done/1024/1024), String.valueOf(total/1024/1024));
                        post(cb,pct,msg);
                    }
                }
            }
        }
        c.disconnect();
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000); c.connect();
        try(InputStream is=c.getInputStream()){
            StringBuilder sb=new StringBuilder(); byte[] b=new byte[4096]; int r;
            while((r=is.read(b))!=-1) sb.append(new String(b,0,r));
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private int lastInt(String json, String key) {
        int last=-1,i=0;
        while(true){
            int p=json.indexOf(key,i); if(p<0) break;
            int s=p+key.length();
            while(s<json.length()&&!Character.isDigit(json.charAt(s)))s++;
            int e=s; while(e<json.length()&&Character.isDigit(json.charAt(e)))e++;
            try{last=Integer.parseInt(json.substring(s,e));}catch(Exception ignored){}
            i=p+1;
        }
        return last;
    }

    private void post(Cb cb,int p,String m){ main.post(()->cb.onProgress(p,m)); }
}
