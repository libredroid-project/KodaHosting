package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;

/**
 * Sleek design system: warm-premium dark theme with cream cards and pill buttons.
 * Dev-switch gated behind `dev_sleek_enabled`. Takes priority over M3 and legacy.
 */
public class SleekThemeHelper {

    public static boolean isSleekEnabled(Context ctx) {
        return App.getPrefs(ctx).getBoolean("dev_sleek_enabled", false);
    }

    /** Call before super.onCreate */
    public static void applyTheme(Activity activity) {
        if (!isSleekEnabled(activity)) return;
        activity.setTheme(R.style.Theme_KodaNetwork_Sleek);
        activity.getWindow().setStatusBarColor(0xFF1C1917);
        activity.getWindow().setNavigationBarColor(0xFF1C1917);
    }

    /** Light-touch styling for legacy views that live inside sleek screens */
    public static void apply(Activity activity) {
        if (!isSleekEnabled(activity)) return;
        View root = activity.findViewById(android.R.id.content);
        if (root != null) styleTree(root);
    }

    private static void styleTree(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            // Warm text colors instead of the cold blue-gray
            int color = tv.getCurrentTextColor();
            if (color == 0xFF8A8A9A) tv.setTextColor(0xFF9C968F);
            else if (color == 0xFF555566) tv.setTextColor(0xFF5C5852);
            else if (color == 0xFFF0F0F0) tv.setTextColor(0xFFFFFFFF);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) styleTree(g.getChildAt(i));
        }
    }
}
