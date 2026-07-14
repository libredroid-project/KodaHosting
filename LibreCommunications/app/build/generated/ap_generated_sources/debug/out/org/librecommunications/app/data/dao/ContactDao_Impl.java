package org.librecommunications.app.data.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import org.librecommunications.app.data.entity.Contact;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ContactDao_Impl implements ContactDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Contact> __insertionAdapterOfContact;

  private final EntityDeletionOrUpdateAdapter<Contact> __updateAdapterOfContact;

  private final SharedSQLiteStatement __preparedStmtOfSetOnlineStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ContactDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfContact = new EntityInsertionAdapter<Contact>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `contacts` (`deviceId`,`name`,`publicKey`,`lastSeen`,`isTrusted`,`isOnline`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Contact entity) {
        if (entity.deviceId == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.deviceId);
        }
        if (entity.name == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.name);
        }
        if (entity.publicKey == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.publicKey);
        }
        statement.bindLong(4, entity.lastSeen);
        final int _tmp = entity.isTrusted ? 1 : 0;
        statement.bindLong(5, _tmp);
        final int _tmp_1 = entity.isOnline ? 1 : 0;
        statement.bindLong(6, _tmp_1);
      }
    };
    this.__updateAdapterOfContact = new EntityDeletionOrUpdateAdapter<Contact>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `contacts` SET `deviceId` = ?,`name` = ?,`publicKey` = ?,`lastSeen` = ?,`isTrusted` = ?,`isOnline` = ? WHERE `deviceId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Contact entity) {
        if (entity.deviceId == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.deviceId);
        }
        if (entity.name == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.name);
        }
        if (entity.publicKey == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.publicKey);
        }
        statement.bindLong(4, entity.lastSeen);
        final int _tmp = entity.isTrusted ? 1 : 0;
        statement.bindLong(5, _tmp);
        final int _tmp_1 = entity.isOnline ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        if (entity.deviceId == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.deviceId);
        }
      }
    };
    this.__preparedStmtOfSetOnlineStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE contacts SET isOnline = ? WHERE deviceId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM contacts";
        return _query;
      }
    };
  }

  @Override
  public void insert(final Contact contact) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfContact.insert(contact);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final Contact contact) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfContact.handle(contact);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void setOnlineStatus(final String deviceId, final boolean isOnline) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetOnlineStatus.acquire();
    int _argIndex = 1;
    final int _tmp = isOnline ? 1 : 0;
    _stmt.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    if (deviceId == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, deviceId);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetOnlineStatus.release(_stmt);
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public LiveData<List<Contact>> getAllContacts() {
    final String _sql = "SELECT * FROM contacts ORDER BY lastSeen DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"contacts"}, false, new Callable<List<Contact>>() {
      @Override
      @Nullable
      public List<Contact> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
          final int _cursorIndexOfLastSeen = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeen");
          final int _cursorIndexOfIsTrusted = CursorUtil.getColumnIndexOrThrow(_cursor, "isTrusted");
          final int _cursorIndexOfIsOnline = CursorUtil.getColumnIndexOrThrow(_cursor, "isOnline");
          final List<Contact> _result = new ArrayList<Contact>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Contact _item;
            final String _tmpDeviceId;
            if (_cursor.isNull(_cursorIndexOfDeviceId)) {
              _tmpDeviceId = null;
            } else {
              _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            }
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpPublicKey;
            if (_cursor.isNull(_cursorIndexOfPublicKey)) {
              _tmpPublicKey = null;
            } else {
              _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            }
            final long _tmpLastSeen;
            _tmpLastSeen = _cursor.getLong(_cursorIndexOfLastSeen);
            final boolean _tmpIsTrusted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsTrusted);
            _tmpIsTrusted = _tmp != 0;
            final boolean _tmpIsOnline;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsOnline);
            _tmpIsOnline = _tmp_1 != 0;
            _item = new Contact(_tmpDeviceId,_tmpName,_tmpPublicKey,_tmpLastSeen,_tmpIsTrusted,_tmpIsOnline);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Contact getContactById(final String deviceId) {
    final String _sql = "SELECT * FROM contacts WHERE deviceId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (deviceId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, deviceId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
      final int _cursorIndexOfLastSeen = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeen");
      final int _cursorIndexOfIsTrusted = CursorUtil.getColumnIndexOrThrow(_cursor, "isTrusted");
      final int _cursorIndexOfIsOnline = CursorUtil.getColumnIndexOrThrow(_cursor, "isOnline");
      final Contact _result;
      if (_cursor.moveToFirst()) {
        final String _tmpDeviceId;
        if (_cursor.isNull(_cursorIndexOfDeviceId)) {
          _tmpDeviceId = null;
        } else {
          _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
        }
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpPublicKey;
        if (_cursor.isNull(_cursorIndexOfPublicKey)) {
          _tmpPublicKey = null;
        } else {
          _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
        }
        final long _tmpLastSeen;
        _tmpLastSeen = _cursor.getLong(_cursorIndexOfLastSeen);
        final boolean _tmpIsTrusted;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsTrusted);
        _tmpIsTrusted = _tmp != 0;
        final boolean _tmpIsOnline;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfIsOnline);
        _tmpIsOnline = _tmp_1 != 0;
        _result = new Contact(_tmpDeviceId,_tmpName,_tmpPublicKey,_tmpLastSeen,_tmpIsTrusted,_tmpIsOnline);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<Contact> getContactByIdLiveData(final String deviceId) {
    final String _sql = "SELECT * FROM contacts WHERE deviceId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (deviceId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, deviceId);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"contacts"}, false, new Callable<Contact>() {
      @Override
      @Nullable
      public Contact call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
          final int _cursorIndexOfLastSeen = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeen");
          final int _cursorIndexOfIsTrusted = CursorUtil.getColumnIndexOrThrow(_cursor, "isTrusted");
          final int _cursorIndexOfIsOnline = CursorUtil.getColumnIndexOrThrow(_cursor, "isOnline");
          final Contact _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDeviceId;
            if (_cursor.isNull(_cursorIndexOfDeviceId)) {
              _tmpDeviceId = null;
            } else {
              _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            }
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpPublicKey;
            if (_cursor.isNull(_cursorIndexOfPublicKey)) {
              _tmpPublicKey = null;
            } else {
              _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            }
            final long _tmpLastSeen;
            _tmpLastSeen = _cursor.getLong(_cursorIndexOfLastSeen);
            final boolean _tmpIsTrusted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsTrusted);
            _tmpIsTrusted = _tmp != 0;
            final boolean _tmpIsOnline;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsOnline);
            _tmpIsOnline = _tmp_1 != 0;
            _result = new Contact(_tmpDeviceId,_tmpName,_tmpPublicKey,_tmpLastSeen,_tmpIsTrusted,_tmpIsOnline);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
