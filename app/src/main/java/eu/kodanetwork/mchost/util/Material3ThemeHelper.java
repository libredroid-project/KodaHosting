package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.material.color.DynamicColors;
import eu.kodanetwork.mchost.App;

public class Material3ThemeHelper {

    public static final int[] COLOR_PRESETS = {
            0xFF6750A4, // Material Purple
            0xFFB3261E, // Ruby Red
            0xFF006874, // Ocean Blue
            0xFF006C4C, // Teal
            0xFF386A20, // Forest Green
            0xFF825500, // Amber
            0xFFFF6B00, // KodaHosting Orange
            0xFF4A4458  // Midnight Slate
    };

    public static boolean isM3Enabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = App.getPrefs(context);
        return prefs.getBoolean("dev_material3_enabled", false);
    }

    public static String getColorMode(Context context) {
        if (context == null) return "koda";
        return App.getPrefs(context).getString("m3_color_mode", "koda");
    }

    public static int getCustomColor(Context context) {
        if (context == null) return 0xFF6750A4;
        return App.getPrefs(context).getInt("m3_custom_color", 0xFF6750A4);
    }

    public static void applyM3Theme(Activity activity) {
        if (activity == null) return;
        
        String mode = getColorMode(activity);
        if ("dynamic".equals(mode)) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    public static void apply(Activity activity) {
        apply(activity, null);
    }

    public static void apply(Activity activity, String colorHex) {
        if (isM3Enabled(activity)) {
            applyM3Theme(activity);
        }
    }
}
