package org.librecommunications.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.librecommunications.app.data.entity.Contact;

import java.util.List;

@Dao
public interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Contact contact);

    @Update
    void update(Contact contact);

    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    LiveData<List<Contact>> getAllContacts();

    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId")
    Contact getContactById(String deviceId);
    
    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId")
    LiveData<Contact> getContactByIdLiveData(String deviceId);
    
    @Query("UPDATE contacts SET isOnline = :isOnline WHERE deviceId = :deviceId")
    void setOnlineStatus(String deviceId, boolean isOnline);
    
    @Query("DELETE FROM contacts")
    void deleteAll();
}
