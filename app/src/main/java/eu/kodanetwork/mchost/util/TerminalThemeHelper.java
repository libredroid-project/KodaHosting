package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import eu.kodanetwork.mchost.App;
import eu.kodanetwork.mchost.R;

public class TerminalThemeHelper {

    public static boolean isTerminalEnabled(Context ctx) {
        return App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false);
    }

    public static void applyThemeToView(Context ctx, View root) {
        if (!isTerminalEnabled(ctx)) return;

        // Change background of root if it's a bottom sheet or dialog root (often has a white/gray bg)
        // Let's not touch root bg to avoid breaking shapes, but we can do it if needed.
        
        
        // Force dark background on the root if it's likely a container
        if (root instanceof ViewGroup) {
            root.setBackgroundColor(Color.parseColor("#111111"));
        }

        applyToChildren(ctx, root);
    }

    private static void applyToChildren(Context ctx, View view) {
        if (view instanceof Button) {
            Button btn = (Button) view;
            String text = btn.getText() != null ? btn.getText().toString().toLowerCase() : "";
            
            int bgRes = R.drawable.bg_mc_button_dark;
            if (text.contains("start") || text.contains("save") || text.contains("apply") || text.contains("add") || text.contains("yes") || text.contains("create") || text.contains("install")) {
                bgRes = R.drawable.bg_mc_button_green;
            } else if (text.contains("stop") || text.contains("kill") || text.contains("delete") || text.contains("remove") || text.contains("no") || text.contains("ban") || text.contains("cancel")) {
                bgRes = R.drawable.bg_mc_button_red;
            } else if (text.contains("restart") || text.contains("edit") || text.contains("update") || text.contains("change")) {
                bgRes = R.drawable.bg_mc_button_orange;
            }
            
            btn.setBackgroundResource(bgRes);
            
            if (btn instanceof MaterialButton) {
                ((MaterialButton) btn).setBackgroundTintList(null);
                ((MaterialButton) btn).setCornerRadius(0);
            }
            
            btn.setTextColor(Color.WHITE);
            // Optionally apply monospace:
            btn.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            
        } else if (view instanceof TextView) {
            // Optional: convert other text to monospace
            
            TextView tv = (TextView) view;
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            // Don't override completely if they already have specific colors, but we can't easily check current color.
            // Let's just set it to #DDDDDD (light gray) so it's visible on dark bg.
            tv.setTextColor(Color.parseColor("#DDDDDD"));

        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToChildren(ctx, group.getChildAt(i));
            }
        }
    }
}
