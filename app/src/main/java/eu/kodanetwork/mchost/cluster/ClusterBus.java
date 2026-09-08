package eu.kodanetwork.mchost.cluster;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cluster-wide state bus. UI subscribes via {@link #addListener}; the link
 * implementations (master/slave) push state changes and received frames here.
 * Singleton — survives Activity changes, tied to the app process.
 */
public class ClusterBus {

    public interface Listener {
        /** A slave device state changed (added, heartbeat, link up/down). Main thread. */
        void onDevicesChanged();
        /** A frame arrived that is not handled internally (e.g. ack for UI). Main thread. */
        void onFrame(JSONObject frame);
    }

    public static class SlaveState {
        public String deviceId;
        public String model = "?";
        public String android = "?";
        public int totalRamMb, cores;
        public int freeRamMb, battery = -1;
        public boolean charging;
        public long lastHeartbeatMs, lastSeenMs, rttMs = -1;
        public boolean linkUp;
        public final List<JSONObject> servers = new ArrayList<>();
    }

    private static final ClusterBus INSTANCE = new ClusterBus();

    public static ClusterBus get() { return INSTANCE; }

    private final Map<String, SlaveState> slaves = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean masterRole, slaveRole;

    private ClusterBus() {}

    // ── roles ────────────────────────────────────────────────────────

    public boolean isMaster() { return masterRole; }
    public boolean isSlave() { return slaveRole; }

    public void setMasterRole(boolean on) { this.masterRole = on; }
    public void setSlaveRole(boolean on) { this.slaveRole = on; }

    // ── slave state management (master side) ─────────────────────────

    public synchronized SlaveState slave(String deviceId) {
        SlaveState s = slaves.get(deviceId);
        if (s == null) {
            s = new SlaveState();
            s.deviceId = deviceId;
            slaves.put(deviceId, s);
        }
        return s;
    }

    public synchronized void removeSlave(String deviceId) {
        slaves.remove(deviceId);
        notifyDevicesChanged();
    }

    public synchronized List<SlaveState> slaves() {
        return new ArrayList<>(slaves.values());
    }

    public synchronized void linkUp(String deviceId, boolean up) {
        SlaveState s = slave(deviceId);
        if (s.linkUp != up) {
            s.linkUp = up;
            s.lastSeenMs = System.currentTimeMillis();
            notifyDevicesChanged();
        }
    }

    // ── listeners ────────────────────────────────────────────────────

    public void addListener(Listener l) { synchronized (listeners) { listeners.add(l); } }
    public void removeListener(Listener l) { synchronized (listeners) { listeners.remove(l); } }

    public void notifyDevicesChanged() {
        List<Listener> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        main.post(() -> { for (Listener l : copy) l.onDevicesChanged(); });
    }

    public void notifyFrame(JSONObject frame) {
        if (frame == null) return;
        List<Listener> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        main.post(() -> { for (Listener l : copy) l.onFrame(frame); });
    }
}
