package org.librecommunications.app.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import org.librecommunications.app.SecureStorage;

import java.util.Locale;

/**
 * LocaleHelper — Static utility for managing app locale independently of system locale.
 * <p>
 * Persists the chosen language code to EncryptedSharedPreferences and applies it
 * by wrapping the base context with the correct {@link Configuration}.
 * <p>
 * Usage: call {@link #onAttach(Context)} from every Activity's
 * {@code attachBaseContext()} to ensure the locale is applied before inflation.
 */
public final class LocaleHelper {

    private static final String PREF_KEY_LANGUAGE = "pref_language";
    private static final String DEFAULT_LANGUAGE = "en";

    private LocaleHelper() {
        // Prevent instantiation
    }

    /**
     * Called from {@code Activity.attachBaseContext()} to wrap the context with
     * the persisted locale before any layout inflation occurs.
     *
     * @param base the base context provided by the framework
     * @return a context configured with the user's chosen locale
     */
    @NonNull
    public static Context onAttach(@NonNull Context base) {
        String lang = getPersistedLocale(base);
        return setLocale(base, lang);
    }

    /**
     * Creates a new context configured with the given locale.
     *
     * @param context      the original context
     * @param languageCode ISO 639-1 language code (e.g. "en", "de")
     * @return a wrapped context with the locale applied
     */
    @NonNull
    public static Context setLocale(@NonNull Context context, @NonNull String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);

        LocaleList localeList = new LocaleList(locale);
        LocaleList.setDefault(localeList);
        config.setLocales(localeList);

        return context.createConfigurationContext(config);
    }

    /**
     * Reads the persisted locale code from EncryptedSharedPreferences.
     *
     * @param context any context
     * @return the stored language code, or "en" as default
     */
    @NonNull
    public static String getPersistedLocale(@NonNull Context context) {
        try {
            SecureStorage storage = SecureStorage.getInstance();
            String lang = storage.getString(PREF_KEY_LANGUAGE, DEFAULT_LANGUAGE);
            return (lang != null && !lang.isEmpty()) ? lang : DEFAULT_LANGUAGE;
        } catch (IllegalStateException e) {
            // SecureStorage may not be initialized yet during early attachBaseContext
            return DEFAULT_LANGUAGE;
        }
    }

    /**
     * Saves the chosen language code to EncryptedSharedPreferences.
     *
     * @param context      any context
     * @param languageCode ISO 639-1 language code
     */
    public static void persistLocale(@NonNull Context context, @NonNull String languageCode) {
        try {
            SecureStorage storage = SecureStorage.getInstance();
            storage.putString(PREF_KEY_LANGUAGE, languageCode);
        } catch (IllegalStateException e) {
            // Fallback — should not happen in normal flow
            android.util.Log.e("LocaleHelper", "SecureStorage not available for persisting locale", e);
        }
    }

    /**
     * Returns the currently configured locale display name.
     *
     * @param context any context
     * @return human-readable language name
     */
    @NonNull
    public static String getCurrentLocaleDisplayName(@NonNull Context context) {
        String code = getPersistedLocale(context);
        Locale locale = new Locale(code);
        String name = locale.getDisplayLanguage(locale);
        if (name.isEmpty()) {
            return code;
        }
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }
}
