package org.librecommunications.app.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Highly secure End-to-End Encryption (E2EE) Manager using built-in Android Cryptography.
 * 
 * Protocol:
 * 1. Each peer generates an Elliptic Curve (EC) KeyPair.
 * 2. Peers exchange Public Keys (in plain text, over the raw P2P socket during handshake).
 * 3. Both peers use ECDH (KeyAgreement) to compute a shared secret.
 * 4. The shared secret is hashed (SHA-256) to derive a 256-bit AES key.
 * 5. All messages are encrypted using AES-256-GCM (Galois/Counter Mode) which provides both confidentiality and authenticity.
 */
public class E2EEManager {
    private static final String TAG = "E2EEManager";
    
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    
    private KeyPair myKeyPair;
    private SecretKey sharedAesKey;

    public E2EEManager() {
        generateKeyPair();
    }

    /**
     * Step 1: Generate EC Key Pair
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256); // NIST P-256
            myKeyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate EC KeyPair", e);
        }
    }

    /**
     * Get my public key as a Base64 string to send to the peer.
     */
    public String getMyPublicKeyBase64() {
        return Base64.encodeToString(myKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
    }

    /**
     * Step 2 & 3: Receive peer's public key and compute shared secret.
     */
    public void establishSharedSecret(String peerPublicKeyBase64) {
        try {
            byte[] peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(peerKeyBytes);
            PublicKey peerPublicKey = keyFactory.generatePublic(keySpec);

            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(myKeyPair.getPrivate());
            keyAgreement.doPhase(peerPublicKey, true);
            
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Hash the secret to ensure it's exactly 256 bits for AES-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] aesKeyBytes = digest.digest(sharedSecret);
            
            sharedAesKey = new SecretKeySpec(aesKeyBytes, "AES");
            Log.i(TAG, "Shared AES-256 key established successfully.");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish shared secret", e);
        }
    }

    /**
     * Step 4: Encrypt outgoing message with AES-GCM
     */
    public String encryptMessage(String plaintext) {
        if (sharedAesKey == null) {
            Log.e(TAG, "Cannot encrypt, shared key not established");
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sharedAesKey, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to ciphertext (IV is needed for decryption and is not secret)
            byte[] combined = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, GCM_IV_LENGTH, cipherText.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Step 5: Decrypt incoming message with AES-GCM
     */
    public String decryptMessage(String encryptedBase64) {
        if (sharedAesKey == null) {
            Log.e(TAG, "Cannot decrypt, shared key not established");
            return null;
        }
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            
            int cipherTextLength = combined.length - GCM_IV_LENGTH;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextLength);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, sharedAesKey, parameterSpec);
            
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed (possibly tampered message or wrong key)", e);
            return null;
        }
    }
    
    public boolean isSecureSessionEstablished() {
        return sharedAesKey != null;
    }
}
