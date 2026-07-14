package org.librecommunications.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * SecureStorage — Thread-safe singleton wrapper around EncryptedSharedPreferences.
 * <p>
 * All persistent preferences in Libre Communications are stored through this class
 * to guarantee AES-256 encryption at rest. Keys are encrypted with AES-256-SIV
 * and values with AES-256-GCM.
 */
public final class SecureStorage {

    private static final String TAG = "SecureStorage";
    private static final String PREFS_FILE_NAME = "libre_secure_prefs";

    private static volatile SecureStorage sInstance;
    private final SharedPreferences encryptedPrefs;

    private SecureStorage(@NonNull Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /**
     * Initializes the singleton. Must be called once from {@link LibreApp#onCreate()}.
     *
     * @param context Application context
     */
    public static void init(@NonNull Context context) throws GeneralSecurityException, IOException {
        if (sInstance == null) {
            synchronized (SecureStorage.class) {
                if (sInstance == null) {
                    sInstance = new SecureStorage(context.getApplicationContext());
                }
            }
        }
    }

    /**
     * Returns the singleton instance. Throws if {@link #init(Context)} has not been called.
     */
    @NonNull
    public static SecureStorage getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("SecureStorage not initialized. Call init() first.");
        }
        return sInstance;
    }

    /**
     * Returns the underlying EncryptedSharedPreferences for direct operations.
     */
    @NonNull
    public SharedPreferences getPrefs() {
        return encryptedPrefs;
    }

    public void putString(@NonNull String key, String value) {
        encryptedPrefs.edit().putString(key, value).apply();
    }

    public String getString(@NonNull String key, String defaultValue) {
        return encryptedPrefs.getString(key, defaultValue);
    }

    public void putBoolean(@NonNull String key, boolean value) {
        encryptedPrefs.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        return encryptedPrefs.getBoolean(key, defaultValue);
    }

    public void putInt(@NonNull String key, int value) {
        encryptedPrefs.edit().putInt(key, value).apply();
    }

    public int getInt(@NonNull String key, int defaultValue) {
        return encryptedPrefs.getInt(key, defaultValue);
    }

    public void putLong(@NonNull String key, long value) {
        encryptedPrefs.edit().putLong(key, value).apply();
    }

    public long getLong(@NonNull String key, long defaultValue) {
        return encryptedPrefs.getLong(key, defaultValue);
    }

    public void remove(@NonNull String key) {
        encryptedPrefs.edit().remove(key).apply();
    }

    public boolean contains(@NonNull String key) {
        return encryptedPrefs.contains(key);
    }

    /**
     * Wipes all encrypted preferences. Used by panic-wipe functionality.
     */
    public void clearAll() {
        encryptedPrefs.edit().clear().apply();
        Log.w(TAG, "All secure preferences cleared");
    }
}
