package eu.kodanetwork.mchost;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import eu.kodanetwork.mchost.ui.ScreensaverActivity;

public class App extends Application implements Application.ActivityLifecycleCallbacks {
    private static App instance;
    private Handler afkHandler = new Handler(Looper.getMainLooper());
    private int activeActivities = 0;
    private Activity currentActivity;

    private static SharedPreferences cachedPrefs;

    public static SharedPreferences getPrefs(Context context) {
        if (cachedPrefs != null) {
            return cachedPrefs;
        }
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            cachedPrefs = EncryptedSharedPreferences.create(
                    context,
                    "koda_settings_enc",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            return cachedPrefs;
        } catch (java.security.GeneralSecurityException se) {
            // Emulators often have broken keystores that trigger this
            if (eu.kodanetwork.mchost.security.AntiTamperSystem.isEmulator()) {
                eu.kodanetwork.mchost.security.AntiTamperSystem.executeLocalEmulatorBan(context);
            } else {
                // Tamper detected! The file was modified by root/ADB, invalidating the MAC.
                eu.kodanetwork.mchost.security.AntiTamperSystem.executePermanentBan(context, "FILE_TAMPER_DETECTED");
            }
            android.util.Log.e("KodaNetwork", "Security Warning: Falling back to unencrypted SharedPreferences (KeyStore error)", se);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                android.widget.Toast.makeText(context, "Security Warning: Device does not support encrypted storage.", android.widget.Toast.LENGTH_LONG).show();
            }
            cachedPrefs = context.getSharedPreferences("koda_settings_enc_fallback", Context.MODE_PRIVATE);
            return cachedPrefs;
        } catch (Exception e) {
            android.util.Log.e("KodaNetwork", "Security Warning: Falling back to unencrypted SharedPreferences", e);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                android.widget.Toast.makeText(context, "Security Warning: Device does not support encrypted storage.", android.widget.Toast.LENGTH_LONG).show();
            }
            cachedPrefs = context.getSharedPreferences("koda_settings_enc_fallback", Context.MODE_PRIVATE);
            return cachedPrefs;
        }
    }

    private Runnable afkRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeActivities > 0 && currentActivity != null && !(currentActivity instanceof ScreensaverActivity) 
                && !(currentActivity instanceof eu.kodanetwork.mchost.ui.CreateServerActivity)) {
                SharedPreferences prefs = getPrefs(App.this);
                String style = prefs.getString("afk_style", "squares");
                if (!"off".equals(style)) {
                    Intent intent = new Intent(currentActivity, ScreensaverActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                }
            }
        }
    };

    private boolean isColdStart = true;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        SharedPreferences prefs = getPrefs(this);
        applyLanguage(this, prefs.getString("language", "system"));
        
        registerActivityLifecycleCallbacks(this);

        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
                boolean wasCold = isColdStart;
                isColdStart = false;

                if (wasCold) {
                    if (eu.kodanetwork.mchost.util.BiometricHelper.isBioEnabledFor(App.this, "bio_on_cold_start")) {
                        eu.kodanetwork.mchost.util.BiometricHelper.startAuthStandalone(App.this, true);
                    }
                } else {
                    if (eu.kodanetwork.mchost.util.BiometricHelper.isBioEnabledFor(App.this, "bio_on_resume")) {
                        eu.kodanetwork.mchost.util.BiometricHelper.startAuthStandalone(App.this, true);
                    }
                }
            }
        });
    }

    public static void resetAfkTimer() {
        if (instance != null) instance.doResetAfkTimer();
    }

    private void doResetAfkTimer() {
        afkHandler.removeCallbacks(afkRunnable);
        SharedPreferences prefs = getPrefs(App.this);
        String style = prefs.getString("afk_style", "squares");
        if (!"off".equals(style)) {
            int timeoutMins = prefs.getInt("afk_timeout", 1);
            afkHandler.postDelayed(afkRunnable, timeoutMins * 60 * 1000L);
        }
    }

    public static void applyLanguage(Context context, String languageCode) {
        if ("system".equals(languageCode) || languageCode == null || languageCode.isEmpty()) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList());
            return;
        }
        androidx.core.os.LocaleListCompat appLocale = androidx.core.os.LocaleListCompat.create(new java.util.Locale(languageCode));
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(@NonNull Activity activity) {
        activeActivities++;
        eu.kodanetwork.mchost.service.KodaServerService.appIsForeground = true;
        if (activeActivities == 1) doResetAfkTimer();

        try {
            android.view.View root = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            if (root != null && root.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
                int color = ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor();
                android.view.Window w = activity.getWindow();
                w.setBackgroundDrawable(root.getBackground());
                w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                
                boolean isLight = androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5;
                androidx.core.view.WindowInsetsControllerCompat controller = new androidx.core.view.WindowInsetsControllerCompat(w, w.getDecorView());
                controller.setAppearanceLightStatusBars(isLight);
                controller.setAppearanceLightNavigationBars(isLight);
                
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
                
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                    androidx.core.graphics.Insets sysBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    androidx.core.graphics.Insets tappable = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.tappableElement());
                    
                    boolean isGesture = sysBars.bottom > 0 && tappable.bottom < sysBars.bottom;
                    
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        w.setNavigationBarDividerColor(android.graphics.Color.TRANSPARENT);
                    }
                    if (isGesture) {
                        w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                        android.view.View bottomBar = activity.findViewById(activity.getResources().getIdentifier("main_bottom_bar", "id", activity.getPackageName()));
                        if (bottomBar != null) {
                            bottomBar.setPadding(bottomBar.getPaddingLeft(), 0, bottomBar.getPaddingRight(), sysBars.bottom);
                            v.setPadding(0, sysBars.top, 0, 0);
                        } else {
                            v.setPadding(0, sysBars.top, 0, sysBars.bottom);
                        }
                    } else {
                        w.setNavigationBarColor(color);
                        v.setPadding(0, sysBars.top, 0, sysBars.bottom);
                    }
                    return insets;
                });
            }
        } catch (Exception ignored) {}
    }
    @Override public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        doResetAfkTimer();
        eu.kodanetwork.mchost.util.ChineseDictionaryHelper.applyToActivity(activity);
    }
    @Override public void onActivityPaused(@NonNull Activity activity) {
        if (currentActivity == activity) currentActivity = null;
        if (!getPrefs(activity).getBoolean("animations_enabled", true)) {
            activity.overridePendingTransition(0, 0);
        }
    }
    @Override public void onActivityStopped(@NonNull Activity activity) {
        activeActivities--;
        if (activeActivities == 0) {
            eu.kodanetwork.mchost.service.KodaServerService.appIsForeground = false;
            afkHandler.removeCallbacks(afkRunnable);
        }
    }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
