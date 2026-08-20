package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** Programmatic Koda-style buttons: 12dp rounded like every button in the app. */
public class KodaButtons {

    public static com.google.android.material.button.MaterialButton make(Context c, String text, int bgColor, int textColor) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(c);
        b.setText(text);
        b.setTextColor(textColor);
        b.setStateListAnimator(null);
        b.setLetterSpacing(0.04f);
        b.setTextSize(13);
        b.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, eu.kodanetwork.mchost.R.font.font_koda),
                android.graphics.Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12 * c.getResources().getDisplayMetrics().density);
        bg.setColor(bgColor);
        b.setBackground(bg);
        return b;
    }

    /** Primary orange button (black text), like DANGER ZONE buttons' shape. */
    public static com.google.android.material.button.MaterialButton primary(Context c, String text) {
        return make(c, text, 0xFFFF6B00, 0xFF000000);
    }

    /** Dark card button with white text. */
    public static com.google.android.material.button.MaterialButton dark(Context c, String text) {
        return make(c, text, 0xFF241C18, 0xFFF0F0F0);
    }
}
