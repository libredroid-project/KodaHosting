package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

public class LocaleHelper {
    public static Context onAttach(Context context) {
        SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(context);
        String defaultLang = Locale.getDefault().getLanguage();
        String lang = prefs.getString("language", defaultLang);
        return setLocale(context, lang);
    }

    private static Context setLocale(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.setLocale(locale);

        return context.createConfigurationContext(configuration);
    }
    
    // Fallback dictionary for programmatic use since XMLs are hardcoded
    public static String t(Context c, String en, String de) {
        SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(c);
        String defaultLang = Locale.getDefault().getLanguage();
        String lang = prefs.getString("language", defaultLang);
        if ("de".equals(lang)) return de;
        return en;
    }
}
