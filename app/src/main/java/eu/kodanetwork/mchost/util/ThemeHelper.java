package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import java.util.ArrayDeque;
import java.util.Deque;

import eu.kodanetwork.mchost.R;

/**
 * Tactical Design Enforcement System.
 * Strictly follows KodaClient website aesthetics.
 * Uses Non-Recursive traversal for absolute crash protection.
 * Supports Dark / Light / Auto theme modes.
 */
public class ThemeHelper {
    private static final String TAG = "ThemeHelper";
    private static final Handler main = new Handler(Looper.getMainLooper());

    // ─── Light Mode Colors ───────────────────────────────────────
    private static final int LIGHT_BG       = 0xFFFFFFFF; // Pure white background
    private static final int LIGHT_SURFACE  = 0xFFFFFFFF;
    private static final int LIGHT_CELL     = 0xFFF1F1F4; // neutral light gray for cards/boxes
    private static final int LIGHT_TEXT     = 0xFF111111;
    private static final int LIGHT_TEXT_SEC = 0xFF555555;
    private static final int LIGHT_HEADER   = 0xFFFFFFFF;
    private static final int LIGHT_STATUS   = 0xFFF1F1F4; // neutral light gray under the status bar

    // ─── Dark Mode Colors ────────────────────────────────────────
    private static final int DARK_BG        = 0xFF0A0807;
    private static final int DARK_SURFACE   = 0xFF16110D;
    private static final int DARK_CELL      = 0xFF16110D;
    private static final int DARK_TEXT      = 0xFFF0F0F0;
    private static final int DARK_TEXT_SEC  = 0xFF8A8A9A;
    private static final int DARK_HEADER    = 0xFF0A0807;
    private static final int DARK_STATUS    = 0xFF0A0807;

    /**
     * Resolve whether the effective theme is light.
     * "dark"  → false
     * "light" → true
     * "auto"  → check system Configuration.UI_MODE_NIGHT_MASK
     */
    public static boolean isLightMode(Context ctx) {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ctx);
        String mode = prefs.getString("theme_mode", "dark");
        if ("light".equals(mode)) return true;
        if ("dark".equals(mode)) return false;
        // auto – check system
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode != Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isLiquidGlass(android.content.Context context) {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(context);
        return prefs != null && prefs.getBoolean("dev_liquid_glass", false);
    }

    public static void apply(Activity activity) {
        // M3 Theme delegation
        if (Material3ThemeHelper.isM3Enabled(activity)) {
            Material3ThemeHelper.apply(activity);
            return;
        }
        apply(activity, null);
    }

