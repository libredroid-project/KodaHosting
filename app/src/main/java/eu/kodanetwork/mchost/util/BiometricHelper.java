package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import eu.kodanetwork.mchost.ui.BiometricAuthActivity;

public class BiometricHelper {

    public static final int REQ_BIO_AUTH = 9005;
    public static final String EXTRA_KILL_ON_CANCEL = "kill_on_cancel";

    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences sp = eu.kodanetwork.mchost.App.getPrefs(context);
        return sp.getBoolean("bio_enabled", false);
    }

    public static boolean isBioEnabledFor(Context context, String key) {
        if (!isBiometricEnabled(context)) return false;
        SharedPreferences sp = eu.kodanetwork.mchost.App.getPrefs(context);
        return sp.getBoolean(key, false);
    }

    public static void startAuth(android.app.Activity activity, boolean killOnCancel) {
        Intent intent = new Intent(activity, BiometricAuthActivity.class);
        intent.putExtra(EXTRA_KILL_ON_CANCEL, killOnCancel);
        activity.startActivityForResult(intent, REQ_BIO_AUTH);
    }

    public static void startAuthStandalone(Context context, boolean killOnCancel) {
        Intent intent = new Intent(context, BiometricAuthActivity.class);
        intent.putExtra(EXTRA_KILL_ON_CANCEL, killOnCancel);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
