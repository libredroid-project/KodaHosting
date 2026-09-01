package eu.kodanetwork.mchost.util;

import android.app.Dialog;
import android.view.Window;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Tablet-only bottom sheet helper: on wide screens (>= 900dp) it caps the sheet
 * width at 640dp and centers it so it does not stretch across the whole display.
 * On phones and for the sheet HEIGHT it changes NOTHING — sheets keep their
 * natural half-height/collapse behavior.
 */
public final class SheetFix {

    private SheetFix() {}

    public static void apply(Dialog dialog) {
        if (!(dialog instanceof BottomSheetDialog)) return;
        Window w = dialog.getWindow();
        if (w == null) return;

        android.util.DisplayMetrics dm = w.getContext().getResources().getDisplayMetrics();
        float density = dm.density;
        if (dm.widthPixels / density < 900) return; // phones: completely untouched

        int maxWidth = (int) (640 * density);
        w.setLayout(Math.min(maxWidth, dm.widthPixels), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(android.view.Gravity.CENTER);
    }
}
