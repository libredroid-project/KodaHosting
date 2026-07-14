package org.librecommunications.app.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SignalProtocolManager {

    private static final String TAG = "SignalProtocolManager";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private static SignalProtocolManager instance;
    private boolean isSignalAvailable = false;

    private SignalProtocolManager() {
        checkSignalAvailability();
    }

    public static synchronized SignalProtocolManager getInstance() {
        if (instance == null) {
            instance = new SignalProtocolManager();
        }
        return instance;
    }

    private void checkSignalAvailability() {
        try {
            // Use reflection to avoid hard compile-time dependency and allow compilation if library differs
            Class<?> signalClass = Class.forName("org.whispersystems.libsignal.SessionCipher");
            isSignalAvailable = (signalClass != null);
            Log.d(TAG, "Signal Protocol library is available: " + isSignalAvailable);
        } catch (ClassNotFoundException | Error e) {
            isSignalAvailable = false;
            Log.w(TAG, "Signal Protocol library not found or incompatible. Falling back to AES.", e);
        }
    }

    public String encryptMessage(String plainText, byte[] symmetricKey) {
        if (isSignalAvailable) {
            try {
                return attemptSignalEncryption(plainText);
            } catch (Exception | Error e) {
                Log.e(TAG, "Signal encryption failed, falling back to AES", e);
            }
        }
        return encryptAESFallback(plainText, symmetricKey);
    }

    public String decryptMessage(String cipherText, byte[] symmetricKey) {
        if (isSignalAvailable) {
            try {
                return attemptSignalDecryption(cipherText);
            } catch (Exception | Error e) {
                Log.e(TAG, "Signal decryption failed, falling back to AES", e);
            }
        }
        return decryptAESFallback(cipherText, symmetricKey);
    }

    private String attemptSignalEncryption(String plainText) throws Exception {
        // Reflection-based instantiation to prevent NoClassDefFoundError during compilation
        Class<?> sessionCipherClass = Class.forName("org.whispersystems.libsignal.SessionCipher");
        // Throw exception temporarily since full Signal protocol requires complex setup (IdentityKeys, PreKeys, etc.)
        // This guarantees fallback is executed for now.
        throw new UnsupportedOperationException("Signal reflection setup is incomplete, triggering fallback.");
    }

    private String attemptSignalDecryption(String cipherText) throws Exception {
        Class<?> sessionCipherClass = Class.forName("org.whispersystems.libsignal.SessionCipher");
        throw new UnsupportedOperationException("Signal reflection setup is incomplete, triggering fallback.");
    }

    private String encryptAESFallback(String plainText, byte[] key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encryptedText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] message = new byte[iv.length + encryptedText.length];
            System.arraycopy(iv, 0, message, 0, iv.length);
            System.arraycopy(encryptedText, 0, message, iv.length, encryptedText.length);

            return Base64.encodeToString(message, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "AES Encryption fallback failed", e);
            return null;
        }
    }

    private String decryptAESFallback(String cipherText, byte[] key) {
        try {
            byte[] message = Base64.decode(cipherText, Base64.DEFAULT);
            if (message.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid message length");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(message, 0, iv, 0, GCM_IV_LENGTH);

            byte[] encryptedText = new byte[message.length - GCM_IV_LENGTH];
            System.arraycopy(message, GCM_IV_LENGTH, encryptedText, 0, encryptedText.length);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decryptedText = cipher.doFinal(encryptedText);

            return new String(decryptedText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "AES Decryption fallback failed", e);
            return null;
        }
    }
}
