package eu.kodanetwork.mchost.integration;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

public final class TermuxBridge {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUNNER_PACKAGE = "com.termux.api";
    private static final String RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND";

    private TermuxBridge() {}

    public static boolean runBashCommand(Context context, String command, boolean background) {
        try {
            eu.kodanetwork.mchost.util.AppLogger.log("TermuxBridge", "Executing bash command (bg=" + background + "): " + command);
            Intent intent = new Intent(RUN_COMMAND_ACTION);
            intent.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Exception e) {
            eu.kodanetwork.mchost.util.AppLogger.log("TermuxBridge", "Error: " + e.getMessage());
            return false;
        }
    }

    public static boolean runBashCommand(Context context, String command) {
        return runBashCommand(context, command, true);
    }

    public static boolean runScript(Context context, File script, String... args) {
        try {
            eu.kodanetwork.mchost.util.AppLogger.log("TermuxBridge", "Executing script via bash: " + script.getAbsolutePath());
            Intent intent = new Intent(RUN_COMMAND_ACTION);
            intent.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            
            // First argument is the script path, followed by its arguments
            String[] bashArgs = new String[args.length + 1];
            bashArgs[0] = script.getAbsolutePath();
            System.arraycopy(args, 0, bashArgs, 1, args.length);
            
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", bashArgs);
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            eu.kodanetwork.mchost.util.AppLogger.log("TermuxBridge", "Script intent dispatched to Termux.");
            return true;
        } catch (Exception e) {
            eu.kodanetwork.mchost.util.AppLogger.log("TermuxBridge", "Error executing script: " + e.getMessage());
            return fallbackViewIntent(context, script);
        }
    }

    public static boolean openTermux(Context context) {
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTermuxInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTermuxApiInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_RUNNER_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean fallbackViewIntent(Context context, File script) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setPackage(TERMUX_PACKAGE);
            i.setData(Uri.parse("file://" + script.getAbsolutePath()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
