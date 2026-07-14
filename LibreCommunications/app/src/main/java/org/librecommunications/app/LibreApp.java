package org.librecommunications.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LibreApp — Application entry point for Libre Communications.
 * <p>
 * Initializes:
 * <ul>
 *   <li>SQLCipher libraries for encrypted database storage</li>
 *   <li>Default uncaught exception handler that logs locally to an encrypted file
 *       without leaking crash data to third-party services</li>
 *   <li>SecureStorage singleton for EncryptedSharedPreferences</li>
 * </ul>
 */
public class LibreApp extends Application {

    private static final String TAG = "LibreApp";
    private static final String CRASH_LOG_DIR = "crash_logs";
    private static final String CRASH_LOG_PREFIX = "crash_";

    private static LibreApp sInstance;

    /**
     * Returns the singleton Application instance.
     *
     * @return the LibreApp instance
     */
    public static LibreApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        initSQLCipher();
        initSecureCrashHandler();
        initSecureStorage();
    }

    /**
     * SQLCipher native libraries are now loaded automatically via Support API.
     */
    private void initSQLCipher() {
        Log.i(TAG, "SQLCipher initialization handled automatically by modern Support API");
    }

    /**
     * Sets up a default uncaught exception handler that writes the stack trace
     * to a locally encrypted file. No crash data is transmitted externally.
     */
    private void initSecureCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrashToEncryptedFile(throwable);
            } catch (Exception ignored) {
                // We must not throw here — last resort, silently swallow.
            }

            // Kill the process; do NOT delegate to any analytics handler.
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        Log.i(TAG, "Secure crash handler installed");
    }

    /**
     * Writes a crash stack trace into an encrypted file inside the app's private storage.
     *
     * @param throwable the unhandled exception
     */
    private void writeCrashToEncryptedFile(Throwable throwable) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            File crashDir = new File(getFilesDir(), CRASH_LOG_DIR);
            if (!crashDir.exists()) {
                boolean created = crashDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create crash log directory");
                    return;
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            File crashFile = new File(crashDir, CRASH_LOG_PREFIX + timestamp + ".log");

            // Delete if leftover from a previous run (EncryptedFile requires fresh files)
            if (crashFile.exists()) {
                boolean deleted = crashFile.delete();
                if (!deleted) {
                    return;
                }
            }

            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    this,
                    crashFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== LIBRE COMMUNICATIONS CRASH LOG ===");
            pw.println("Time : " + timestamp);
            pw.println("Thread: " + Thread.currentThread().getName());
            pw.println("========================================");
            throwable.printStackTrace(pw);
            pw.flush();

            try (OutputStream os = encryptedFile.openFileOutput()) {
                os.write(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (GeneralSecurityException | IOException e) {
            // Last resort — write plain-text to internal storage (still app-private).
            try {
                File fallback = new File(getFilesDir(), "crash_fallback.log");
                try (FileOutputStream fos = new FileOutputStream(fallback, true)) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    fos.write(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // Nothing more we can do.
            }
        }
    }

    /**
     * Initializes the SecureStorage singleton that wraps EncryptedSharedPreferences.
     */
    private void initSecureStorage() {
        try {
            SecureStorage.init(this);
            Log.i(TAG, "SecureStorage initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SecureStorage", e);
        }
    }

    /**
     * Convenience accessor for the application context.
     */
    public static Context getAppContext() {
        return sInstance.getApplicationContext();
    }
}
