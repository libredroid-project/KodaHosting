package org.librecommunications.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a message queued for delivery.
 * Used to persist outgoing messages when the recipient is offline,
 * with retry counting and delivery status tracking.
 */
@Entity(tableName = "queued_messages")
public class QueuedMessageEntity {

    /** Delivery status constants */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENDING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_FAILED = 3;

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "recipient_id")
    private String recipientId;

    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    private byte[] encryptedPayload;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "retry_count")
    private int retryCount;

    @ColumnInfo(name = "status")
    private int status;

    /**
     * Constructs a new QueuedMessageEntity.
     *
     * @param id               Unique identifier for the queued message.
     * @param recipientId      ID of the intended recipient.
     * @param encryptedPayload Encrypted message payload bytes.
     * @param timestamp        Timestamp (epoch ms) when the message was queued.
     * @param retryCount       Number of delivery attempts made.
     * @param status           Current delivery status (PENDING, SENDING, SENT, FAILED).
     */
    public QueuedMessageEntity(@NonNull String id, String recipientId, byte[] encryptedPayload,
                                long timestamp, int retryCount, int status) {
        this.id = id;
        this.recipientId = recipientId;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
        this.status = status;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(byte[] encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
