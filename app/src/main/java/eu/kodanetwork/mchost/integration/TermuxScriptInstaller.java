package eu.kodanetwork.mchost.integration;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class TermuxScriptInstaller {
    private TermuxScriptInstaller() {}

    public static File ensureSetupScript(Context context) throws IOException {
        String publicDir = "/sdcard/Documents/KodaNetwork";
        String script = 
            "#!/data/data/com.termux/files/usr/bin/bash\n" +
            "echo '--- KodaNetwork Setup Start ---'\n" +
            "termux-setup-storage\n" +
            "mkdir -p " + publicDir + "\n" +
            "rm -f " + publicDir + "/.setup_ready\n" +
            "pkg update -y\n" +
            "pkg install -y curl openjdk-21 unzip\n" +
            "echo 'Installing FRP Tunnel...'\n" +
            "curl -L https://github.com/fatedier/frp/releases/download/v0.56.0/frp_0.56.0_linux_arm64.tar.gz -o /data/data/com.termux/files/home/frp.tar.gz\n" +
            "tar -xzf /data/data/com.termux/files/home/frp.tar.gz -C /data/data/com.termux/files/home/\n" +
            "mv /data/data/com.termux/files/home/frp_0.56.0_linux_arm64/frpc /data/data/com.termux/files/usr/bin/frpc\n" +
            "chmod +x /data/data/com.termux/files/usr/bin/frpc\n" +
            "rm -rf /data/data/com.termux/files/home/frp*\n" +
            "touch " + publicDir + "/.setup_ready\n" +
            "echo '--- Setup Complete! ---'\n";
        return writeScript(context, "termux_setup.sh", script);
    }

    public static boolean isSetupReady() {
        File f = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "KodaNetwork/.setup_ready");
        return f.exists();
    }

    public static File ensureServerStartScript(Context context) throws IOException {
        String script = "#!/data/data/com.termux/files/usr/bin/bash\n"
            + "set -e\n"
            + "SERVER_DIR=\"$1\"\n"
            + "RAM_MB=\"$2\"\n"
            + "if [ ! -f \"$HOME/.kodahosting/java_ready\" ]; then\n"
            + "  pkg update -y && pkg install -y proot-distro openjdk-21\n"
            + "  proot-distro install ubuntu || true\n"
            + "  proot-distro login ubuntu -- bash -lc 'apt update && apt install -y openjdk-21-jre-headless'\n"
            + "  mkdir -p \"$HOME/.kodahosting\" && echo \"ok\" > \"$HOME/.kodahosting/java_ready\"\n"
            + "fi\n"
            + "cd \"$SERVER_DIR\"\n"
            + "proot-distro login ubuntu -- bash -lc 'cd \"'\"$SERVER_DIR\"'\" && java -Xmx'\"$RAM_MB\"'M -Xms512M -jar server.jar --nogui --add-plugin=.sys/koda_core.jar'\n";
        return writeScript(context, "start_minecraft_server.sh", script);
    }

    public static File ensurePlayitStartScript(Context context) throws IOException {
        String script = "#!/data/data/com.termux/files/usr/bin/bash\n"
            + "set -e\n"
            + "OUT_FILE=\"$1\"\n"
            + "PLAYIT_TOKEN=\"$2\"\n"
            + "pkg install -y curl\n"
            + "if [ ! -x \"$HOME/playit\" ]; then\n"
            + "  curl -fsSL https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-aarch64 -o \"$HOME/playit\"\n"
            + "  chmod +x \"$HOME/playit\"\n"
            + "fi\n"
            + "if [ -n \"$PLAYIT_TOKEN\" ]; then\n"
            + "  mkdir -p \"$HOME/.config/playit_gg\"\n"
            + "  printf '%s' \"$PLAYIT_TOKEN\" > \"$HOME/.config/playit_gg/token\"\n"
            + "fi\n"
            + "\"$HOME/playit\" > \"$OUT_FILE\" 2>&1\n";
        return writeScript(context, "start_playit.sh", script);
    }

    public static File ensureJavaFromSupabaseScript(Context context) throws IOException {
        String script = "#!/data/data/com.termux/files/usr/bin/bash\n"
            + "set -e\n"
            + "JDK_URL=\"$1\"\n"
            + "JDK_SHA=\"$2\"\n"
            + "pkg update -y\n"
            + "pkg install -y proot-distro curl coreutils\n"
            + "proot-distro install ubuntu || true\n"
            + "mkdir -p \"$HOME/.kodahosting\"\n"
            + "JDK_TGZ=\"$HOME/.kodahosting/openjdk17.tar.gz\"\n"
            + "curl -L \"$JDK_URL\" -o \"$JDK_TGZ\"\n"
            + "if [ -n \"$JDK_SHA\" ]; then\n"
            + "  echo \"$JDK_SHA  $JDK_TGZ\" | sha256sum -c -\n"
            + "fi\n"
            + "proot-distro login ubuntu -- bash -lc 'mkdir -p /opt/kodahosting && tar -xzf \"'\"$JDK_TGZ\"'\" -C /opt/kodahosting'\n"
            + "echo \"ok\" > \"$HOME/.kodahosting/java_ready\"\n";
        return writeScript(context, "setup_java_from_supabase.sh", script);
    }

    private static File writeScript(Context context, String name, String content) throws IOException {
        File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "KodaNetwork/scripts");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create public script dir: " + dir.getAbsolutePath());
        File scriptFile = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(scriptFile, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        scriptFile.setReadable(true, false);
        scriptFile.setExecutable(true, false);
        return scriptFile;
    }
}
