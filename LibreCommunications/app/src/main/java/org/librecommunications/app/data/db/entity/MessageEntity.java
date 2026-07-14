package org.librecommunications.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing an individual message in a conversation.
 * Supports multiple message types, encryption status tracking,
 * and optional auto-delete scheduling.
 */
@Entity(
    tableName = "messages",
    foreignKeys = @ForeignKey(
        entity = ConversationEntity.class,
        parentColumns = "id",
        childColumns = "conversation_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = "conversation_id"),
        @Index(value = "timestamp")
    }
)
public class MessageEntity {

    /** Message type constants */
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_VOICE = 3;
    public static final int TYPE_LOCATION = 4;

    /** Encryption status constants */
    public static final int ENCRYPTION_ENCRYPTED = 0;
    public static final int ENCRYPTION_VERIFIED = 1;
    public static final int ENCRYPTION_FAILED = 2;

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "conversation_id")
    private String conversationId;

    @ColumnInfo(name = "sender_id")
    private String senderId;

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "message_type")
    private int messageType;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "is_outgoing")
    private boolean isOutgoing;

    @ColumnInfo(name = "is_read")
    private boolean isRead;

    @ColumnInfo(name = "encryption_status")
    private int encryptionStatus;

    @Nullable
    @ColumnInfo(name = "auto_delete_at")
    private Long autoDeleteAt;

    /**
     * Constructs a new MessageEntity.
     *
     * @param id               Unique message identifier.
     * @param conversationId   ID of the parent conversation.
     * @param senderId         ID of the message sender.
     * @param content          Encrypted message content.
     * @param messageType      Type of message (TEXT, FILE, IMAGE, VOICE, LOCATION).
     * @param timestamp        Timestamp (epoch ms) when message was created.
     * @param isOutgoing       Whether this message was sent by the local user.
     * @param isRead           Whether the message has been read.
     * @param encryptionStatus Encryption status (ENCRYPTED, VERIFIED, FAILED).
     * @param autoDeleteAt     Optional timestamp (epoch ms) when this message should be deleted.
     */
    public MessageEntity(@NonNull String id, String conversationId, String senderId,
                          String content, int messageType, long timestamp,
                          boolean isOutgoing, boolean isRead, int encryptionStatus,
                          @Nullable Long autoDeleteAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.messageType = messageType;
        this.timestamp = timestamp;
        this.isOutgoing = isOutgoing;
        this.isRead = isRead;
        this.encryptionStatus = encryptionStatus;
        this.autoDeleteAt = autoDeleteAt;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public void setOutgoing(boolean outgoing) {
        isOutgoing = outgoing;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public int getEncryptionStatus() {
        return encryptionStatus;
    }

    public void setEncryptionStatus(int encryptionStatus) {
        this.encryptionStatus = encryptionStatus;
    }

    @Nullable
    public Long getAutoDeleteAt() {
        return autoDeleteAt;
    }

    public void setAutoDeleteAt(@Nullable Long autoDeleteAt) {
        this.autoDeleteAt = autoDeleteAt;
    }
}
