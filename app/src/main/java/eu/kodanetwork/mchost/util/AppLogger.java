package eu.kodanetwork.mchost.util;

import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    public interface Listener {
        void onLogAdded(String line);
    }

    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void log(String tag, String message) {
        String time = sdf.format(new Date());
        String line = "[" + time + "] [" + tag + "] " + message;
        
        android.util.Log.d(tag, message);
        
        synchronized (logs) {
            logs.add(line);
            if (logs.size() > 5000) {
                logs.remove(0);
            }
        }
        
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onLogAdded(line);
            }
        });
    }

    public static void log(String message) {
        log("KodaDebug", message);
    }

    public static String getAllLogs() {
        synchronized (logs) {
            StringBuilder sb = new StringBuilder();
            for (String l : logs) {
                sb.append(l).append("\n");
            }
            return sb.toString();
        }
    }

    public static void addListener(Listener listener) {
        mainHandler.post(() -> listeners.add(listener));
    }

    public static void removeListener(Listener listener) {
        mainHandler.post(() -> listeners.remove(listener));
    }
}
