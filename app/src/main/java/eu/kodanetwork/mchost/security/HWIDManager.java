package eu.kodanetwork.mchost.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.security.MessageDigest;

public class HWIDManager {

    /**
     * Generates a reliable Hardware ID based on ANDROID_ID and device build info.
     * This is used for HWID bans if the device is caught tampering.
     */
    public static String getDeviceHWID(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String buildInfo = Build.BOARD + Build.BRAND + Build.DEVICE + Build.HARDWARE + Build.MANUFACTURER + Build.MODEL + Build.PRODUCT;
        
        String rawHwid = androidId + buildInfo;
        return sha256(rawHwid);
    }

    private static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
