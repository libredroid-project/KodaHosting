package org.librecommunications.app.data.model;

import java.util.Objects;

/**
 * Plain data object representing a nearby device discovered via BLE or Wi-Fi Direct.
 * Equality and hash code are based solely on the device address so that
 * collections can correctly de-duplicate device entries.
 */
public class NearbyDevice {

    private String address;
    private String name;
    private int rssi;
    private long lastSeen;
    private boolean isConnected;

    /**
     * Constructs a new NearbyDevice.
     *
     * @param address     Hardware (MAC) address of the device.
     * @param name        Human-readable device name, may be null.
     * @param rssi        Received Signal Strength Indicator in dBm.
     * @param lastSeen    Timestamp (epoch ms) when the device was last detected.
     * @param isConnected Whether an active data connection exists to this device.
     */
    public NearbyDevice(String address, String name, int rssi, long lastSeen, boolean isConnected) {
        this.address = address;
        this.name = name;
        this.rssi = rssi;
        this.lastSeen = lastSeen;
        this.isConnected = isConnected;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NearbyDevice that = (NearbyDevice) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "NearbyDevice{" +
                "address='" + address + '\'' +
                ", name='" + name + '\'' +
                ", rssi=" + rssi +
                ", lastSeen=" + lastSeen +
                ", isConnected=" + isConnected +
                '}';
    }
}
