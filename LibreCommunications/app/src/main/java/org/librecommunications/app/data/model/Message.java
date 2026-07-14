package org.librecommunications.app.data.model;

import org.librecommunications.app.data.db.entity.MessageEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * UI-layer message model that provides display-ready fields.
 * Constructed from a {@link MessageEntity} for use in adapters and view-models.
 */
public class Message {

    private final String id;
    private final String senderName;
    private final String content;
    private final long timestamp;
    private final boolean isOutgoing;
    private final int encryptionStatus;

    /**
     * Constructs a Message from a {@link MessageEntity}.
     *
     * @param entity     The Room entity to convert.
     * @param senderName The resolved display name of the sender.
     */
    public Message(MessageEntity entity, String senderName) {
        this.id = entity.getId();
        this.senderName = senderName;
        this.content = entity.getContent();
        this.timestamp = entity.getTimestamp();
        this.isOutgoing = entity.isOutgoing();
        this.encryptionStatus = entity.getEncryptionStatus();
    }

    /**
     * Full-parameter constructor for manual creation.
     *
     * @param id               Unique message identifier.
     * @param senderName       Display name of the sender.
     * @param content          Message content text.
     * @param timestamp        Epoch-ms timestamp.
     * @param isOutgoing       Whether the local user sent this message.
     * @param encryptionStatus Encryption verification status.
     */
    public Message(String id, String senderName, String content, long timestamp,
                   boolean isOutgoing, int encryptionStatus) {
        this.id = id;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.isOutgoing = isOutgoing;
        this.encryptionStatus = encryptionStatus;
    }

    public String getId() {
        return id;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public int getEncryptionStatus() {
        return encryptionStatus;
    }

    /**
     * Returns the timestamp formatted as HH:mm for display in chat bubbles.
     *
     * @return Formatted time string.
     */
    public String getFormattedTime() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", senderName='" + senderName + '\'' +
                ", content='" + content + '\'' +
                ", time=" + getFormattedTime() +
                ", outgoing=" + isOutgoing +
                '}';
    }
}
