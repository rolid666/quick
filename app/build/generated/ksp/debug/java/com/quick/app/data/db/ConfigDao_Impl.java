package com.quick.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConfigDao_Impl implements ConfigDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DeviceConfig> __insertionAdapterOfDeviceConfig;

  public ConfigDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDeviceConfig = new EntityInsertionAdapter<DeviceConfig>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `device_config` (`id`,`name`,`ip`,`port`,`unitId`,`pollIntervalMs`,`timeoutMs`,`autoStart`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DeviceConfig entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getIp());
        statement.bindLong(4, entity.getPort());
        statement.bindLong(5, entity.getUnitId());
        statement.bindLong(6, entity.getPollIntervalMs());
        statement.bindLong(7, entity.getTimeoutMs());
        final int _tmp = entity.getAutoStart() ? 1 : 0;
        statement.bindLong(8, _tmp);
      }
    };
  }

  @Override
  public Object put(final DeviceConfig cfg, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDeviceConfig.insert(cfg);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<DeviceConfig> get() {
    final String _sql = "SELECT * FROM device_config WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"device_config"}, new Callable<DeviceConfig>() {
      @Override
      @Nullable
      public DeviceConfig call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfUnitId = CursorUtil.getColumnIndexOrThrow(_cursor, "unitId");
          final int _cursorIndexOfPollIntervalMs = CursorUtil.getColumnIndexOrThrow(_cursor, "pollIntervalMs");
          final int _cursorIndexOfTimeoutMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutMs");
          final int _cursorIndexOfAutoStart = CursorUtil.getColumnIndexOrThrow(_cursor, "autoStart");
          final DeviceConfig _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpIp;
            _tmpIp = _cursor.getString(_cursorIndexOfIp);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final int _tmpUnitId;
            _tmpUnitId = _cursor.getInt(_cursorIndexOfUnitId);
            final long _tmpPollIntervalMs;
            _tmpPollIntervalMs = _cursor.getLong(_cursorIndexOfPollIntervalMs);
            final long _tmpTimeoutMs;
            _tmpTimeoutMs = _cursor.getLong(_cursorIndexOfTimeoutMs);
            final boolean _tmpAutoStart;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoStart);
            _tmpAutoStart = _tmp != 0;
            _result = new DeviceConfig(_tmpId,_tmpName,_tmpIp,_tmpPort,_tmpUnitId,_tmpPollIntervalMs,_tmpTimeoutMs,_tmpAutoStart);
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

  @Override
  public Object getOnce(final Continuation<? super DeviceConfig> $completion) {
    final String _sql = "SELECT * FROM device_config WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DeviceConfig>() {
      @Override
      @Nullable
      public DeviceConfig call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfUnitId = CursorUtil.getColumnIndexOrThrow(_cursor, "unitId");
          final int _cursorIndexOfPollIntervalMs = CursorUtil.getColumnIndexOrThrow(_cursor, "pollIntervalMs");
          final int _cursorIndexOfTimeoutMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutMs");
          final int _cursorIndexOfAutoStart = CursorUtil.getColumnIndexOrThrow(_cursor, "autoStart");
          final DeviceConfig _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpIp;
            _tmpIp = _cursor.getString(_cursorIndexOfIp);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final int _tmpUnitId;
            _tmpUnitId = _cursor.getInt(_cursorIndexOfUnitId);
            final long _tmpPollIntervalMs;
            _tmpPollIntervalMs = _cursor.getLong(_cursorIndexOfPollIntervalMs);
            final long _tmpTimeoutMs;
            _tmpTimeoutMs = _cursor.getLong(_cursorIndexOfTimeoutMs);
            final boolean _tmpAutoStart;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoStart);
            _tmpAutoStart = _tmp != 0;
            _result = new DeviceConfig(_tmpId,_tmpName,_tmpIp,_tmpPort,_tmpUnitId,_tmpPollIntervalMs,_tmpTimeoutMs,_tmpAutoStart);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
