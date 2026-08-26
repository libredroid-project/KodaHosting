package eu.kodanetwork.mchost.util;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * Landscape fix for the tall custom dialogs (Praetor / ToS / join address):
 * content gets wrapped in a ScrollView so it never clips, and in landscape
 * the dialog moves to the LEFT side (62% width) instead of overflowing the
 * short screen height in the middle.
 */
public class DialogLandFix {

    public static void apply(Dialog d) {
        if (d == null || d.getWindow() == null) return;
        try {
            ViewGroup content = (ViewGroup) d.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return;

            // wrap the content in a ScrollView so tall dialogs scroll instead of clipping
            View child = content.getChildAt(0);
            if (!(child instanceof ScrollView)) {
                android.view.ViewGroup.LayoutParams oldLp = child.getLayoutParams();
                content.removeView(child);
                ScrollView scroller = new ScrollView(d.getContext());
                scroller.addView(child, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                content.addView(scroller, 0, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            boolean land = d.getContext().getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            if (land) {
                int w = (int)(d.getContext().getResources().getDisplayMetrics().widthPixels * 0.62f);
                d.getWindow().setLayout(w, ViewGroup.LayoutParams.MATCH_PARENT);
                d.getWindow().setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            }
        } catch (Exception ignored) {}
    }
}
