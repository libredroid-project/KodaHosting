package org.librecommunications.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "contacts")
public class Contact {

    @PrimaryKey
    @NonNull
    public String deviceId; // e.g. Wi-Fi Direct MAC or a generated UUID

    public String name;
    
    // For Signal Protocol
    public String publicKey;
    
    public long lastSeen;
    public boolean isTrusted;
    public boolean isOnline;

    public Contact(@NonNull String deviceId, String name, String publicKey, long lastSeen, boolean isTrusted, boolean isOnline) {
        this.deviceId = deviceId;
        this.name = name;
        this.publicKey = publicKey;
        this.lastSeen = lastSeen;
        this.isTrusted = isTrusted;
        this.isOnline = isOnline;
    }
}
