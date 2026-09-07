package com.quick.app.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile LineDao _lineDao;

  private volatile ModelDao _modelDao;

  private volatile ConfigDao _configDao;

  private volatile SettingDao _settingDao;

  private volatile RecordDao _recordDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `line` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_line_name` ON `line` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `model` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_model_name` ON `model` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `device_config` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `ip` TEXT NOT NULL, `port` INTEGER NOT NULL, `unitId` INTEGER NOT NULL, `pollIntervalMs` INTEGER NOT NULL, `timeoutMs` INTEGER NOT NULL, `autoStart` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `snapshotKey` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `lineName` TEXT NOT NULL, `modelName` TEXT NOT NULL, `deviceSn` TEXT, `deviceIp` TEXT NOT NULL, `setTemp` INTEGER, `measuredTemp` INTEGER, `tolerance` INTEGER, `result` TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_record_snapshotKey` ON `measurement_record` (`snapshotKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_record_timestampMs` ON `measurement_record` (`timestampMs`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6cdc2b069331c041a7301daf5aa22c32')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `line`");
        db.execSQL("DROP TABLE IF EXISTS `model`");
        db.execSQL("DROP TABLE IF EXISTS `device_config`");
        db.execSQL("DROP TABLE IF EXISTS `measurement_record`");
        db.execSQL("DROP TABLE IF EXISTS `app_setting`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsLine = new HashMap<String, TableInfo.Column>(5);
        _columnsLine.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLine.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLine.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLine.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLine.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLine = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLine = new HashSet<TableInfo.Index>(1);
        _indicesLine.add(new TableInfo.Index("index_line_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        final TableInfo _infoLine = new TableInfo("line", _columnsLine, _foreignKeysLine, _indicesLine);
        final TableInfo _existingLine = TableInfo.read(db, "line");
        if (!_infoLine.equals(_existingLine)) {
          return new RoomOpenHelper.ValidationResult(false, "line(com.quick.app.data.db.Line).\n"
                  + " Expected:\n" + _infoLine + "\n"
                  + " Found:\n" + _existingLine);
        }
        final HashMap<String, TableInfo.Column> _columnsModel = new HashMap<String, TableInfo.Column>(5);
        _columnsModel.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsModel.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsModel.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsModel.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsModel.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysModel = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesModel = new HashSet<TableInfo.Index>(1);
        _indicesModel.add(new TableInfo.Index("index_model_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        final TableInfo _infoModel = new TableInfo("model", _columnsModel, _foreignKeysModel, _indicesModel);
        final TableInfo _existingModel = TableInfo.read(db, "model");
        if (!_infoModel.equals(_existingModel)) {
          return new RoomOpenHelper.ValidationResult(false, "model(com.quick.app.data.db.Model).\n"
                  + " Expected:\n" + _infoModel + "\n"
                  + " Found:\n" + _existingModel);
        }
        final HashMap<String, TableInfo.Column> _columnsDeviceConfig = new HashMap<String, TableInfo.Column>(8);
        _columnsDeviceConfig.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("ip", new TableInfo.Column("ip", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("port", new TableInfo.Column("port", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("unitId", new TableInfo.Column("unitId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("pollIntervalMs", new TableInfo.Column("pollIntervalMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("timeoutMs", new TableInfo.Column("timeoutMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceConfig.put("autoStart", new TableInfo.Column("autoStart", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDeviceConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDeviceConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDeviceConfig = new TableInfo("device_config", _columnsDeviceConfig, _foreignKeysDeviceConfig, _indicesDeviceConfig);
        final TableInfo _existingDeviceConfig = TableInfo.read(db, "device_config");
        if (!_infoDeviceConfig.equals(_existingDeviceConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "device_config(com.quick.app.data.db.DeviceConfig).\n"
                  + " Expected:\n" + _infoDeviceConfig + "\n"
                  + " Found:\n" + _existingDeviceConfig);
        }
        final HashMap<String, TableInfo.Column> _columnsMeasurementRecord = new HashMap<String, TableInfo.Column>(11);
        _columnsMeasurementRecord.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("snapshotKey", new TableInfo.Column("snapshotKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("timestampMs", new TableInfo.Column("timestampMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("lineName", new TableInfo.Column("lineName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("modelName", new TableInfo.Column("modelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("deviceSn", new TableInfo.Column("deviceSn", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("deviceIp", new TableInfo.Column("deviceIp", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("setTemp", new TableInfo.Column("setTemp", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("measuredTemp", new TableInfo.Column("measuredTemp", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("tolerance", new TableInfo.Column("tolerance", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("result", new TableInfo.Column("result", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeasurementRecord = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMeasurementRecord = new HashSet<TableInfo.Index>(2);
        _indicesMeasurementRecord.add(new TableInfo.Index("index_measurement_record_snapshotKey", true, Arrays.asList("snapshotKey"), Arrays.asList("ASC")));
        _indicesMeasurementRecord.add(new TableInfo.Index("index_measurement_record_timestampMs", false, Arrays.asList("timestampMs"), Arrays.asList("ASC")));
        final TableInfo _infoMeasurementRecord = new TableInfo("measurement_record", _columnsMeasurementRecord, _foreignKeysMeasurementRecord, _indicesMeasurementRecord);
        final TableInfo _existingMeasurementRecord = TableInfo.read(db, "measurement_record");
        if (!_infoMeasurementRecord.equals(_existingMeasurementRecord)) {
          return new RoomOpenHelper.ValidationResult(false, "measurement_record(com.quick.app.data.db.MeasurementRecord).\n"
                  + " Expected:\n" + _infoMeasurementRecord + "\n"
                  + " Found:\n" + _existingMeasurementRecord);
        }
        final HashMap<String, TableInfo.Column> _columnsAppSetting = new HashMap<String, TableInfo.Column>(2);
        _columnsAppSetting.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAppSetting.put("value", new TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAppSetting = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAppSetting = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAppSetting = new TableInfo("app_setting", _columnsAppSetting, _foreignKeysAppSetting, _indicesAppSetting);
        final TableInfo _existingAppSetting = TableInfo.read(db, "app_setting");
        if (!_infoAppSetting.equals(_existingAppSetting)) {
          return new RoomOpenHelper.ValidationResult(false, "app_setting(com.quick.app.data.db.AppSetting).\n"
                  + " Expected:\n" + _infoAppSetting + "\n"
                  + " Found:\n" + _existingAppSetting);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "6cdc2b069331c041a7301daf5aa22c32", "e515feff61576a66f752d95213cf9ff1");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "line","model","device_config","measurement_record","app_setting");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `line`");
      _db.execSQL("DELETE FROM `model`");
      _db.execSQL("DELETE FROM `device_config`");
      _db.execSQL("DELETE FROM `measurement_record`");
      _db.execSQL("DELETE FROM `app_setting`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(LineDao.class, LineDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ModelDao.class, ModelDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConfigDao.class, ConfigDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingDao.class, SettingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RecordDao.class, RecordDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public LineDao lineDao() {
    if (_lineDao != null) {
      return _lineDao;
    } else {
      synchronized(this) {
        if(_lineDao == null) {
          _lineDao = new LineDao_Impl(this);
        }
        return _lineDao;
      }
    }
  }

  @Override
  public ModelDao modelDao() {
    if (_modelDao != null) {
      return _modelDao;
    } else {
      synchronized(this) {
        if(_modelDao == null) {
          _modelDao = new ModelDao_Impl(this);
        }
        return _modelDao;
      }
    }
  }

  @Override
  public ConfigDao configDao() {
    if (_configDao != null) {
      return _configDao;
    } else {
      synchronized(this) {
        if(_configDao == null) {
          _configDao = new ConfigDao_Impl(this);
        }
        return _configDao;
      }
    }
  }

  @Override
  public SettingDao settingDao() {
    if (_settingDao != null) {
      return _settingDao;
    } else {
      synchronized(this) {
        if(_settingDao == null) {
          _settingDao = new SettingDao_Impl(this);
        }
        return _settingDao;
      }
    }
  }

  @Override
  public RecordDao recordDao() {
    if (_recordDao != null) {
      return _recordDao;
    } else {
      synchronized(this) {
        if(_recordDao == null) {
          _recordDao = new RecordDao_Impl(this);
        }
        return _recordDao;
      }
    }
  }
}
