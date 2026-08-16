package eu.kodanetwork.mchost.security;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.List;

import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.ui.PraetorWarningActivity;
import eu.kodanetwork.mchost.R;

public class PraetorSystem {

    /**
     * Checks if the device has an active internet connection.
     * If not, it launches the PRAETOR warning activity and returns false.
     */
    public static boolean checkNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            triggerWarning(context, 
                "No Internet Connection.\nCritical network operations are blocked to prevent data corruption.", 
                "RETRY CONNECTION");
            return false;
        }
        return true;
    }

    /**
     * Checks if there are any servers currently online or starting.
     * Prevents creating or extracting new servers while one is running to avoid OOM kills.
     */
    public static boolean checkConcurrentServer(Context context) {
        List<ServerInstance> servers = ServerRepo.get(context).all();
        boolean isAnyRunning = false;
        for (ServerInstance server : servers) {
            if (server.state == ServerInstance.State.ONLINE || server.state == ServerInstance.State.STARTING) {
                isAnyRunning = true;
                break;
            }
        }

        if (isAnyRunning) {
            triggerWarning(context, 
                "Simultaneous server setup blocked.\nAnother server is currently running. Setting up a new server now would cause a memory overload and forcefully crash the active server.", 
                "UNDERSTOOD");
            return false;
        }
        return true;
    }

    /**
     * Checks if there is enough free RAM to start the target server.
     * Leaves a buffer of 200MB for Android OS.
     */
    public static boolean checkRamForStart(Context context, ServerInstance targetServer) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(memoryInfo);

        long totalSystemRamMB = memoryInfo.totalMem / (1024 * 1024);
        long freeRamMB = memoryInfo.availMem / (1024 * 1024);
        
        long runningServersRamMB = 0;
        List<ServerInstance> allServers = ServerRepo.get(context).all();
        for (ServerInstance server : allServers) {
            if ((server.state == ServerInstance.State.ONLINE || server.state == ServerInstance.State.STARTING)
                 && !server.getId().equals(targetServer.getId())) {
                runningServersRamMB += server.getRamMB();
            }
        }

        long requiredRamMB = targetServer.getRamMB();
        long androidBufferMB = 1024; // Keep 1.0GB buffer for Android OS

        boolean isAvailExceeded = freeRamMB < requiredRamMB + androidBufferMB;
        boolean isTotalExceeded = (runningServersRamMB + requiredRamMB + androidBufferMB) > totalSystemRamMB;

        if (isAvailExceeded || isTotalExceeded) {
            long maxAllowedRam = Math.max(512, totalSystemRamMB - runningServersRamMB - androidBufferMB);
            if (maxAllowedRam > freeRamMB - androidBufferMB) {
                maxAllowedRam = Math.max(512, freeRamMB - androidBufferMB);
            }

            String reason = context.getString(R.string.praetor_reason_ram_conflict, totalSystemRamMB, runningServersRamMB, requiredRamMB);
            Intent intent = new Intent(context, PraetorWarningActivity.class);
            intent.putExtra(PraetorWarningActivity.EXTRA_REASON, reason);
            intent.putExtra(PraetorWarningActivity.EXTRA_ACTION, "RAM_CONFLICT");
            intent.putExtra("extra_server_id", targetServer.getId());
            intent.putExtra("extra_max_ram", maxAllowedRam);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }
        return true;
    }

    private static void triggerWarning(Context context, String reason, String actionText) {
        Intent intent = new Intent(context, PraetorWarningActivity.class);
        intent.putExtra(PraetorWarningActivity.EXTRA_REASON, reason);
        intent.putExtra(PraetorWarningActivity.EXTRA_ACTION, actionText);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
