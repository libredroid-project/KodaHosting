package org.librecommunications.app.ui.nearby;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.librecommunications.app.R;
import org.librecommunications.app.data.LibreDatabase;
import org.librecommunications.app.data.entity.Contact;
import org.librecommunications.app.network.p2p.WifiDirectManager;

import android.net.wifi.p2p.WifiP2pInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NearbyFragment extends Fragment implements WifiDirectManager.P2pListener {

    private RecyclerView rvNearbyDevices;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private TextView tvStatus;
    private Button btnScan;
    private NearbyDeviceAdapter adapter;
    private List<WifiDirectManager.DiscoveredService> services = new ArrayList<>();
    private WifiDirectManager p2pManager;
    private ExecutorService dbExecutor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nearby, container, false);
        
        dbExecutor = Executors.newSingleThreadExecutor();

        rvNearbyDevices = view.findViewById(R.id.rvNearbyDevices);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        btnScan = view.findViewById(R.id.btnScan);
        tvStatus = view.findViewById(R.id.tvScanStatus);

        setupRecyclerView();

        swipeRefreshLayout.setOnRefreshListener(this::startScan);
        swipeRefreshLayout.setColorSchemeColors(0xFFFF6B00);
        btnScan.setOnClickListener(v -> startScan());
        
        p2pManager = WifiDirectManager.getInstance(requireContext());
        p2pManager.setListener(this);

        return view;
    }

    private void setupRecyclerView() {
        adapter = new NearbyDeviceAdapter(services, service -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Secure Connection")
                .setMessage("Request a secure P2P connection to " + service.displayName + "?")
                .setPositiveButton("Connect", (dialog, which) -> {
                    Toast.makeText(requireContext(), "Connecting to " + service.displayName, Toast.LENGTH_SHORT).show();
                    
                    // Insert into Contacts DB
                    dbExecutor.execute(() -> {
                        Contact contact = new Contact(
                            service.device.deviceAddress,
                            service.displayName,
                            null, // PK negotiated later
                            System.currentTimeMillis(),
                            false, // isTrusted
                            false  // isOnline (must wait for handshake_ack)
                        );
                        LibreDatabase.getDatabase(requireContext()).contactDao().insert(contact);
                    });
                    
                    p2pManager.connect(service.device);
                    
                    // Navigate to Chats
                    Navigation.findNavController(requireView()).navigate(R.id.nav_chats);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        rvNearbyDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNearbyDevices.setAdapter(adapter);
    }

    private void startScan() {
        // ── Pre-flight checks ──────────────────────────────────
        if (!checkPrerequisites()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        emptyStateLayout.setVisibility(View.GONE);
        setStatus("Scanning for nearby Libre nodes...");
        
        p2pManager.discoverServices();
        
        rvNearbyDevices.postDelayed(() -> {
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
                if (services.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    setStatus("No devices found. Ensure both devices have WiFi + GPS on.");
                }
            }
        }, 15000); // Give 15 seconds for discovery
    }

    /**
     * Check all prerequisites for Wi-Fi Direct discovery.
     * Returns true if everything is good to go.
     */
    private boolean checkPrerequisites() {
        // 1. Check WiFi is enabled
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            setStatus("⚠ WiFi is OFF. Please enable WiFi to discover nearby devices.");
            Toast.makeText(requireContext(), "WiFi must be enabled for nearby discovery!", Toast.LENGTH_LONG).show();
            emptyStateLayout.setVisibility(View.VISIBLE);
            return false;
        }

        // 2. Check Location Services are enabled (CRITICAL for Wi-Fi Direct!)
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        if (locationManager != null) {
            try { gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            try { networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        }
        if (!gpsEnabled && !networkEnabled) {
            setStatus("⚠ Location/GPS is OFF. Android requires Location to be ON for device discovery!");
            Toast.makeText(requireContext(), "Please turn ON Location/GPS in Android settings!", Toast.LENGTH_LONG).show();

            // Offer to open location settings
            try {
                startActivity(new android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) {}
            
            emptyStateLayout.setVisibility(View.VISIBLE);
            return false;
        }

        // 3. Check permissions
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            setStatus("⚠ Location permission not granted.");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                setStatus("⚠ Nearby Devices permission not granted.");
                requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, 101);
                return false;
            }
        }

        return true;
    }

    private void setStatus(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        p2pManager.onResume();
        // Auto-start scan when tab becomes visible
        startScan();
    }

    @Override
    public void onPause() {
        super.onPause();
        p2pManager.onPause();
    }

    @Override
    public void onServicesAvailable(List<WifiDirectManager.DiscoveredService> discoveredServices) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            swipeRefreshLayout.setRefreshing(false);
            services.clear();
            services.addAll(discoveredServices);
            adapter.notifyDataSetChanged();
            emptyStateLayout.setVisibility(services.isEmpty() ? View.VISIBLE : View.GONE);
            if (!services.isEmpty()) {
                setStatus("Found " + services.size() + " nearby node(s)");
            }
        });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // Handled globally by NetworkService mostly
    }

    @Override
    public void onDisconnected() {
    }
    
    @Override
    public void onDestroy() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        super.onDestroy();
    }
    
    public static class NearbyDeviceAdapter extends RecyclerView.Adapter<NearbyDeviceAdapter.ViewHolder> {
        private List<WifiDirectManager.DiscoveredService> services;
        private OnDeviceClickListener listener;
        
        public interface OnDeviceClickListener {
            void onDeviceClick(WifiDirectManager.DiscoveredService service);
        }
        
        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvDeviceName, tvDeviceType;
            Button btnConnect;
            
            public ViewHolder(View v) {
                super(v);
                tvAvatar = v.findViewById(R.id.tvAvatar);
                tvDeviceName = v.findViewById(R.id.tvDeviceName);
                tvDeviceType = v.findViewById(R.id.tvDeviceType);
                btnConnect = v.findViewById(R.id.btnConnect);
            }
        }
        
        public NearbyDeviceAdapter(List<WifiDirectManager.DiscoveredService> services, OnDeviceClickListener listener) {
            this.services = services;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WifiDirectManager.DiscoveredService service = services.get(position);
            
            String name = service.displayName != null ? service.displayName : "Unknown";
            holder.tvDeviceName.setText(name);
            holder.tvAvatar.setText(name.substring(0, 1).toUpperCase());
            holder.tvDeviceType.setText("Libre Node • " + service.device.deviceAddress);
            
            holder.btnConnect.setOnClickListener(v -> listener.onDeviceClick(service));
        }

        @Override
        public int getItemCount() {
            return services.size();
        }
    }
}
