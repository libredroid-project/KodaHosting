package eu.kodanetwork.mchost.security;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class TripwireObserver extends FileObserver {

    private static final String TAG = "TripwireObserver";
    private static TripwireObserver instance;
    private final Context context;

    private TripwireObserver(Context context, String path) {
        // Watch for anyone opening or reading the file
        super(path, FileObserver.OPEN | FileObserver.ACCESS);
        this.context = context;
    }

    public static void startWatching(Context context) {
        if (instance != null) return;

        try {
            File honeypot = new File(context.getFilesDir(), "koda_sys_auth_cache.json");
            if (!honeypot.exists()) {
                FileOutputStream fos = new FileOutputStream(honeypot);
                fos.write("{\"warning\":\"do_not_open_tripwire\"}".getBytes());
                fos.close();
            }

            instance = new TripwireObserver(context, honeypot.getAbsolutePath());
            instance.startWatching();
            Log.i(TAG, "Tripwire armed.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to arm tripwire", e);
        }
    }

    @Override
    public void onEvent(int event, String path) {
        if (event == FileObserver.OPEN || event == FileObserver.ACCESS) {
            Log.w(TAG, "Tripwire triggered! Unauthorized local file access detected.");
            // Trigger permanent ban
            AntiTamperSystem.executePermanentBan(context, "FILE_TRIPWIRE_TRIGGERED");
        }
    }
}
