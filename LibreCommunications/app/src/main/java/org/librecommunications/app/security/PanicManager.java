package org.librecommunications.app.security;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.librecommunications.app.crypto.KeyStoreManager;

import java.io.File;

public class PanicManager {

    private static final String TAG = "PanicManager";
    private static final String PANIC_PREFS = "panic_preferences";

    public static void triggerPanic(Context context) {
        Log.w(TAG, "Panic mode triggered! Executing emergency protocols.");
        
        // 1. Wipe cryptographic keys
        wipeCryptoKeys();

        // 2. Clear application data
        clearApplicationData(context);

        // 3. Clear SharedPreferences
        clearSharedPreferences(context);

        // 4. Optionally attempt self-uninstall
        promptSelfUninstall(context);

        // 5. Kill the app process immediately
        killAppProcess();
    }

    private static void wipeCryptoKeys() {
        try {
            KeyStoreManager.getInstance().clearKey();
            Log.i(TAG, "Cryptographic keys wiped successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to wipe cryptographic keys.", e);
        }
    }

    private static void clearApplicationData(Context context) {
        try {
            File cacheDirectory = context.getCacheDir();
            File applicationDirectory = new File(cacheDirectory.getParent());
            if (applicationDirectory.exists()) {
                String[] fileNames = applicationDirectory.list();
                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        if (!fileName.equals("lib")) {
                            deleteFile(new File(applicationDirectory, fileName));
                        }
                    }
                }
            }
            Log.i(TAG, "Application data cleared.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear application data.", e);
        }
    }

    private static boolean deleteFile(File file) {
        boolean deletedAll = true;
        if (file != null) {
            if (file.isDirectory()) {
                String[] children = file.list();
                if (children != null) {
                    for (String child : children) {
                        deletedAll = deleteFile(new File(file, child)) && deletedAll;
                    }
                }
            }
            return file.delete() && deletedAll;
        }
        return false;
    }

    private static void clearSharedPreferences(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PANIC_PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            Log.i(TAG, "SharedPreferences cleared.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear SharedPreferences.", e);
        }
    }

    private static void promptSelfUninstall(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch uninstall intent.", e);
        }
    }

    private static void killAppProcess() {
        Log.i(TAG, "Terminating application process.");
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}
