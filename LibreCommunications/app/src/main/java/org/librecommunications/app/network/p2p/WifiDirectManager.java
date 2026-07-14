package org.librecommunications.app.network.p2p;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiDirectManager {

    private static final String TAG = "WifiDirectManager";
    private static final String SERVICE_INSTANCE = "_librecomm";
    private static final String SERVICE_REG_TYPE = "_presence._tcp";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;

    private static WifiDirectManager instance;

    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final IntentFilter intentFilter;
    private final Handler mainHandler;
    
    private P2pListener listener;
    private BroadcastReceiver receiver;
    private boolean isRegistered = false;
    private boolean isLocalServiceRegistered = false;
    private boolean isDiscovering = false;
    
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private final Map<String, DiscoveredService> discoveredServices = new HashMap<>();

    public static class DiscoveredService {
        public WifiP2pDevice device;
        public String displayName;
        public String serviceName;
        
        public DiscoveredService(WifiP2pDevice device, String displayName, String serviceName) {
            this.device = device;
            this.displayName = displayName;
            this.serviceName = serviceName;
        }
    }

    public interface P2pListener {
        void onServicesAvailable(List<DiscoveredService> services);
        void onConnectionInfoAvailable(WifiP2pInfo info);
        void onDisconnected();
    }

    private WifiDirectManager(Context context) {
        this.context = context.getApplicationContext();
        manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this.context, Looper.getMainLooper(), null);
        mainHandler = new Handler(Looper.getMainLooper());
        
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    public static synchronized WifiDirectManager getInstance(Context context) {
        if (instance == null) {
            instance = new WifiDirectManager(context);
        }
        return instance;
    }

    public void setListener(P2pListener listener) {
        this.listener = listener;
    }

    private String getFailureReason(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED: return "P2P_UNSUPPORTED";
            case WifiP2pManager.ERROR: return "INTERNAL_ERROR";
            case WifiP2pManager.BUSY: return "BUSY";
            default: return "UNKNOWN(" + reason + ")";
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LOCAL SERVICE REGISTRATION (with retry on BUSY)
    // ════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    public void startLocalService() {
        if (!hasPermissions()) {
            Log.w(TAG, "startLocalService: Missing permissions!");
            return;
        }
        startLocalServiceWithRetry(0);
    }

    @SuppressLint("MissingPermission")
    private void startLocalServiceWithRetry(int attempt) {
        if (attempt >= MAX_RETRIES) {
            Log.e(TAG, "✗ Gave up registering local service after " + MAX_RETRIES + " retries");
            showToast("Could not register service. Please restart the app.");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String displayName = prefs.getString("display_name", "Anonymous Node");

        Map<String, String> record = new HashMap<>();
        record.put("name", displayName);
        record.put("app", "libre_comm");
        record.put("version", "1.0");

        WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);

        Log.d(TAG, "Registering local service (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")...");

        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "  ✓ clearLocalServices OK");
                manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        isLocalServiceRegistered = true;
                        Log.d(TAG, "  ✓ Local DNS-SD service registered as: " + displayName);
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "  ✗ addLocalService FAILED: " + getFailureReason(reason));
                        if (reason == WifiP2pManager.BUSY) {
                            long delay = RETRY_DELAY_MS * (attempt + 1);
                            Log.d(TAG, "  ↻ Retrying in " + delay + "ms...");
                            mainHandler.postDelayed(() -> startLocalServiceWithRetry(attempt + 1), delay);
                        }
                    }
                });
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "  ✗ clearLocalServices FAILED: " + getFailureReason(reason));
                if (reason == WifiP2pManager.BUSY) {
                    long delay = RETRY_DELAY_MS * (attempt + 1);
                    Log.d(TAG, "  ↻ Retrying clearLocalServices in " + delay + "ms...");
                    mainHandler.postDelayed(() -> startLocalServiceWithRetry(attempt + 1), delay);
                }
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // SERVICE DISCOVERY (with retry on BUSY)
    // ════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    public void discoverServices() {
        if (!hasPermissions()) {
            Log.w(TAG, "discoverServices: Missing permissions!");
            showToast("Missing permissions for nearby discovery");
            return;
        }

        Log.d(TAG, "═══ Starting discovery sequence ═══");
        discoveredServices.clear();
        isDiscovering = true;
        
        // Ensure local service is registered so we can be found
        if (!isLocalServiceRegistered) {
            Log.d(TAG, "Local service not yet registered, registering first...");
            startLocalService();
        }

        // Set up response listeners
        manager.setDnsSdResponseListeners(channel,
            (instanceName, registrationType, srcDevice) -> {
                Log.d(TAG, "→ DNS-SD Service found: " + instanceName + " type=" + registrationType 
                    + " device=" + (srcDevice != null ? srcDevice.deviceName : "null"));
            },
            (fullDomainName, record, device) -> {
                Log.d(TAG, "→ TXT record: domain=" + fullDomainName 
                    + " device=" + (device != null ? device.deviceName + "(" + device.deviceAddress + ")" : "null")
                    + " record=" + (record != null ? record.toString() : "null"));
                    
                if (fullDomainName != null && fullDomainName.contains(SERVICE_INSTANCE)) {
                    String name = "Unknown";
                    if (record != null && record.containsKey("name")) {
                        name = record.get("name");
                    }
                    if (name == null) name = "Unknown";
                    
                    DiscoveredService ds = new DiscoveredService(device, name, fullDomainName);
                    discoveredServices.put(device.deviceAddress, ds);
                    
                    Log.d(TAG, "★ Libre node discovered: " + name + " @ " + device.deviceAddress);
                    
                    if (listener != null) {
                        listener.onServicesAvailable(new ArrayList<>(discoveredServices.values()));
                    }
                }
            });

        // Start peer discovery first to wake the radio, then discover services after delay
        startPeerDiscoveryWithRetry(0);
    }

    @SuppressLint("MissingPermission")
    private void startPeerDiscoveryWithRetry(int attempt) {
        if (attempt >= MAX_RETRIES) {
            Log.e(TAG, "✗ Gave up peer discovery after " + MAX_RETRIES + " retries");
            showToast("Discovery failed. Try toggling WiFi off and on.");
            return;
        }

        Log.d(TAG, "  Starting discoverPeers (attempt " + (attempt + 1) + ")...");

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "  ✓ discoverPeers started (radio waking up)");
                // Wait for radio to warm up, then add service request and discover
                mainHandler.postDelayed(() -> addServiceRequestWithRetry(0), 1500);
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "  ✗ discoverPeers FAILED: " + getFailureReason(reason));
                if (reason == WifiP2pManager.BUSY) {
                    long delay = RETRY_DELAY_MS * (attempt + 1);
                    Log.d(TAG, "  ↻ Retrying discoverPeers in " + delay + "ms...");
                    mainHandler.postDelayed(() -> startPeerDiscoveryWithRetry(attempt + 1), delay);
                } else {
                    showToast("Peer discovery failed: " + getFailureReason(reason));
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void addServiceRequestWithRetry(int attempt) {
        if (attempt >= MAX_RETRIES) {
            Log.e(TAG, "✗ Gave up adding service request after " + MAX_RETRIES + " retries");
            return;
        }

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        Log.d(TAG, "  Clearing+adding service request (attempt " + (attempt + 1) + ")...");

        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "  ✓ clearServiceRequests OK");
                manager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "  ✓ addServiceRequest OK");
                        startServiceDiscoveryWithRetry(0);
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "  ✗ addServiceRequest FAILED: " + getFailureReason(reason));
                        if (reason == WifiP2pManager.BUSY) {
                            long delay = RETRY_DELAY_MS * (attempt + 1);
                            mainHandler.postDelayed(() -> addServiceRequestWithRetry(attempt + 1), delay);
                        }
                    }
                });
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "  ✗ clearServiceRequests FAILED: " + getFailureReason(reason));
                if (reason == WifiP2pManager.BUSY) {
                    long delay = RETRY_DELAY_MS * (attempt + 1);
                    Log.d(TAG, "  ↻ Retrying clearServiceRequests in " + delay + "ms...");
                    mainHandler.postDelayed(() -> addServiceRequestWithRetry(attempt + 1), delay);
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startServiceDiscoveryWithRetry(int attempt) {
        if (!isDiscovering) return;
        if (attempt >= MAX_RETRIES) {
            Log.e(TAG, "✗ Gave up service discovery after " + MAX_RETRIES + " retries");
            showToast("Service discovery failed after retries. Toggle WiFi.");
            return;
        }

        Log.d(TAG, "  Starting discoverServices (attempt " + (attempt + 1) + ")...");

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "  ✓ discoverServices RUNNING!");
                showToast("Scanning for Libre nodes...");

                // Schedule periodic re-discovery for reliability
                if (isDiscovering) {
                    mainHandler.postDelayed(() -> periodicRediscovery(), 6000);
                }
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "  ✗ discoverServices FAILED: " + getFailureReason(reason));
                if (reason == WifiP2pManager.BUSY) {
                    long delay = RETRY_DELAY_MS * (attempt + 1);
                    Log.d(TAG, "  ↻ Retrying discoverServices in " + delay + "ms...");
                    mainHandler.postDelayed(() -> startServiceDiscoveryWithRetry(attempt + 1), delay);
                } else {
                    showToast("Discovery error: " + getFailureReason(reason));
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void periodicRediscovery() {
        if (!isDiscovering || !hasPermissions()) return;
        
        Log.d(TAG, "  ↻ Periodic re-discovery...");
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "  ✓ Re-discovery OK");
                if (isDiscovering) {
                    mainHandler.postDelayed(() -> periodicRediscovery(), 6000);
                }
            }
            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "  ↻ Re-discovery busy, retrying full chain...");
                if (isDiscovering) {
                    // Full restart of discovery chain after a delay
                    mainHandler.postDelayed(() -> startPeerDiscoveryWithRetry(0), 3000);
                }
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // CONNECTION
    // ════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    public void connect(WifiP2pDevice device) {
        if (!hasPermissions()) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✓ Connection initiated to " + device.deviceName);
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "✗ Connect failed: " + getFailureReason(reason));
                showToast("Connection failed: " + getFailureReason(reason));
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private boolean hasPermissions() {
        boolean hasLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNearby = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNearby = ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }
        if (!hasLocation) Log.w(TAG, "Missing: ACCESS_FINE_LOCATION");
        if (!hasNearby) Log.w(TAG, "Missing: NEARBY_WIFI_DEVICES");
        return hasLocation && hasNearby;
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    public void onResume() {
        if (!isRegistered) {
            receiver = new WiFiDirectBroadcastReceiver();
            context.registerReceiver(receiver, intentFilter);
            isRegistered = true;
        }
    }

    public void onPause() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
            isRegistered = false;
        }
        isDiscovering = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    private class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                Log.d(TAG, "P2P state: " + (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED ? "ENABLED" : "DISABLED"));
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    showToast("Wi-Fi Direct is disabled!");
                }
            }
            else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                Log.d(TAG, "Peers changed event");
            }
            else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    android.net.NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null && networkInfo.isConnected()) {
                        Log.d(TAG, "P2P Connected!");
                        manager.requestConnectionInfo(channel, info -> {
                            if (listener != null) listener.onConnectionInfoAvailable(info);
                        });
                    } else {
                        Log.d(TAG, "P2P Disconnected");
                        if (listener != null) listener.onDisconnected();
                    }
                }
            }
        }
    }
}
