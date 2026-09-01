package eu.kodanetwork.mchost.util;

import android.app.Dialog;
import android.view.ViewGroup;
import android.view.Window;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Makes BottomSheets usable on tablets and in landscape.
 * Default sheets are WRAP_CONTENT — on tablets they look tiny ("not reaching the
 * corners") and in landscape they collapse to a sliver. This forces the sheet to
 * open fully expanded at ~90% screen height, caps its width at 640dp on wide
 * screens (centered), and keeps it edge-to-edge.
 */
public final class SheetFix {

    private SheetFix() {}

    public static void apply(Dialog dialog) {
        if (!(dialog instanceof BottomSheetDialog)) return;
        BottomSheetDialog sheet = (BottomSheetDialog) dialog;
        Window w = sheet.getWindow();
        if (w == null) return;

        android.util.DisplayMetrics dm = w.getContext().getResources().getDisplayMetrics();
        float density = dm.density;
        boolean wide = dm.widthPixels / density >= 900;

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setFitToContents(false);
        // 90% of screen height so it never collapses to a sliver in landscape
        sheet.getBehavior().setPeekHeight((int) (dm.heightPixels * 0.9));

        if (wide) {
            // side-sheet look: capped width, centered, almost full height
            int maxWidth = (int) (640 * density);
            int height = (int) (dm.heightPixels * 0.94);
            w.setLayout(Math.min(maxWidth, dm.widthPixels), height);
            w.setGravity(android.view.Gravity.CENTER);
        }

        // let the content itself grow instead of wrapping at its (small) natural size
        android.view.View contentRoot = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (contentRoot != null) {
            ViewGroup.LayoutParams lp = contentRoot.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            contentRoot.setLayoutParams(lp);
        }
    }
}
