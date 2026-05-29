package eu.kodanetwork.mchost.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import eu.kodanetwork.mchost.BuildConfig;

public class SecureConfigStore {
    private static final String PREF_NAME = "secure_ionos";
    private static final String KEY_API = "api_key";
    private static final String KEY_SECRET = "api_secret";

    private final SharedPreferences prefs;

    public SecureConfigStore(Context context) throws Exception {
        Context app = context.getApplicationContext();
        MasterKey key = new MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build();
        prefs = EncryptedSharedPreferences.create(
            app,
            PREF_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public void saveCredentials(String apiKey, String apiSecret) {
        prefs.edit().putString(KEY_API, apiKey).putString(KEY_SECRET, apiSecret).apply();
    }

    public String getApiKey() {
        String local = prefs.getString(KEY_API, "");
        if (!local.isEmpty()) return local;
        return eu.kodanetwork.mchost.security.PraetorSecurity.getIonosKey();
    }

    public String getApiSecret() {
        String local = prefs.getString(KEY_SECRET, "");
        if (!local.isEmpty()) return local;
        return eu.kodanetwork.mchost.security.PraetorSecurity.getIonosSecret();
    }

    public String getCombinedHeader() {
        String key = getApiKey();
        String secret = getApiSecret();
        if (key == null || secret == null || key.isEmpty() || secret.isEmpty()) return "";
        return key + "." + secret;
    }
}
