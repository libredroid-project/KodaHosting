package org.librecommunications.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import org.librecommunications.app.SecureStorage;
import org.librecommunications.app.data.dao.ContactDao;
import org.librecommunications.app.data.dao.MessageDao;
import org.librecommunications.app.data.entity.Contact;
import org.librecommunications.app.data.entity.Message;

@Database(entities = {Contact.class, Message.class}, version = 1, exportSchema = false)
public abstract class LibreDatabase extends RoomDatabase {

    public abstract ContactDao contactDao();
    public abstract MessageDao messageDao();

    private static volatile LibreDatabase INSTANCE;

    public static LibreDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (LibreDatabase.class) {
                if (INSTANCE == null) {
                    
                    // Retrieve or generate a high-entropy passphrase for the database
                    String dbPassphrase = SecureStorage.getInstance().getString("db_passphrase", null);
                    if (dbPassphrase == null) {
                        dbPassphrase = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString();
                        SecureStorage.getInstance().putString("db_passphrase", dbPassphrase);
                    }
                    
                    System.loadLibrary("sqlcipher");
                    
                    final byte[] passphrase = dbPassphrase.getBytes();
                    final SupportOpenHelperFactory factory = new SupportOpenHelperFactory(passphrase);
                    
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            LibreDatabase.class, "libre_secure.db")
                            .openHelperFactory(factory)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
