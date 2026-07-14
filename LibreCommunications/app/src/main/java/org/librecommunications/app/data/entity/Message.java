package org.librecommunications.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
    tableName = "messages",
    foreignKeys = @ForeignKey(
        entity = Contact.class,
        parentColumns = "deviceId",
        childColumns = "contactId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("contactId")}
)
public class Message {

    @PrimaryKey
    @NonNull
    public String messageId;

    @NonNull
    public String contactId;

    public String content;
    
    public long timestamp;
    
    public boolean isOutgoing;
    
    public int status; // 0 = Sending, 1 = Sent, 2 = Delivered, 3 = Failed
    
    public int messageType; // 0 = TEXT, 1 = IMAGE, 2 = FILE
    public String mediaUri; // Local path to the file/image

    public Message(@NonNull String messageId, @NonNull String contactId, String content, long timestamp, boolean isOutgoing, int status, int messageType, String mediaUri) {
        this.messageId = messageId;
        this.contactId = contactId;
        this.content = content;
        this.timestamp = timestamp;
        this.isOutgoing = isOutgoing;
        this.status = status;
        this.messageType = messageType;
        this.mediaUri = mediaUri;
    }

    public static Message createOutgoingText(String contactId, String content) {
        return new Message(UUID.randomUUID().toString(), contactId, content, System.currentTimeMillis(), true, 0, 0, null);
    }
    
    public static Message createOutgoingMedia(String contactId, int type, String uri) {
        return new Message(UUID.randomUUID().toString(), contactId, null, System.currentTimeMillis(), true, 0, type, uri);
    }
}