    /**
     * Re-runs theming including views that were created after the initial pass
     * (adapter rows, dashboards, programmatic sheets). Clears the STYLED tags
     * first so the BFS actually visits every view again — this is what makes
     * light mode work outside Settings, where content is mostly static XML.
     */
    public static void reapply(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) clearStyledTags(root);
        apply(activity);
    }

    private static void clearStyledTags(View v) {
        if ("STYLED".equals(v.getTag(R.id.tag_themed))) {
            v.setTag(R.id.tag_themed, null);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) clearStyledTags(g.getChildAt(i));
        }
    }

    public static void apply(android.app.Dialog dialog) {
        if (dialog == null || !dialog.isShowing()) return;
        // M3 Theme delegation
        if (Material3ThemeHelper.isM3Enabled(dialog.getContext())) {
            Material3ThemeHelper.apply(dialog);
            return;
        }
        main.postDelayed(() -> {
            try {
                android.content.Context ctx = dialog.getContext();
                android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ctx);
                boolean lightMode = isLightMode(ctx);
                int themeColor = 0xFFFF6A00;
                boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));
                
                boolean isLiquidGlass = prefs.getBoolean("dev_liquid_glass", false);
                if (isLiquidGlass) {
                    // Optional: make dialog window translucent
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    }
                }
                
                View root = dialog.findViewById(android.R.id.content);
                if (root != null) {
                    applyToView(root, lightMode, themeColor, isCyber);
                }
            } catch (Exception ignored) {}
        }, 50);
    }

    public static void apply(Activity activity, String colorHex) {
        if (activity == null || activity.isFinishing()) return;

        // M3 Theme delegation
        if (Material3ThemeHelper.isM3Enabled(activity)) {
            Material3ThemeHelper.apply(activity);
            return;
        }

        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(activity);
        if (!prefs.getBoolean("animations_enabled", true)) {
            activity.getWindow().setWindowAnimations(0);
            activity.overridePendingTransition(0, 0);
        }

        // Run with a small delay to ensure activity is settled
        main.postDelayed(() -> {
            try {
                String themeName = prefs.getString("app_theme", "modern");
                boolean lightMode = isLightMode(activity);
                
                Log.d(TAG, "Protocol Execute: " + themeName + " light=" + lightMode + " on " + activity.getClass().getSimpleName());
                
                int themeColor = 0xFFFF6A00; // Tactical Orange
                boolean isLiquidGlass = prefs.getBoolean("dev_liquid_glass", false);
                if (isLiquidGlass) {
                    themeColor = 0xFFB388FF; // dev_purple
                } else if (colorHex != null && !colorHex.isEmpty()) {
                    try { themeColor = Color.parseColor(colorHex); } catch (Exception ignored) {}
                }
                boolean isPraetorDesign = activity instanceof eu.kodanetwork.mchost.ui.design.LoginPageActivity || 
                                          activity instanceof eu.kodanetwork.mchost.ui.design.RegisterPageActivity;

                View root = activity.findViewById(android.R.id.content);
                if (root == null) return;
                
                // 1. Background
                if (!isPraetorDesign && !isLiquidGlass) {
                    if (lightMode) {
                        root.setBackgroundColor(LIGHT_BG);
                    } else {
                        root.setBackgroundResource(R.drawable.bg_cyber_grid);
                    }
                } else if (isLiquidGlass) {
                    root.setBackgroundResource(R.drawable.bg_liquid_glass);
                }

                // 2. Iterative Traversal (BFS)
                Deque<View> stack = new ArrayDeque<>();
                stack.push(root);

                while (!stack.isEmpty()) {
                    View v = stack.pop();
                    if (v == null) continue;

                    // Skip if intentionally blocked
                    Object tag = v.getTag(R.id.tag_themed);
                    if ("BLOCKED".equals(tag)) {
                        // If it's a container, still add children just in case new ones appeared
                        if (v instanceof ViewGroup) {
                            ViewGroup g = (ViewGroup) v;
                            for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
                        }
                        continue;
                    }

                    // Ignore applying styles to Praetor screens' root views that might ruin them
                    if (isPraetorDesign && (v == root || v.getId() == R.id.tv_praetor_title)) {
                        // Do not style the title or root
                    } else {
                        // Don't apply 'STYLED' tag twice
                        if (!"STYLED".equals(tag)) {
                            v.setTag(R.id.tag_themed, "STYLED");

                            // Apply Styling
                            boolean isCyberLocal = "cyber".equals(themeName);
                            styleView(v, themeColor, isCyberLocal, lightMode, isLiquidGlass);
                        }
                    }

                    // Add children
                    if (v instanceof ViewGroup) {
                        ViewGroup g = (ViewGroup) v;
                        for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
                    }
                }
                
                // 3. System UI
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    boolean isCreateServer = activity instanceof eu.kodanetwork.mchost.ui.CreateServerActivity;
                    boolean isSupportChat = activity instanceof eu.kodanetwork.mchost.ui.SupportChatActivity;

                    androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                            androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
                    
                    if (isPraetorDesign) {
                        activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
                        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                    } else if (isLiquidGlass) {
                        activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
                        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                        activity.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_liquid_glass);
                        if (!isSupportChat) {
                            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                        }
                    } else if (lightMode) {
                        activity.getWindow().setStatusBarColor(LIGHT_STATUS);
                        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            activity.getWindow().setNavigationBarContrastEnforced(false);
                        }
                        if (windowInsetsController != null) {
                            windowInsetsController.setAppearanceLightStatusBars(true);
                            windowInsetsController.setAppearanceLightNavigationBars(true);
                        }
                        if (!isSupportChat) {
                            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                        }
                    } else {
                        boolean isSettings = activity instanceof eu.kodanetwork.mchost.ui.SettingsActivity;
                        int darkBg = (isCreateServer || isSettings) ? 0xFF1B1613 : DARK_STATUS;
                        activity.getWindow().setStatusBarColor(darkBg);
                        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            activity.getWindow().setNavigationBarContrastEnforced(false);
                        }
                        if (windowInsetsController != null) {
                            windowInsetsController.setAppearanceLightStatusBars(false);
                            windowInsetsController.setAppearanceLightNavigationBars(false);
                        }
                        if (!isSupportChat) {
                            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Tactical Design Failed: " + t.getMessage());
            }
        }, 150);
    }

    public static void applyToView(View root, boolean lightMode, int themeColor, boolean isCyber) {
        if (root == null) return;
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(root.getContext());
        // M3 Theme delegation
        if (Material3ThemeHelper.isM3Enabled(root.getContext())) {
            Material3ThemeHelper.styleTree(root, Material3ThemeHelper.resolveRoles(root.getContext()));
            return;
        }
        boolean isLiquidGlass = prefs.getBoolean("dev_liquid_glass", false);
        if (isLiquidGlass) {
            themeColor = 0xFFB388FF;
        }
        Deque<View> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            View v = stack.pop();
            if (v == null) continue;

            Object tag = v.getTag(R.id.tag_themed);
            if ("BLOCKED".equals(tag)) {
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
                }
                continue;
            }

            if (!"STYLED".equals(tag)) {
                v.setTag(R.id.tag_themed, "STYLED");
                styleView(v, themeColor, isCyber, lightMode, isLiquidGlass);
            }

            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
            }
        }
    }

    private static void styleView(View v, int themeColor, boolean isCyber, boolean lightMode, boolean isLiquidGlass) {
        try {
            boolean isButton = (v instanceof Button || v instanceof MaterialButton || v.getClass().getSimpleName().contains("Button"))
                               && !(v instanceof android.widget.CompoundButton);
            boolean isSwitch = v instanceof android.widget.Switch || v instanceof androidx.appcompat.widget.SwitchCompat;

            // ═══════════════════════════════════════════════════════
            // DARK / LIGHT / CYBER paths
            // ═══════════════════════════════════════════════════════
            if (v instanceof TextView && !(v instanceof Button) && !v.getClass().getSimpleName().contains("Button")) {
                TextView tv = (TextView) v;
                String text = tv.getText().toString();
                
                float density = tv.getContext().getResources().getDisplayMetrics().scaledDensity;
                float sizeSp = tv.getTextSize() / (density > 0 ? density : 1f);
                
                boolean isHeader = (sizeSp > 26 || tv.getId() == R.id.tv_server_count || tv.getId() == R.id.tv_title);
                
                if (isHeader) {
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    tv.setAllCaps(true);
                    tv.setLetterSpacing(0.2f);
                    tv.setTextColor(themeColor);
                    tv.setShadowLayer(lightMode ? 0 : 8, 0, 0, themeColor);
                    if (isCyber && !lightMode && !text.isEmpty() && !text.startsWith("[") && text.length() < 25 && !text.contains("\n")) {
                        tv.setText("[ " + text.toUpperCase() + " ]");
                    }
                } else {
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
                    tv.setLetterSpacing(0.06f);
                    
                    int current = tv.getTextColors().getDefaultColor();

                    if (lightMode) {
                        if (current == 0xFF888888 || current == 0xFF2A2A2A || current == 0xFF444444
                            || current == 0xFF555566 || current == 0xFF8A8A9A || current == DARK_TEXT_SEC) {
                            tv.setTextColor(LIGHT_TEXT_SEC);
                        } else if (current == Color.WHITE || current == 0xFFEEEEEE || current == 0xFFF0F0F0
                                   || current == DARK_TEXT || current == 0xFFDDDDDD || current == 0xFFBBBBCC) {
                            tv.setTextColor(LIGHT_TEXT);
                        }
                    } else {
                        if (current == 0xFF888888 || current == 0xFF2A2A2A || current == 0xFF444444) {
                            if (isCyber && !text.isEmpty() && !text.startsWith("//") && text.length() < 30 && !text.contains("\n")) {
                                tv.setText("// " + text.toUpperCase());
                            }
                        } else if (current == Color.WHITE || current == 0xFFEEEEEE || current == 0xFFF0F0F0) {
                            tv.setTextColor(0xFFDDDDDD);
                        }
                    }
                    tv.setShadowLayer(0, 0, 0, 0);
                }
            }

            if (isSwitch) {
                if (isLiquidGlass && v instanceof androidx.appcompat.widget.SwitchCompat) {
                    androidx.appcompat.widget.SwitchCompat sw = (androidx.appcompat.widget.SwitchCompat) v;
                    int[][] states = new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] { -android.R.attr.state_checked }
                    };
                    int[] thumbColors = new int[] {
                        0xFFB388FF, // dev_purple checked
                        0xFFE0E0E0  // light gray unchecked
                    };
                    int[] trackColors = new int[] {
                        0x88B388FF, // semi-transparent purple checked
                        0x44FFFFFF  // translucent white glass unchecked
                    };
                    sw.setThumbTintList(new android.content.res.ColorStateList(states, thumbColors));
                    sw.setTrackTintList(new android.content.res.ColorStateList(states, trackColors));
                }
            }

            if (isButton) {
                if (isLiquidGlass) {
                    v.setBackgroundResource(R.drawable.bg_glass_button);
                    v.setOnTouchListener((view, motionEvent) -> {
                        switch (motionEvent.getAction()) {
                            case android.view.MotionEvent.ACTION_DOWN:
                                view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start();
                                break;
                            case android.view.MotionEvent.ACTION_UP:
                            case android.view.MotionEvent.ACTION_CANCEL:
                                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                                break;
                        }
                        return false;
                    });
                } else if (isCyber && !lightMode) {
                    v.setBackgroundResource(R.drawable.cyber_button_bg);
                }
                
                if (v instanceof TextView) {
                    TextView btv = (TextView) v;
                    btv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    btv.setAllCaps(true);
                    btv.setShadowLayer(0, 0, 0, 0);
                    
                    int currentText = btv.getTextColors().getDefaultColor();
                    if (lightMode) {
                        if (currentText == Color.WHITE || currentText == 0xFFF0F0F0 || currentText == 0xFFEEEEEE || currentText == 0xFFDDDDDD || currentText == DARK_TEXT) {
                            btv.setTextColor(LIGHT_TEXT);
                        } else if (currentText == 0xFF8A8A9A || currentText == 0xFF888888) {
                            btv.setTextColor(LIGHT_TEXT_SEC);
                        }
                    } else if (isCyber) {
                        btv.setTextColor(Color.WHITE);
                    } else {
                        if (currentText != themeColor && currentText != 0xFFFF6A00) {
                            btv.setTextColor(Color.WHITE);
                        }
                    }
                }
            } // Close if (isButton)
            if (v instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) v;
                if (lightMode) {
                    mb.setElevation(0f);
                    mb.setStateListAnimator(null); // Removes shadow entirely
                    // Set corner radius explicitly since we stripped background styles
                    boolean isTarget = mb.getId() == R.id.btn_import || mb.getId() == R.id.btn_import_zip 
                                    || mb.getId() == R.id.btn_copy_log || mb.getId() == R.id.btn_clear;
                    int tint = mb.getBackgroundTintList() != null ? mb.getBackgroundTintList().getDefaultColor() : 0;
                    
                    if (isTarget || (isDark(tint) && !isOrange(tint) && tint != 0)) {
                        mb.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEDEDED));
                        mb.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFF8C38)); // Orange stroke
                        mb.setStrokeWidth(3);
                        mb.setTextColor(0xFF111111); // Force visible text color
                        if (mb.getIconTint() != null) {
                            int icTint = mb.getIconTint().getDefaultColor();
                            if (icTint == Color.WHITE || icTint == 0xFFF0F0F0 || icTint == 0xFFEEEEEE || icTint == 0xFFDDDDDD) {
                                mb.setIconTint(android.content.res.ColorStateList.valueOf(0xFF555566));
                            }
                        }
                    }
                }
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight() + 8, v.getPaddingBottom());
            } else if (v.getId() == R.id.btn_back && v instanceof android.widget.ImageButton) {
                if (lightMode) {
                    ((android.widget.ImageButton) v).setColorFilter(0xFFFF8C38);
                }
            } else if ((v instanceof ViewGroup || v instanceof android.widget.EditText) 
                    && v.getBackground() != null 
                    && v.getId() != android.R.id.content 
                    && !(v instanceof android.widget.NumberPicker)
                    && !v.getClass().getSimpleName().equals("SwipeRefreshLayout")
                    && !v.getClass().getSimpleName().equals("RecyclerView")) {
                if (lightMode) {
                    try {
                        android.graphics.drawable.Drawable bg = v.getBackground();
                        if (bg instanceof android.graphics.drawable.GradientDrawable) {
                            android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) bg;
                            gd.setColor(LIGHT_CELL);
                            gd.setStroke(0, 0); // No orange stroke for internal card backgrounds like stats_card_bg
                        } else if (v instanceof android.widget.EditText) {
                            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                            gd.setColor(LIGHT_CELL);
                            gd.setStroke(3, 0xFFFF8C38);
                            gd.setCornerRadius(16f); // slight corner for text inputs
                            int pL = v.getPaddingLeft(), pT = v.getPaddingTop(), pR = v.getPaddingRight(), pB = v.getPaddingBottom();
                            v.setBackground(gd);
                            v.setPadding(pL, pT, pR, pB);
                        } else {
                            // Safe fallback for generic ViewGroups (Headers, Tabs, Lists)
                            String idName = "";
                            try {
                                if (v.getId() != android.view.View.NO_ID) {
                                    idName = v.getResources().getResourceEntryName(v.getId());
                                }
                            } catch (Exception e) {}
                            
                            int pL = v.getPaddingLeft(), pT = v.getPaddingTop(), pR = v.getPaddingRight(), pB = v.getPaddingBottom();
                            boolean isFullScreen = v.getLayoutParams() != null && v.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT && v.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT;
                            
                            if ("app_top_bar".equals(idName)) {
                                v.setBackgroundColor(LIGHT_CELL);
                            } else if (isFullScreen || "tabs".equals(idName) || v.getClass().getSimpleName().contains("Tab")) {
                                v.setBackgroundColor(LIGHT_BG); // Blend in headers/tabs and root layers
                            } else if (!"card_players".equals(idName) && !idName.startsWith("layout_header") 
                                    && !(bg instanceof android.graphics.drawable.RippleDrawable) 
                                    && !(bg instanceof android.graphics.drawable.StateListDrawable)) {
                                // Create bounded boxes for everything else that had a background (like CardViews)
                                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                                gd.setColor(LIGHT_CELL);
                                gd.setStroke(3, 0xFFFF8C38);
                                gd.setCornerRadius(24f); 
                                v.setBackground(gd);
                            }
                            v.setPadding(pL, pT, pR, pB);
                        }
                    } catch (Exception e) {
                        v.setBackgroundColor(LIGHT_CELL);
                    }
                } else if (isLiquidGlass) {
                    try {
                        String idName = "";
                        if (v.getId() != android.view.View.NO_ID) {
                            idName = v.getResources().getResourceEntryName(v.getId());
                        }
                        boolean isFullScreen = v.getLayoutParams() != null && v.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT && v.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT;
                        
                        if (isFullScreen || "tabs".equals(idName) || v.getClass().getSimpleName().contains("Tab") 
                                || "app_top_bar".equals(idName) || "main_top_bar".equals(idName) || "main_bottom_bar".equals(idName)
                                || "card_players".equals(idName) || "top_bar_container".equals(idName) || "files_path_container".equals(idName) || idName.startsWith("layout_header")) {
                            // Don't draw the glass panel box on full screen roots or tab bars
                            if ("main_top_bar".equals(idName) || "app_top_bar".equals(idName) || "top_bar_container".equals(idName)) {
                                v.setBackgroundColor(Color.TRANSPARENT);
                            } else if (android.os.Build.VERSION.SDK_INT >= 21 && !idName.equals("card_players") && !idName.startsWith("layout_header")) {
                                v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33000000));
                            } else {
                                v.setBackgroundColor(Color.TRANSPARENT);
                            }
                        } else {
                            if (v instanceof android.widget.EditText) {
                                ((android.widget.EditText) v).setHintTextColor(0x88FFFFFF);
                            }
                            int pL = v.getPaddingLeft(), pT = v.getPaddingTop(), pR = v.getPaddingRight(), pB = v.getPaddingBottom();
                            v.setBackgroundResource(R.drawable.bg_glass_panel);
                            v.setPadding(pL, pT, pR, pB);
                            
                            if (v instanceof androidx.cardview.widget.CardView) {
                                ((androidx.cardview.widget.CardView) v).setCardElevation(0f);
                                ((androidx.cardview.widget.CardView) v).setCardBackgroundColor(Color.TRANSPARENT);
                            }
                        }
                    } catch (Exception e) {}
                } else if (isCyber) {
                    v.setBackgroundResource(R.drawable.cell_cyber_bg);
                    v.setPadding(40, 40, 40, 40);
                }
            }
        } catch (Exception ignored) {}
    }

    /** Check if a color is any shade of orange used in the app */
    private static boolean isOrange(int color) {
        return color == 0xFFFF6B00 || color == 0xFFFF6A00 || color == 0xFFCC5500
            || color == 0xFFFF8C38 || color == 0xFFE65100;
    }

    /** Check if a color is dark (brightness < 50) */
    private static boolean isDark(int color) {
        return brightness(color) < 50;
    }

    /** Get perceived brightness 0-255 */
    private static int brightness(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (int)(0.299 * r + 0.587 * g + 0.114 * b);
    }
}
