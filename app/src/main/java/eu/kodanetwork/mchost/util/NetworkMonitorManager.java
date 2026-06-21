package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import eu.kodanetwork.mchost.R;

public class NetworkMonitorManager {
    private static NetworkMonitorManager instance;
    private final Context context;
    private boolean isNetworkLost = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable alarmRunnable;
    private Runnable repeatingAlarmRunnable;
    private long alarmStartTime;
    private android.media.MediaPlayer mediaPlayer;
    private android.media.MediaPlayer alarmPlayer;

    private NetworkMonitorManager(Context context) {
        this.context = context.getApplicationContext();
        registerNetworkCallback();
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new NetworkMonitorManager(context);
        }
    }

    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                isNetworkLost = false;
                if (alarmRunnable != null) handler.removeCallbacks(alarmRunnable);
                if (repeatingAlarmRunnable != null) handler.removeCallbacks(repeatingAlarmRunnable);
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                    } catch (Exception ignored) {}
                }
                if (alarmPlayer != null) {
                    try {
                        if (alarmPlayer.isPlaying()) alarmPlayer.stop();
                        alarmPlayer.release();
                        alarmPlayer = null;
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onLost(Network network) {
                isNetworkLost = true;
                if (alarmRunnable != null) handler.removeCallbacks(alarmRunnable);
                if (repeatingAlarmRunnable != null) handler.removeCallbacks(repeatingAlarmRunnable);
                
                alarmStartTime = System.currentTimeMillis();
                
                alarmRunnable = () -> {
                    if (!isNetworkLost) return;
                    SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(context);
                    if (!prefs.getBoolean("network_alarm_enabled", false)) return;
                    
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;

                    // 1. Start continuous alarm
                    try {
                        if (alarmPlayer != null) alarmPlayer.release();
                        alarmPlayer = new android.media.MediaPlayer();
                        alarmPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                        alarmPlayer.setDataSource(context, android.net.Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.custom_alarm));
                        alarmPlayer.prepare();
                        alarmPlayer.setLooping(true);
                        alarmPlayer.setVolume(1.0f, 1.0f);
                        alarmPlayer.start();
                    } catch (Exception e) {
                        Log.e("NetworkMonitorManager", "Failed to setup alarmPlayer", e);
                    }

                    // 2. repeating voice alarm every 30s
                    repeatingAlarmRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!isNetworkLost) return;
                            if (System.currentTimeMillis() - alarmStartTime > 5 * 60 * 1000) return; // 5 min timeout
                            
                            String lang = prefs.getString("language", "en");
                            String message = lang.equals("de") 
                                ? "Internetverbindung verloren. Bitte sofort neu verbinden." 
                                : "Internet connection lost. Please try to reconnect immediately.";
                            
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
                            
                            // Duck alarm volume
                            if (alarmPlayer != null && alarmPlayer.isPlaying()) {
                                alarmPlayer.setVolume(0.1f, 0.1f);
                            }
                            
                            // Play voice
                            try {
                                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0);
                                
                                if (mediaPlayer != null) mediaPlayer.release();
                                int audioRes = lang.equals("de") ? R.raw.alarm_voice_de : R.raw.alarm_voice_en;
                                mediaPlayer = android.media.MediaPlayer.create(context, audioRes);
                                if (mediaPlayer != null) {
                                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                                    mediaPlayer.start();
                                }
                            } catch (Exception e) {
                                Log.e("NetworkMonitorManager", "Failed to play voice", e);
                            }
                            
                            // Fade in alarm after voice finishes (approx 5-6s)
                            handler.postDelayed(() -> {
                                if (!isNetworkLost) return;
                                final int steps = 20;
                                final int delayMs = 150; // Total 3s fade in
                                for (int i = 1; i <= steps; i++) {
                                    final float vol = 0.1f + (0.9f * ((float)i / steps));
                                    handler.postDelayed(() -> {
                                        if (alarmPlayer != null && alarmPlayer.isPlaying()) {
                                            alarmPlayer.setVolume(vol, vol);
                                        }
                                    }, i * delayMs);
                                }
                            }, 6000);
                            
                            handler.postDelayed(this, 30000); // 30 seconds interval
                        }
                    };
                    handler.post(repeatingAlarmRunnable);
                };
                handler.postDelayed(alarmRunnable, 5000);
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= 24) {
            cm.registerDefaultNetworkCallback(cb);
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, cb);
        }
    }
}
