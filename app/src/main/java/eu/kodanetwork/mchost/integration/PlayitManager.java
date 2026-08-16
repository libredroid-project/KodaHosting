package eu.kodanetwork.mchost.integration;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayitManager {
    private static final String TAG = "PlayitManager";
    private static final Pattern PLAYIT_HOST_PATTERN =
        Pattern.compile("([a-zA-Z0-9-]+\\.playit\\.gg)");
    private static final String PLAYIT_URL = "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-aarch64";

    private final Context appContext;

    public PlayitManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public File getLogFile(String serverId) {
        File d = new File(appContext.getFilesDir(), "playit");
        if (!d.exists()) d.mkdirs();
        return new File(d, "playit_" + serverId + ".log");
    }

    public Process startTunnelNative(String serverId, String playitToken) {
        try {
            File playitBin = new File(appContext.getFilesDir(), "playit-bin");
            if (!playitBin.exists()) {
                Log.i(TAG, "Downloading playit binary...");
                HttpURLConnection c = (HttpURLConnection) new URL(PLAYIT_URL).openConnection();
                c.setInstanceFollowRedirects(true);
                c.connect();
                try (InputStream is = c.getInputStream(); FileOutputStream fos = new FileOutputStream(playitBin)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                }
                playitBin.setExecutable(true, false);
            }

            File configDir = new File(appContext.getFilesDir(), "playit_config_" + serverId);
            configDir.mkdirs();
            if (playitToken != null && !playitToken.isEmpty()) {
                File tokenFile = new File(configDir, "playit.toml");
                try (FileOutputStream fos = new FileOutputStream(tokenFile)) {
                    fos.write(("secret_key = \"" + playitToken + "\"\n").getBytes());
                }
            }

            File log = getLogFile(serverId);
            ProcessBuilder pb = new PlayitProcessBuilder(playitBin, configDir, log).build();
            return pb.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start native playit", e);
            return null;
        }
    }

    private static class PlayitProcessBuilder {
        private final ProcessBuilder pb;
        public PlayitProcessBuilder(File bin, File configDir, File logFile) {
            this.pb = new ProcessBuilder(bin.getAbsolutePath(), "--config", new File(configDir, "playit.toml").getAbsolutePath());
            this.pb.redirectErrorStream(true);
            this.pb.redirectOutput(logFile);
        }
        public ProcessBuilder build() { return pb; }
    }

    public String readAssignedAddress(String serverId) {
        File log = getLogFile(serverId);
        if (!log.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(log))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = PLAYIT_HOST_PATTERN.matcher(line);
                if (m.find()) return m.group(1).toLowerCase();
            }
        } catch (IOException ignored) {}
        return null;
    }
}
