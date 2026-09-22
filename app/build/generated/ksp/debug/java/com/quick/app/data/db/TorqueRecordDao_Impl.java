package com.quick.app.data.db;

import android.database.Cursor;
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
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TorqueRecordDao_Impl implements TorqueRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TorqueRecord> __insertionAdapterOfTorqueRecord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByFilter;

  public TorqueRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTorqueRecord = new EntityInsertionAdapter<TorqueRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `torque_record` (`id`,`sessionId`,`timestampMs`,`savedAtMs`,`lineName`,`modelName`,`rangeName`,`rangeMin`,`rangeMax`,`deviceName`,`v1`,`v2`,`v3`,`average`,`judge`,`sampleCount`,`unitText`,`rawText`,`rawHex`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TorqueRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindLong(3, entity.getTimestampMs());
        statement.bindLong(4, entity.getSavedAtMs());
        statement.bindString(5, entity.getLineName());
        statement.bindString(6, entity.getModelName());
        statement.bindString(7, entity.getRangeName());
        if (entity.getRangeMin() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getRangeMin());
        }
        if (entity.getRangeMax() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getRangeMax());
        }
        statement.bindString(10, entity.getDeviceName());
        if (entity.getV1() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getV1());
        }
        if (entity.getV2() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getV2());
        }
        if (entity.getV3() == null) {
          statement.bindNull(13);
        } else {
          statement.bindDouble(13, entity.getV3());
        }
        if (entity.getAverage() == null) {
          statement.bindNull(14);
        } else {
          statement.bindDouble(14, entity.getAverage());
        }
        statement.bindString(15, entity.getJudge());
        statement.bindLong(16, entity.getSampleCount());
        statement.bindString(17, entity.getUnitText());
        statement.bindString(18, entity.getRawText());
        statement.bindString(19, entity.getRawHex());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM torque_record WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByFilter = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        DELETE FROM torque_record\n"
                + "        WHERE (? IS NULL OR timestampMs >= ?)\n"
                + "          AND (? IS NULL OR timestampMs <= ?)\n"
                + "          AND (? IS NULL OR lineName = ?)\n"
                + "          AND (? IS NULL OR modelName = ?)\n"
                + "          AND (? IS NULL OR rangeName = ?)\n"
                + "          AND (? IS NULL OR deviceName = ?)\n"
                + "          AND (? IS NULL OR judge = ?)\n"
                + "          AND (? IS NULL OR lineName LIKE '%' || ? || '%'\n"
                + "                       OR modelName LIKE '%' || ? || '%'\n"
                + "                       OR rangeName LIKE '%' || ? || '%'\n"
                + "                       OR deviceName LIKE '%' || ? || '%'\n"
                + "                       OR rawText LIKE '%' || ? || '%')\n"
                + "        ";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TorqueRecord record, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTorqueRecord.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByFilter(final Long fromMs, final Long toMs, final String line,
      final String model, final String range, final String device, final String judge,
      final String kw, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByFilter.acquire();
        int _argIndex = 1;
        if (fromMs == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, fromMs);
        }
        _argIndex = 2;
        if (fromMs == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, fromMs);
        }
        _argIndex = 3;
        if (toMs == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, toMs);
        }
        _argIndex = 4;
        if (toMs == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, toMs);
        }
        _argIndex = 5;
        if (line == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, line);
        }
        _argIndex = 6;
        if (line == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, line);
        }
        _argIndex = 7;
        if (model == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, model);
        }
        _argIndex = 8;
        if (model == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, model);
        }
        _argIndex = 9;
        if (range == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, range);
        }
        _argIndex = 10;
        if (range == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, range);
        }
        _argIndex = 11;
        if (device == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, device);
        }
        _argIndex = 12;
        if (device == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, device);
        }
        _argIndex = 13;
        if (judge == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, judge);
        }
        _argIndex = 14;
        if (judge == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, judge);
        }
        _argIndex = 15;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
        _argIndex = 16;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
        _argIndex = 17;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
        _argIndex = 18;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
        _argIndex = 19;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
        _argIndex = 20;
        if (kw == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, kw);
        }
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
          __preparedStmtOfDeleteByFilter.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TorqueRecord>> query(final Long fromMs, final Long toMs, final String line,
      final String model, final String range, final String device, final String judge,
      final String kw) {
    final String _sql = "\n"
            + "        SELECT * FROM torque_record\n"
            + "        WHERE (? IS NULL OR timestampMs >= ?)\n"
            + "          AND (? IS NULL OR timestampMs <= ?)\n"
            + "          AND (? IS NULL OR lineName = ?)\n"
            + "          AND (? IS NULL OR modelName = ?)\n"
            + "          AND (? IS NULL OR rangeName = ?)\n"
            + "          AND (? IS NULL OR deviceName = ?)\n"
            + "          AND (? IS NULL OR judge = ?)\n"
            + "          AND (? IS NULL OR lineName LIKE '%' || ? || '%'\n"
            + "                       OR modelName LIKE '%' || ? || '%'\n"
            + "                       OR rangeName LIKE '%' || ? || '%'\n"
            + "                       OR deviceName LIKE '%' || ? || '%'\n"
            + "                       OR rawText LIKE '%' || ? || '%')\n"
            + "        ORDER BY timestampMs DESC, id DESC\n"
            + "        LIMIT 20000\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 20);
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
    if (range == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, range);
    }
    _argIndex = 10;
    if (range == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, range);
    }
    _argIndex = 11;
    if (device == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, device);
    }
    _argIndex = 12;
    if (device == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, device);
    }
    _argIndex = 13;
    if (judge == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, judge);
    }
    _argIndex = 14;
    if (judge == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, judge);
    }
    _argIndex = 15;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 16;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 17;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 18;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 19;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 20;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_record"}, new Callable<List<TorqueRecord>>() {
      @Override
      @NonNull
      public List<TorqueRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfSavedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "savedAtMs");
          final int _cursorIndexOfLineName = CursorUtil.getColumnIndexOrThrow(_cursor, "lineName");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfRangeName = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeName");
          final int _cursorIndexOfRangeMin = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeMin");
          final int _cursorIndexOfRangeMax = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeMax");
          final int _cursorIndexOfDeviceName = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceName");
          final int _cursorIndexOfV1 = CursorUtil.getColumnIndexOrThrow(_cursor, "v1");
          final int _cursorIndexOfV2 = CursorUtil.getColumnIndexOrThrow(_cursor, "v2");
          final int _cursorIndexOfV3 = CursorUtil.getColumnIndexOrThrow(_cursor, "v3");
          final int _cursorIndexOfAverage = CursorUtil.getColumnIndexOrThrow(_cursor, "average");
          final int _cursorIndexOfJudge = CursorUtil.getColumnIndexOrThrow(_cursor, "judge");
          final int _cursorIndexOfSampleCount = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleCount");
          final int _cursorIndexOfUnitText = CursorUtil.getColumnIndexOrThrow(_cursor, "unitText");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawText");
          final int _cursorIndexOfRawHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawHex");
          final List<TorqueRecord> _result = new ArrayList<TorqueRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TorqueRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final long _tmpSavedAtMs;
            _tmpSavedAtMs = _cursor.getLong(_cursorIndexOfSavedAtMs);
            final String _tmpLineName;
            _tmpLineName = _cursor.getString(_cursorIndexOfLineName);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final String _tmpRangeName;
            _tmpRangeName = _cursor.getString(_cursorIndexOfRangeName);
            final Double _tmpRangeMin;
            if (_cursor.isNull(_cursorIndexOfRangeMin)) {
              _tmpRangeMin = null;
            } else {
              _tmpRangeMin = _cursor.getDouble(_cursorIndexOfRangeMin);
            }
            final Double _tmpRangeMax;
            if (_cursor.isNull(_cursorIndexOfRangeMax)) {
              _tmpRangeMax = null;
            } else {
              _tmpRangeMax = _cursor.getDouble(_cursorIndexOfRangeMax);
            }
            final String _tmpDeviceName;
            _tmpDeviceName = _cursor.getString(_cursorIndexOfDeviceName);
            final Double _tmpV1;
            if (_cursor.isNull(_cursorIndexOfV1)) {
              _tmpV1 = null;
            } else {
              _tmpV1 = _cursor.getDouble(_cursorIndexOfV1);
            }
            final Double _tmpV2;
            if (_cursor.isNull(_cursorIndexOfV2)) {
              _tmpV2 = null;
            } else {
              _tmpV2 = _cursor.getDouble(_cursorIndexOfV2);
            }
            final Double _tmpV3;
            if (_cursor.isNull(_cursorIndexOfV3)) {
              _tmpV3 = null;
            } else {
              _tmpV3 = _cursor.getDouble(_cursorIndexOfV3);
            }
            final Double _tmpAverage;
            if (_cursor.isNull(_cursorIndexOfAverage)) {
              _tmpAverage = null;
            } else {
              _tmpAverage = _cursor.getDouble(_cursorIndexOfAverage);
            }
            final String _tmpJudge;
            _tmpJudge = _cursor.getString(_cursorIndexOfJudge);
            final int _tmpSampleCount;
            _tmpSampleCount = _cursor.getInt(_cursorIndexOfSampleCount);
            final String _tmpUnitText;
            _tmpUnitText = _cursor.getString(_cursorIndexOfUnitText);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpRawHex;
            _tmpRawHex = _cursor.getString(_cursorIndexOfRawHex);
            _item = new TorqueRecord(_tmpId,_tmpSessionId,_tmpTimestampMs,_tmpSavedAtMs,_tmpLineName,_tmpModelName,_tmpRangeName,_tmpRangeMin,_tmpRangeMax,_tmpDeviceName,_tmpV1,_tmpV2,_tmpV3,_tmpAverage,_tmpJudge,_tmpSampleCount,_tmpUnitText,_tmpRawText,_tmpRawHex);
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
      final String model, final String range, final String device, final String judge,
      final String kw) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM torque_record\n"
            + "        WHERE (? IS NULL OR timestampMs >= ?)\n"
            + "          AND (? IS NULL OR timestampMs <= ?)\n"
            + "          AND (? IS NULL OR lineName = ?)\n"
            + "          AND (? IS NULL OR modelName = ?)\n"
            + "          AND (? IS NULL OR rangeName = ?)\n"
            + "          AND (? IS NULL OR deviceName = ?)\n"
            + "          AND (? IS NULL OR judge = ?)\n"
            + "          AND (? IS NULL OR lineName LIKE '%' || ? || '%'\n"
            + "                       OR modelName LIKE '%' || ? || '%'\n"
            + "                       OR rangeName LIKE '%' || ? || '%'\n"
            + "                       OR deviceName LIKE '%' || ? || '%'\n"
            + "                       OR rawText LIKE '%' || ? || '%')\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 20);
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
    if (range == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, range);
    }
    _argIndex = 10;
    if (range == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, range);
    }
    _argIndex = 11;
    if (device == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, device);
    }
    _argIndex = 12;
    if (device == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, device);
    }
    _argIndex = 13;
    if (judge == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, judge);
    }
    _argIndex = 14;
    if (judge == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, judge);
    }
    _argIndex = 15;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 16;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 17;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 18;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 19;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    _argIndex = 20;
    if (kw == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, kw);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_record"}, new Callable<Integer>() {
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
  public Flow<TorqueRecord> lastOne() {
    final String _sql = "SELECT * FROM torque_record ORDER BY timestampMs DESC, id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_record"}, new Callable<TorqueRecord>() {
      @Override
      @Nullable
      public TorqueRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfSavedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "savedAtMs");
          final int _cursorIndexOfLineName = CursorUtil.getColumnIndexOrThrow(_cursor, "lineName");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfRangeName = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeName");
          final int _cursorIndexOfRangeMin = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeMin");
          final int _cursorIndexOfRangeMax = CursorUtil.getColumnIndexOrThrow(_cursor, "rangeMax");
          final int _cursorIndexOfDeviceName = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceName");
          final int _cursorIndexOfV1 = CursorUtil.getColumnIndexOrThrow(_cursor, "v1");
          final int _cursorIndexOfV2 = CursorUtil.getColumnIndexOrThrow(_cursor, "v2");
          final int _cursorIndexOfV3 = CursorUtil.getColumnIndexOrThrow(_cursor, "v3");
          final int _cursorIndexOfAverage = CursorUtil.getColumnIndexOrThrow(_cursor, "average");
          final int _cursorIndexOfJudge = CursorUtil.getColumnIndexOrThrow(_cursor, "judge");
          final int _cursorIndexOfSampleCount = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleCount");
          final int _cursorIndexOfUnitText = CursorUtil.getColumnIndexOrThrow(_cursor, "unitText");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawText");
          final int _cursorIndexOfRawHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawHex");
          final TorqueRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final long _tmpSavedAtMs;
            _tmpSavedAtMs = _cursor.getLong(_cursorIndexOfSavedAtMs);
            final String _tmpLineName;
            _tmpLineName = _cursor.getString(_cursorIndexOfLineName);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final String _tmpRangeName;
            _tmpRangeName = _cursor.getString(_cursorIndexOfRangeName);
            final Double _tmpRangeMin;
            if (_cursor.isNull(_cursorIndexOfRangeMin)) {
              _tmpRangeMin = null;
            } else {
              _tmpRangeMin = _cursor.getDouble(_cursorIndexOfRangeMin);
            }
            final Double _tmpRangeMax;
            if (_cursor.isNull(_cursorIndexOfRangeMax)) {
              _tmpRangeMax = null;
            } else {
              _tmpRangeMax = _cursor.getDouble(_cursorIndexOfRangeMax);
            }
            final String _tmpDeviceName;
            _tmpDeviceName = _cursor.getString(_cursorIndexOfDeviceName);
            final Double _tmpV1;
            if (_cursor.isNull(_cursorIndexOfV1)) {
              _tmpV1 = null;
            } else {
              _tmpV1 = _cursor.getDouble(_cursorIndexOfV1);
            }
            final Double _tmpV2;
            if (_cursor.isNull(_cursorIndexOfV2)) {
              _tmpV2 = null;
            } else {
              _tmpV2 = _cursor.getDouble(_cursorIndexOfV2);
            }
            final Double _tmpV3;
            if (_cursor.isNull(_cursorIndexOfV3)) {
              _tmpV3 = null;
            } else {
              _tmpV3 = _cursor.getDouble(_cursorIndexOfV3);
            }
            final Double _tmpAverage;
            if (_cursor.isNull(_cursorIndexOfAverage)) {
              _tmpAverage = null;
            } else {
              _tmpAverage = _cursor.getDouble(_cursorIndexOfAverage);
            }
            final String _tmpJudge;
            _tmpJudge = _cursor.getString(_cursorIndexOfJudge);
            final int _tmpSampleCount;
            _tmpSampleCount = _cursor.getInt(_cursorIndexOfSampleCount);
            final String _tmpUnitText;
            _tmpUnitText = _cursor.getString(_cursorIndexOfUnitText);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpRawHex;
            _tmpRawHex = _cursor.getString(_cursorIndexOfRawHex);
            _result = new TorqueRecord(_tmpId,_tmpSessionId,_tmpTimestampMs,_tmpSavedAtMs,_tmpLineName,_tmpModelName,_tmpRangeName,_tmpRangeMin,_tmpRangeMax,_tmpDeviceName,_tmpV1,_tmpV2,_tmpV3,_tmpAverage,_tmpJudge,_tmpSampleCount,_tmpUnitText,_tmpRawText,_tmpRawHex);
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
  public Flow<Integer> total() {
    final String _sql = "SELECT COUNT(*) FROM torque_record";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"torque_record"}, new Callable<Integer>() {
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
