package com.quick.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
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
public final class RecordDao_Impl implements RecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MeasurementRecord> __insertionAdapterOfMeasurementRecord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByKey;

  public RecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMeasurementRecord = new EntityInsertionAdapter<MeasurementRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `measurement_record` (`id`,`snapshotKey`,`timestampMs`,`lineName`,`modelName`,`deviceInfo`,`deviceIp`,`targetTemp`,`tempLow`,`tempHigh`,`measuredTemp`,`leakageMv`,`result`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MeasurementRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSnapshotKey());
        statement.bindLong(3, entity.getTimestampMs());
        statement.bindString(4, entity.getLineName());
        statement.bindString(5, entity.getModelName());
        if (entity.getDeviceInfo() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDeviceInfo());
        }
        statement.bindString(7, entity.getDeviceIp());
        if (entity.getTargetTemp() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getTargetTemp());
        }
        if (entity.getTempLow() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getTempLow());
        }
        if (entity.getTempHigh() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getTempHigh());
        }
        if (entity.getMeasuredTemp() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getMeasuredTemp());
        }
        if (entity.getLeakageMv() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getLeakageMv());
        }
        statement.bindString(13, entity.getResult());
      }
    };
    this.__preparedStmtOfDeleteByKey = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM measurement_record WHERE snapshotKey = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final MeasurementRecord record,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMeasurementRecord.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByKey(final String key, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByKey.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, key);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByKey.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MeasurementRecord>> query(final Long fromMs, final Long toMs, final String line,
      final String model, final String result, final String kw) {
    final String _sql = "\n"
            + "        SELECT * FROM measurement_record\n"
            + "        WHERE (? IS NULL OR timestampMs >= ?)\n"
            + "          AND (? IS NULL OR timestampMs <= ?)\n"
            + "          AND (? IS NULL OR lineName = ?)\n"
            + "          AND (? IS NULL OR modelName = ?)\n"
            + "          AND (? IS NULL OR result = ?)\n"
            + "          AND (? IS NULL OR lineName LIKE '%' || ? || '%'\n"
            + "                       OR modelName LIKE '%' || ? || '%'\n"
            + "                       OR deviceInfo LIKE '%' || ? || '%')\n"
            + "        ORDER BY timestampMs DESC, id DESC\n"
            + "        LIMIT 20000\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 14);
    int _argIndex = 1;
    if (fromMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, fromMs);
    }
    _argIndex = 2;
    if (fromMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, fromMs);
    }
    _argIndex = 3;
    if (toMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, toMs);
    }
    _argIndex = 4;
    if (toMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, toMs);
    }
    _argIndex = 5;
    if (line == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, line);
    }
    _argIndex = 6;
    if (line == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, line);
    }
    _argIndex = 7;
    if (model == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, model);
    }
    _argIndex = 8;
    if (model == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, model);
    }
    _argIndex = 9;
    if (result == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, result);
    }
    _argIndex = 10;
    if (result == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, result);
    }
    _argIndex = 11;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 12;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 13;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 14;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"measurement_record"}, new Callable<List<MeasurementRecord>>() {
      @Override
      @NonNull
      public List<MeasurementRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSnapshotKey = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotKey");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfLineName = CursorUtil.getColumnIndexOrThrow(_cursor, "lineName");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfDeviceInfo = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceInfo");
          final int _cursorIndexOfDeviceIp = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceIp");
          final int _cursorIndexOfTargetTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "targetTemp");
          final int _cursorIndexOfTempLow = CursorUtil.getColumnIndexOrThrow(_cursor, "tempLow");
          final int _cursorIndexOfTempHigh = CursorUtil.getColumnIndexOrThrow(_cursor, "tempHigh");
          final int _cursorIndexOfMeasuredTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredTemp");
          final int _cursorIndexOfLeakageMv = CursorUtil.getColumnIndexOrThrow(_cursor, "leakageMv");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final List<MeasurementRecord> _result = new ArrayList<MeasurementRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MeasurementRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSnapshotKey;
            _tmpSnapshotKey = _cursor.getString(_cursorIndexOfSnapshotKey);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final String _tmpLineName;
            _tmpLineName = _cursor.getString(_cursorIndexOfLineName);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final String _tmpDeviceInfo;
            if (_cursor.isNull(_cursorIndexOfDeviceInfo)) {
              _tmpDeviceInfo = null;
            } else {
              _tmpDeviceInfo = _cursor.getString(_cursorIndexOfDeviceInfo);
            }
            final String _tmpDeviceIp;
            _tmpDeviceIp = _cursor.getString(_cursorIndexOfDeviceIp);
            final Integer _tmpTargetTemp;
            if (_cursor.isNull(_cursorIndexOfTargetTemp)) {
              _tmpTargetTemp = null;
            } else {
              _tmpTargetTemp = _cursor.getInt(_cursorIndexOfTargetTemp);
            }
            final Integer _tmpTempLow;
            if (_cursor.isNull(_cursorIndexOfTempLow)) {
              _tmpTempLow = null;
            } else {
              _tmpTempLow = _cursor.getInt(_cursorIndexOfTempLow);
            }
            final Integer _tmpTempHigh;
            if (_cursor.isNull(_cursorIndexOfTempHigh)) {
              _tmpTempHigh = null;
            } else {
              _tmpTempHigh = _cursor.getInt(_cursorIndexOfTempHigh);
            }
            final Double _tmpMeasuredTemp;
            if (_cursor.isNull(_cursorIndexOfMeasuredTemp)) {
              _tmpMeasuredTemp = null;
            } else {
              _tmpMeasuredTemp = _cursor.getDouble(_cursorIndexOfMeasuredTemp);
            }
            final Double _tmpLeakageMv;
            if (_cursor.isNull(_cursorIndexOfLeakageMv)) {
              _tmpLeakageMv = null;
            } else {
              _tmpLeakageMv = _cursor.getDouble(_cursorIndexOfLeakageMv);
            }
            final String _tmpResult;
            _tmpResult = _cursor.getString(_cursorIndexOfResult);
            _item = new MeasurementRecord(_tmpId,_tmpSnapshotKey,_tmpTimestampMs,_tmpLineName,_tmpModelName,_tmpDeviceInfo,_tmpDeviceIp,_tmpTargetTemp,_tmpTempLow,_tmpTempHigh,_tmpMeasuredTemp,_tmpLeakageMv,_tmpResult);
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
  public Flow<Integer> count(final Long fromMs, final Long toMs, final String line,
      final String model, final String result) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM measurement_record\n"
            + "        WHERE (? IS NULL OR timestampMs >= ?)\n"
            + "          AND (? IS NULL OR timestampMs <= ?)\n"
            + "          AND (? IS NULL OR lineName = ?)\n"
            + "          AND (? IS NULL OR modelName = ?)\n"
            + "          AND (? IS NULL OR result = ?)\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 10);
    int _argIndex = 1;
    if (fromMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, fromMs);
    }
    _argIndex = 2;
    if (fromMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, fromMs);
    }
    _argIndex = 3;
    if (toMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, toMs);
    }
    _argIndex = 4;
    if (toMs == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, toMs);
    }
    _argIndex = 5;
    if (line == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, line);
    }
    _argIndex = 6;
    if (line == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, line);
    }
    _argIndex = 7;
    if (model == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, model);
    }
    _argIndex = 8;
    if (model == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, model);
    }
    _argIndex = 9;
    if (result == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, result);
    }
    _argIndex = 10;
    if (result == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, result);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"measurement_record"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Flow<MeasurementRecord> lastOne() {
    final String _sql = "SELECT * FROM measurement_record ORDER BY timestampMs DESC, id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"measurement_record"}, new Callable<MeasurementRecord>() {
      @Override
      @Nullable
      public MeasurementRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSnapshotKey = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotKey");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfLineName = CursorUtil.getColumnIndexOrThrow(_cursor, "lineName");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfDeviceInfo = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceInfo");
          final int _cursorIndexOfDeviceIp = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceIp");
          final int _cursorIndexOfTargetTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "targetTemp");
          final int _cursorIndexOfTempLow = CursorUtil.getColumnIndexOrThrow(_cursor, "tempLow");
          final int _cursorIndexOfTempHigh = CursorUtil.getColumnIndexOrThrow(_cursor, "tempHigh");
          final int _cursorIndexOfMeasuredTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredTemp");
          final int _cursorIndexOfLeakageMv = CursorUtil.getColumnIndexOrThrow(_cursor, "leakageMv");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final MeasurementRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSnapshotKey;
            _tmpSnapshotKey = _cursor.getString(_cursorIndexOfSnapshotKey);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final String _tmpLineName;
            _tmpLineName = _cursor.getString(_cursorIndexOfLineName);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final String _tmpDeviceInfo;
            if (_cursor.isNull(_cursorIndexOfDeviceInfo)) {
              _tmpDeviceInfo = null;
            } else {
              _tmpDeviceInfo = _cursor.getString(_cursorIndexOfDeviceInfo);
            }
            final String _tmpDeviceIp;
            _tmpDeviceIp = _cursor.getString(_cursorIndexOfDeviceIp);
            final Integer _tmpTargetTemp;
            if (_cursor.isNull(_cursorIndexOfTargetTemp)) {
              _tmpTargetTemp = null;
            } else {
              _tmpTargetTemp = _cursor.getInt(_cursorIndexOfTargetTemp);
            }
            final Integer _tmpTempLow;
            if (_cursor.isNull(_cursorIndexOfTempLow)) {
              _tmpTempLow = null;
            } else {
              _tmpTempLow = _cursor.getInt(_cursorIndexOfTempLow);
            }
            final Integer _tmpTempHigh;
            if (_cursor.isNull(_cursorIndexOfTempHigh)) {
              _tmpTempHigh = null;
            } else {
              _tmpTempHigh = _cursor.getInt(_cursorIndexOfTempHigh);
            }
            final Double _tmpMeasuredTemp;
            if (_cursor.isNull(_cursorIndexOfMeasuredTemp)) {
              _tmpMeasuredTemp = null;
            } else {
              _tmpMeasuredTemp = _cursor.getDouble(_cursorIndexOfMeasuredTemp);
            }
            final Double _tmpLeakageMv;
            if (_cursor.isNull(_cursorIndexOfLeakageMv)) {
              _tmpLeakageMv = null;
            } else {
              _tmpLeakageMv = _cursor.getDouble(_cursorIndexOfLeakageMv);
            }
            final String _tmpResult;
            _tmpResult = _cursor.getString(_cursorIndexOfResult);
            _result = new MeasurementRecord(_tmpId,_tmpSnapshotKey,_tmpTimestampMs,_tmpLineName,_tmpModelName,_tmpDeviceInfo,_tmpDeviceIp,_tmpTargetTemp,_tmpTempLow,_tmpTempHigh,_tmpMeasuredTemp,_tmpLeakageMv,_tmpResult);
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
  public Object findByKey(final String key,
      final Continuation<? super MeasurementRecord> $completion) {
    final String _sql = "SELECT * FROM measurement_record WHERE snapshotKey = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MeasurementRecord>() {
      @Override
      @Nullable
      public MeasurementRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSnapshotKey = CursorUtil.getColumnIndexOrThrow(_cursor, "snapshotKey");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfLineName = CursorUtil.getColumnIndexOrThrow(_cursor, "lineName");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfDeviceInfo = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceInfo");
          final int _cursorIndexOfDeviceIp = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceIp");
          final int _cursorIndexOfTargetTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "targetTemp");
          final int _cursorIndexOfTempLow = CursorUtil.getColumnIndexOrThrow(_cursor, "tempLow");
          final int _cursorIndexOfTempHigh = CursorUtil.getColumnIndexOrThrow(_cursor, "tempHigh");
          final int _cursorIndexOfMeasuredTemp = CursorUtil.getColumnIndexOrThrow(_cursor, "measuredTemp");
          final int _cursorIndexOfLeakageMv = CursorUtil.getColumnIndexOrThrow(_cursor, "leakageMv");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final MeasurementRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSnapshotKey;
            _tmpSnapshotKey = _cursor.getString(_cursorIndexOfSnapshotKey);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final String _tmpLineName;
            _tmpLineName = _cursor.getString(_cursorIndexOfLineName);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final String _tmpDeviceInfo;
            if (_cursor.isNull(_cursorIndexOfDeviceInfo)) {
              _tmpDeviceInfo = null;
            } else {
              _tmpDeviceInfo = _cursor.getString(_cursorIndexOfDeviceInfo);
            }
            final String _tmpDeviceIp;
            _tmpDeviceIp = _cursor.getString(_cursorIndexOfDeviceIp);
            final Integer _tmpTargetTemp;
            if (_cursor.isNull(_cursorIndexOfTargetTemp)) {
              _tmpTargetTemp = null;
            } else {
              _tmpTargetTemp = _cursor.getInt(_cursorIndexOfTargetTemp);
            }
            final Integer _tmpTempLow;
            if (_cursor.isNull(_cursorIndexOfTempLow)) {
              _tmpTempLow = null;
            } else {
              _tmpTempLow = _cursor.getInt(_cursorIndexOfTempLow);
            }
            final Integer _tmpTempHigh;
            if (_cursor.isNull(_cursorIndexOfTempHigh)) {
              _tmpTempHigh = null;
            } else {
              _tmpTempHigh = _cursor.getInt(_cursorIndexOfTempHigh);
            }
            final Double _tmpMeasuredTemp;
            if (_cursor.isNull(_cursorIndexOfMeasuredTemp)) {
              _tmpMeasuredTemp = null;
            } else {
              _tmpMeasuredTemp = _cursor.getDouble(_cursorIndexOfMeasuredTemp);
            }
            final Double _tmpLeakageMv;
            if (_cursor.isNull(_cursorIndexOfLeakageMv)) {
              _tmpLeakageMv = null;
            } else {
              _tmpLeakageMv = _cursor.getDouble(_cursorIndexOfLeakageMv);
            }
            final String _tmpResult;
            _tmpResult = _cursor.getString(_cursorIndexOfResult);
            _result = new MeasurementRecord(_tmpId,_tmpSnapshotKey,_tmpTimestampMs,_tmpLineName,_tmpModelName,_tmpDeviceInfo,_tmpDeviceIp,_tmpTargetTemp,_tmpTempLow,_tmpTempHigh,_tmpMeasuredTemp,_tmpLeakageMv,_tmpResult);
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
