package org.librecommunications.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a trusted contact.
 * Trusted contacts receive SOS alerts, panic notifications,
 * and dead-man's-switch messages.
 */
@Entity(
    tableName = "trusted_contacts",
    foreignKeys = @ForeignKey(
        entity = ContactEntity.class,
        parentColumns = "id",
        childColumns = "contact_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "contact_id")
)
public class TrustedContactEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "contact_id")
    private String contactId;

    @ColumnInfo(name = "added_at")
    private long addedAt;

    @ColumnInfo(name = "sos_message")
    private String sosMessage;

    /**
     * Constructs a new TrustedContactEntity.
     *
     * @param id         Unique identifier for this trusted-contact record.
     * @param contactId  FK reference to the associated ContactEntity.
     * @param addedAt    Timestamp (epoch ms) when the contact was trusted.
     * @param sosMessage Custom SOS message to send to this contact in emergencies.
     */
    public TrustedContactEntity(@NonNull String id, String contactId, long addedAt, String sosMessage) {
        this.id = id;
        this.contactId = contactId;
        this.addedAt = addedAt;
        this.sosMessage = sosMessage;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public String getSosMessage() {
        return sosMessage;
    }

    public void setSosMessage(String sosMessage) {
        this.sosMessage = sosMessage;
    }
}
