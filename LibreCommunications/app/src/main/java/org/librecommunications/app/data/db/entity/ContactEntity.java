package org.librecommunications.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a contact in the Libre Communications app.
 * Stores identity, display name, public key fingerprint, verification status,
 * and online presence information.
 */
@Entity(tableName = "contacts")
public class ContactEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "display_name")
    private String displayName;

    @ColumnInfo(name = "public_key_fingerprint")
    private String publicKeyFingerprint;

    @ColumnInfo(name = "is_verified")
    private boolean isVerified;

    @ColumnInfo(name = "last_seen")
    private long lastSeen;

    @ColumnInfo(name = "is_online")
    private boolean isOnline;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    /**
     * Constructs a new ContactEntity.
     *
     * @param id                   Unique identifier for the contact.
     * @param displayName          Display name shown in the UI.
     * @param publicKeyFingerprint Hex-encoded fingerprint of the contact's public key.
     * @param isVerified           Whether the contact's identity has been verified.
     * @param lastSeen             Timestamp (epoch ms) of last known activity.
     * @param isOnline             Whether the contact is currently online.
     * @param createdAt            Timestamp (epoch ms) when the contact was added.
     */
    public ContactEntity(@NonNull String id, String displayName, String publicKeyFingerprint,
                          boolean isVerified, long lastSeen, boolean isOnline, long createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.publicKeyFingerprint = publicKeyFingerprint;
        this.isVerified = isVerified;
        this.lastSeen = lastSeen;
        this.isOnline = isOnline;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
