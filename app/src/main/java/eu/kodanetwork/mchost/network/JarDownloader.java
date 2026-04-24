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

import eu.kodanetwork.mchost.model.ServerInstance;

public class JarDownloader {

    public interface Cb {
        void onProgress(int pct, String msg);
        void onDone(File jar);
        void onError(String err);
    }

    private final ExecutorService ex   = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    public void download(ServerInstance srv, Cb cb) {
        ex.submit(() -> {
            try {
                post(cb, 0, "Resolving URL…");
                String url = resolve(srv, cb);
                if (url == null) return;
                post(cb, 5, "Connecting…");
                new File(srv.getServerDir()).mkdirs();
                
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".jar") || fileName.contains("?")) {
                    fileName = "server.jar";
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
                post(cb,2,"Checking PaperMC API…");
                return paper(srv.getVersion(), cb);
            case PURPUR:
                post(cb,2,"Checking Purpur API…");
                return "https://api.purpurmc.org/v2/purpur/"+srv.getVersion()+"/latest/download";
            case VANILLA:
                post(cb,2,"Checking Mojang manifest…");
                return vanilla(srv.getVersion(), cb);
            case FABRIC:
                post(cb,2,"Building Fabric URL…");
                return "https://meta.fabricmc.net/v2/versions/loader/"+srv.getVersion()+"/stable/stable/server/jar";
            default:
                main.post(() -> cb.onError("Manual install required for "+srv.getType().name()+
                    ".\nPlace server.jar in: "+srv.getServerDir()));
                return null;
        }
    }

    private String paper(String ver, Cb cb) throws Exception {
        String j = fetch("https://api.papermc.io/v2/projects/paper/versions/"+ver+"/builds");
        int build = lastInt(j,"\"build\":");
        if (build<0) throw new Exception("No Paper builds found for "+ver);
        post(cb,4,"Found build #"+build);
        return "https://api.papermc.io/v2/projects/paper/versions/"+ver+"/builds/"+build+
               "/downloads/paper-"+ver+"-"+build+".jar";
    }

    private String vanilla(String ver, Cb cb) throws Exception {
        String manifest = fetch("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        int p = manifest.indexOf("\"id\":\""+ver+"\"");
        if (p<0) throw new Exception("Version "+ver+" not found in Mojang manifest");
        int us = manifest.indexOf("\"url\":\"",p)+7;
        String vUrl = manifest.substring(us, manifest.indexOf("\"",us));
        String vj = fetch(vUrl);
        int sp2 = vj.indexOf("\"server\"");
        int ss = vj.indexOf("\"url\":\"",sp2)+7;
        return vj.substring(ss, vj.indexOf("\"",ss));
    }

    private void dl(String urlStr, File dest, Cb cb) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000); c.setReadTimeout(180000);
        c.setRequestProperty("User-Agent","KodaNetwork/3.0");
        c.connect();
        if (c.getResponseCode()>=300) throw new Exception("HTTP "+c.getResponseCode());
        long total = c.getContentLengthLong();
        try (InputStream is=c.getInputStream(); FileOutputStream fo=new FileOutputStream(dest)) {
            byte[] buf=new byte[16384]; long done=0; int r;
            while((r=is.read(buf))!=-1){
                fo.write(buf,0,r); done+=r;
                if(total>0){
                    int pct=(int)(done*100/total);
                    String msg="Downloading… "+done/1024/1024+" / "+total/1024/1024+" MB";
                    post(cb,pct,msg);
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
