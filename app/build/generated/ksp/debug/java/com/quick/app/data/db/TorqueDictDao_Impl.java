package com.quick.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TorqueDictDao_Impl implements TorqueDictDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TorqueDict> __insertionAdapterOfTorqueDict;

  private final EntityInsertionAdapter<TorqueDict> __insertionAdapterOfTorqueDict_1;

  private final EntityDeletionOrUpdateAdapter<TorqueDict> __updateAdapterOfTorqueDict;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public TorqueDictDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTorqueDict = new EntityInsertionAdapter<TorqueDict>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `torque_dict` (`id`,`kind`,`name`,`enabled`,`createdAt`,`updatedAt`,`minValue`,`maxValue`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TorqueDict entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getName());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getUpdatedAt());
        if (entity.getMinValue() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getMinValue());
        }
        if (entity.getMaxValue() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getMaxValue());
        }
      }
    };
    this.__insertionAdapterOfTorqueDict_1 = new EntityInsertionAdapter<TorqueDict>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `torque_dict` (`id`,`kind`,`name`,`enabled`,`createdAt`,`updatedAt`,`minValue`,`maxValue`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TorqueDict entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getName());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getUpdatedAt());
        if (entity.getMinValue() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getMinValue());
        }
        if (entity.getMaxValue() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getMaxValue());
        }
      }
    };
    this.__updateAdapterOfTorqueDict = new EntityDeletionOrUpdateAdapter<TorqueDict>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `torque_dict` SET `id` = ?,`kind` = ?,`name` = ?,`enabled` = ?,`createdAt` = ?,`updatedAt` = ?,`minValue` = ?,`maxValue` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TorqueDict entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getName());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getUpdatedAt());
        if (entity.getMinValue() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getMinValue());
        }
        if (entity.getMaxValue() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getMaxValue());
        }
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM torque_dict WHERE kind = ? AND name = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TorqueDict item, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTorqueDict.insertAndReturnId(item);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertIgnoreAll(final List<TorqueDict> items,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfTorqueDict_1.insertAndReturnIdsList(items);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final TorqueDict item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTorqueDict.handle(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String kind, final String name,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, kind);
        _argIndex = 2;
        _stmt.bindString(_argIndex, name);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TorqueDict>> all(final String kind) {
    final String _sql = "SELECT * FROM torque_dict WHERE kind = ? ORDER BY name";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, kind);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_dict"}, new Callable<List<TorqueDict>>() {
      @Override
      @NonNull
      public List<TorqueDict> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfMinValue = CursorUtil.getColumnIndexOrThrow(_cursor, "minValue");
          final int _cursorIndexOfMaxValue = CursorUtil.getColumnIndexOrThrow(_cursor, "maxValue");
          final List<TorqueDict> _result = new ArrayList<TorqueDict>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TorqueDict _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final Double _tmpMinValue;
            if (_cursor.isNull(_cursorIndexOfMinValue)) {
              _tmpMinValue = null;
            } else {
              _tmpMinValue = _cursor.getDouble(_cursorIndexOfMinValue);
            }
            final Double _tmpMaxValue;
            if (_cursor.isNull(_cursorIndexOfMaxValue)) {
              _tmpMaxValue = null;
            } else {
              _tmpMaxValue = _cursor.getDouble(_cursorIndexOfMaxValue);
            }
            _item = new TorqueDict(_tmpId,_tmpKind,_tmpName,_tmpEnabled,_tmpCreatedAt,_tmpUpdatedAt,_tmpMinValue,_tmpMaxValue);
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
  public Flow<List<TorqueDict>> allKinds() {
    final String _sql = "SELECT * FROM torque_dict ORDER BY kind, name";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_dict"}, new Callable<List<TorqueDict>>() {
      @Override
      @NonNull
      public List<TorqueDict> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfMinValue = CursorUtil.getColumnIndexOrThrow(_cursor, "minValue");
          final int _cursorIndexOfMaxValue = CursorUtil.getColumnIndexOrThrow(_cursor, "maxValue");
          final List<TorqueDict> _result = new ArrayList<TorqueDict>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TorqueDict _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final Double _tmpMinValue;
            if (_cursor.isNull(_cursorIndexOfMinValue)) {
              _tmpMinValue = null;
            } else {
              _tmpMinValue = _cursor.getDouble(_cursorIndexOfMinValue);
            }
            final Double _tmpMaxValue;
            if (_cursor.isNull(_cursorIndexOfMaxValue)) {
              _tmpMaxValue = null;
            } else {
              _tmpMaxValue = _cursor.getDouble(_cursorIndexOfMaxValue);
            }
            _item = new TorqueDict(_tmpId,_tmpKind,_tmpName,_tmpEnabled,_tmpCreatedAt,_tmpUpdatedAt,_tmpMinValue,_tmpMaxValue);
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
  public Object snapshot(final String kind,
      final Continuation<? super List<TorqueDict>> $completion) {
    final String _sql = "SELECT * FROM torque_dict WHERE kind = ? ORDER BY name";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, kind);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TorqueDict>>() {
      @Override
      @NonNull
      public List<TorqueDict> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfMinValue = CursorUtil.getColumnIndexOrThrow(_cursor, "minValue");
          final int _cursorIndexOfMaxValue = CursorUtil.getColumnIndexOrThrow(_cursor, "maxValue");
          final List<TorqueDict> _result = new ArrayList<TorqueDict>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TorqueDict _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final Double _tmpMinValue;
            if (_cursor.isNull(_cursorIndexOfMinValue)) {
              _tmpMinValue = null;
            } else {
              _tmpMinValue = _cursor.getDouble(_cursorIndexOfMinValue);
            }
            final Double _tmpMaxValue;
            if (_cursor.isNull(_cursorIndexOfMaxValue)) {
              _tmpMaxValue = null;
            } else {
              _tmpMaxValue = _cursor.getDouble(_cursorIndexOfMaxValue);
            }
            _item = new TorqueDict(_tmpId,_tmpKind,_tmpName,_tmpEnabled,_tmpCreatedAt,_tmpUpdatedAt,_tmpMinValue,_tmpMaxValue);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object snapshotAll(final Continuation<? super List<TorqueDict>> $completion) {
    final String _sql = "SELECT * FROM torque_dict ORDER BY kind, name";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TorqueDict>>() {
      @Override
      @NonNull
      public List<TorqueDict> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfMinValue = CursorUtil.getColumnIndexOrThrow(_cursor, "minValue");
          final int _cursorIndexOfMaxValue = CursorUtil.getColumnIndexOrThrow(_cursor, "maxValue");
          final List<TorqueDict> _result = new ArrayList<TorqueDict>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TorqueDict _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final Double _tmpMinValue;
            if (_cursor.isNull(_cursorIndexOfMinValue)) {
              _tmpMinValue = null;
            } else {
              _tmpMinValue = _cursor.getDouble(_cursorIndexOfMinValue);
            }
            final Double _tmpMaxValue;
            if (_cursor.isNull(_cursorIndexOfMaxValue)) {
              _tmpMaxValue = null;
            } else {
              _tmpMaxValue = _cursor.getDouble(_cursorIndexOfMaxValue);
            }
            _item = new TorqueDict(_tmpId,_tmpKind,_tmpName,_tmpEnabled,_tmpCreatedAt,_tmpUpdatedAt,_tmpMinValue,_tmpMaxValue);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object find(final String kind, final String name,
      final Continuation<? super TorqueDict> $completion) {
    final String _sql = "SELECT * FROM torque_dict WHERE kind = ? AND name = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, kind);
    _argIndex = 2;
    _statement.bindString(_argIndex, name);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TorqueDict>() {
      @Override
      @Nullable
      public TorqueDict call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfMinValue = CursorUtil.getColumnIndexOrThrow(_cursor, "minValue");
          final int _cursorIndexOfMaxValue = CursorUtil.getColumnIndexOrThrow(_cursor, "maxValue");
          final TorqueDict _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final Double _tmpMinValue;
            if (_cursor.isNull(_cursorIndexOfMinValue)) {
              _tmpMinValue = null;
            } else {
              _tmpMinValue = _cursor.getDouble(_cursorIndexOfMinValue);
            }
            final Double _tmpMaxValue;
            if (_cursor.isNull(_cursorIndexOfMaxValue)) {
              _tmpMaxValue = null;
            } else {
              _tmpMaxValue = _cursor.getDouble(_cursorIndexOfMaxValue);
            }
            _result = new TorqueDict(_tmpId,_tmpKind,_tmpName,_tmpEnabled,_tmpCreatedAt,_tmpUpdatedAt,_tmpMinValue,_tmpMaxValue);
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
