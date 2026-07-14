package org.librecommunications.app.security;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class StealthManager {

    private static final String TAG = "StealthManager";

    // Assuming the main launcher activity name
    private static final String ALIAS_ACTIVITY_NAME = "org.librecommunications.app.MainActivity";
    private static final String FAKE_CRASH_ACTIVITY_NAME = "org.librecommunications.app.FakeCrashActivity";

    public static void enableStealthMode(Context context) {
        Log.i(TAG, "Enabling stealth mode.");
        hideAppIcon(context);
    }

    public static void disableStealthMode(Context context) {
        Log.i(TAG, "Disabling stealth mode.");
        showAppIcon(context);
    }

    private static void hideAppIcon(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, ALIAS_ACTIVITY_NAME);
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
            Log.i(TAG, "App icon hidden successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide app icon.", e);
        }
    }

    private static void showAppIcon(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, ALIAS_ACTIVITY_NAME);
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
            Log.i(TAG, "App icon restored successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore app icon.", e);
        }
    }

    public static boolean isStealthModeEnabled(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, ALIAS_ACTIVITY_NAME);
            int state = packageManager.getComponentEnabledSetting(componentName);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || 
                   state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (Exception e) {
            Log.e(TAG, "Error checking stealth mode state.", e);
            return false;
        }
    }
    
    public static void enableFakeCrash(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, FAKE_CRASH_ACTIVITY_NAME);
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
            Log.i(TAG, "Fake crash alias enabled.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable fake crash.", e);
        }
    }
}
