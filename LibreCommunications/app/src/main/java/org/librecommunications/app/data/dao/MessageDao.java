package org.librecommunications.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.librecommunications.app.data.entity.Message;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesForContact(String contactId);
    
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC LIMIT 1")
    Message getLastMessageForContact(String contactId);
    
    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    void updateMessageStatus(String messageId, int status);

    @Query("DELETE FROM messages")
    void deleteAll();
}
