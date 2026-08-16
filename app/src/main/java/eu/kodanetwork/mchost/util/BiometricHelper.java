package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.biometric.BiometricPrompt;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import eu.kodanetwork.mchost.ui.BiometricAuthActivity;

public class BiometricHelper {

    private static final String KEY_NAME = "koda_biometric_key";

    public static void generateKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_NAME)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(-1)
                        .build());
                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BiometricPrompt.CryptoObject getCryptoObject() {
        try {
            generateKey();
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);
            Cipher cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return new BiometricPrompt.CryptoObject(cipher);
        } catch (Exception e) {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                keyStore.deleteEntry(KEY_NAME);
                generateKey();
                SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);
                Cipher cipher = Cipher.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES + "/"
                                + KeyProperties.BLOCK_MODE_CBC + "/"
                                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                return new BiometricPrompt.CryptoObject(cipher);
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    public static final int REQ_BIO_AUTH = 9005;
    public static final String EXTRA_KILL_ON_CANCEL = "kill_on_cancel";

    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences sp = eu.kodanetwork.mchost.App.getPrefs(context);
        return sp.getBoolean("bio_enabled", false);
    }

    public static boolean isBioEnabledFor(Context context, String key) {
        if (!isBiometricEnabled(context)) return false;
        SharedPreferences sp = eu.kodanetwork.mchost.App.getPrefs(context);
        return sp.getBoolean(key, false);
    }

    public static void startAuth(android.app.Activity activity, boolean killOnCancel) {
        Intent intent = new Intent(activity, BiometricAuthActivity.class);
        intent.putExtra(EXTRA_KILL_ON_CANCEL, killOnCancel);
        activity.startActivityForResult(intent, REQ_BIO_AUTH);
    }

    public static void startAuthStandalone(Context context, boolean killOnCancel) {
        Intent intent = new Intent(context, BiometricAuthActivity.class);
        intent.putExtra(EXTRA_KILL_ON_CANCEL, killOnCancel);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
