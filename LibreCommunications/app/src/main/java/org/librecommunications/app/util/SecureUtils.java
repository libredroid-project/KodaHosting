package org.librecommunications.app.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.content.ContextCompat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Collection of static security and general-purpose utility methods
 * used throughout the Libre Communications app.
 */
public final class SecureUtils {

    private SecureUtils() {
        // Prevent instantiation
    }

    /**
     * Computes the SHA-256 hash of a PIN string and returns it as a lowercase hex string.
     *
     * @param pin The plaintext PIN to hash.
     * @return Hex-encoded SHA-256 digest, or empty string on failure.
     */
    public static String hashPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            android.util.Log.e("SecureUtils", "SHA-256 not available", e);
            return "";
        }
    }

    /**
     * Generates a random UUID string for use as entity identifiers.
     *
     * @return A new random UUID string.
     */
    public static String generateRandomId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes The byte array to convert.
     * @return Hex string representation.
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @param hex The hex string to convert (must have even length).
     * @return Byte array, or empty array on invalid input.
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return new byte[0];
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i + 1), 16));
            }
        } catch (Exception e) {
            android.util.Log.e("SecureUtils", "Invalid hex string", e);
            return new byte[0];
        }
        return data;
    }

    /**
     * Formats a hex fingerprint string into colon-separated pairs (e.g. "AB:CD:EF:01").
     *
     * @param hex Raw hex string.
     * @return Formatted fingerprint string.
     */
    public static String formatFingerprint(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        String upper = hex.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upper.length(); i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            int end = Math.min(i + 2, upper.length());
            sb.append(upper, i, end);
        }
        return sb.toString();
    }

    /**
     * Formats an epoch-millisecond timestamp into a localized date/time string.
     *
     * @param timestamp Epoch milliseconds.
     * @return Formatted date/time string (e.g. "2026-07-13 16:30").
     */
    public static String getTimestampFormatted(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    /**
     * Checks whether a single runtime permission has been granted.
     *
     * @param context    Application or activity context.
     * @param permission The permission string (e.g. Manifest.permission.CAMERA).
     * @return True if the permission is granted.
     */
    public static boolean isPermissionGranted(Context context, String permission) {
        if (context == null || permission == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks whether all of the specified runtime permissions have been granted.
     *
     * @param context     Application or activity context.
     * @param permissions Array of permission strings.
     * @return True if every permission in the array is granted.
     */
    public static boolean areAllPermissionsGranted(Context context, String[] permissions) {
        if (context == null || permissions == null) {
            return false;
        }
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vibrates the device for the specified duration.
     * Uses the appropriate API depending on the Android version.
     *
     * @param context    Application context.
     * @param durationMs Vibration duration in milliseconds.
     */
    public static void vibrateDevice(Context context, long durationMs) {
        if (context == null || durationMs <= 0) {
            return;
        }
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager =
                        (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                } else {
                    return;
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SecureUtils", "Vibration failed", e);
        }
    }

    /**
     * Returns the device model name.
     *
     * @return The value of {@link Build#MODEL}.
     */
    public static String getDeviceName() {
        return Build.MODEL != null ? Build.MODEL : "Unknown";
    }
}
