package org.librecommunications.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a conversation thread.
 * Links to a contact and tracks the last message preview, unread count,
 * pin/mute status, and auto-delete interval.
 */
@Entity(
    tableName = "conversations",
    foreignKeys = @ForeignKey(
        entity = ContactEntity.class,
        parentColumns = "id",
        childColumns = "contact_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "contact_id")
)
public class ConversationEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "contact_id")
    private String contactId;

    @ColumnInfo(name = "last_message_preview")
    private String lastMessagePreview;

    @ColumnInfo(name = "last_message_time")
    private long lastMessageTime;

    @ColumnInfo(name = "unread_count")
    private int unreadCount;

    @ColumnInfo(name = "is_pinned")
    private boolean isPinned;

    @ColumnInfo(name = "is_muted")
    private boolean isMuted;

    @ColumnInfo(name = "auto_delete_interval")
    private long autoDeleteInterval;

    /**
     * Constructs a new ConversationEntity.
     *
     * @param id                 Unique identifier for the conversation.
     * @param contactId          ID of the associated contact.
     * @param lastMessagePreview Preview text of the last message.
     * @param lastMessageTime    Timestamp (epoch ms) of the last message.
     * @param unreadCount        Number of unread messages.
     * @param isPinned           Whether the conversation is pinned to the top.
     * @param isMuted            Whether notifications are muted.
     * @param autoDeleteInterval Interval in ms after which messages auto-delete (0 = disabled).
     */
    public ConversationEntity(@NonNull String id, String contactId, String lastMessagePreview,
                               long lastMessageTime, int unreadCount, boolean isPinned,
                               boolean isMuted, long autoDeleteInterval) {
        this.id = id;
        this.contactId = contactId;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount = unreadCount;
        this.isPinned = isPinned;
        this.isMuted = isMuted;
        this.autoDeleteInterval = autoDeleteInterval;
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

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
    }

    public long getAutoDeleteInterval() {
        return autoDeleteInterval;
    }

    public void setAutoDeleteInterval(long autoDeleteInterval) {
        this.autoDeleteInterval = autoDeleteInterval;
    }
}
