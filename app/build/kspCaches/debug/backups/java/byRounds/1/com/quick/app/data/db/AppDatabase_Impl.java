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

  private volatile StationDao _stationDao;

  private volatile TempPresetDao _tempPresetDao;

  private volatile LimitPresetDao _limitPresetDao;

  private volatile DeviceSnDao _deviceSnDao;

  private volatile ConfigDao _configDao;

  private volatile SettingDao _settingDao;

  private volatile RecordDao _recordDao;

  private volatile TorqueDictDao _torqueDictDao;

  private volatile TorqueRecordDao _torqueRecordDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(9) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `line` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_line_name` ON `line` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `model` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_model_name` ON `model` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `station` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_station_name` ON `station` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `temp_preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `setTemp` INTEGER NOT NULL, `tolerance` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_temp_preset_name` ON `temp_preset` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `limit_preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `valueRaw` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_limit_preset_kind_name` ON `limit_preset` (`kind`, `name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `device_sn` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `source` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_sn_name` ON `device_sn` (`name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `device_config` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `ip` TEXT NOT NULL, `port` INTEGER NOT NULL, `unitId` INTEGER NOT NULL, `pollIntervalMs` INTEGER NOT NULL, `timeoutMs` INTEGER NOT NULL, `autoStart` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `snapshotKey` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `lineName` TEXT NOT NULL, `modelName` TEXT NOT NULL, `stationName` TEXT NOT NULL DEFAULT '', `deviceInfo` TEXT, `deviceIp` TEXT NOT NULL, `channel` INTEGER, `targetTemp` INTEGER, `tempLow` INTEGER, `tempHigh` INTEGER, `voltageLimitMv` REAL, `resistanceLimitOhm` REAL, `measuredTemp` REAL, `measuredVoltageMv` REAL, `measuredResistanceOhm` REAL, `result` TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_record_snapshotKey` ON `measurement_record` (`snapshotKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_record_timestampMs` ON `measurement_record` (`timestampMs`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `torque_dict` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `minValue` REAL, `maxValue` REAL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_torque_dict_kind_name` ON `torque_dict` (`kind`, `name`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `torque_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `savedAtMs` INTEGER NOT NULL, `lineName` TEXT NOT NULL, `modelName` TEXT NOT NULL, `rangeName` TEXT NOT NULL, `rangeMin` REAL, `rangeMax` REAL, `deviceName` TEXT NOT NULL, `v1` REAL, `v2` REAL, `v3` REAL, `average` REAL, `judge` TEXT NOT NULL, `sampleCount` INTEGER NOT NULL, `unitText` TEXT NOT NULL, `rawText` TEXT NOT NULL, `rawHex` TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_torque_record_sessionId` ON `torque_record` (`sessionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_torque_record_timestampMs` ON `torque_record` (`timestampMs`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ab53cf5d9a0efe8c7ec8e8f6a055ec0b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `line`");
        db.execSQL("DROP TABLE IF EXISTS `model`");
        db.execSQL("DROP TABLE IF EXISTS `station`");
        db.execSQL("DROP TABLE IF EXISTS `temp_preset`");
        db.execSQL("DROP TABLE IF EXISTS `limit_preset`");
        db.execSQL("DROP TABLE IF EXISTS `device_sn`");
        db.execSQL("DROP TABLE IF EXISTS `device_config`");
        db.execSQL("DROP TABLE IF EXISTS `measurement_record`");
        db.execSQL("DROP TABLE IF EXISTS `app_setting`");
        db.execSQL("DROP TABLE IF EXISTS `torque_dict`");
        db.execSQL("DROP TABLE IF EXISTS `torque_record`");
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
        final HashMap<String, TableInfo.Column> _columnsStation = new HashMap<String, TableInfo.Column>(5);
        _columnsStation.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStation.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStation.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStation.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStation.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStation = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStation = new HashSet<TableInfo.Index>(1);
        _indicesStation.add(new TableInfo.Index("index_station_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        final TableInfo _infoStation = new TableInfo("station", _columnsStation, _foreignKeysStation, _indicesStation);
        final TableInfo _existingStation = TableInfo.read(db, "station");
        if (!_infoStation.equals(_existingStation)) {
          return new RoomOpenHelper.ValidationResult(false, "station(com.quick.app.data.db.Station).\n"
                  + " Expected:\n" + _infoStation + "\n"
                  + " Found:\n" + _existingStation);
        }
        final HashMap<String, TableInfo.Column> _columnsTempPreset = new HashMap<String, TableInfo.Column>(7);
        _columnsTempPreset.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("setTemp", new TableInfo.Column("setTemp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("tolerance", new TableInfo.Column("tolerance", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTempPreset.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTempPreset = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTempPreset = new HashSet<TableInfo.Index>(1);
        _indicesTempPreset.add(new TableInfo.Index("index_temp_preset_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        final TableInfo _infoTempPreset = new TableInfo("temp_preset", _columnsTempPreset, _foreignKeysTempPreset, _indicesTempPreset);
        final TableInfo _existingTempPreset = TableInfo.read(db, "temp_preset");
        if (!_infoTempPreset.equals(_existingTempPreset)) {
          return new RoomOpenHelper.ValidationResult(false, "temp_preset(com.quick.app.data.db.TempPreset).\n"
                  + " Expected:\n" + _infoTempPreset + "\n"
                  + " Found:\n" + _existingTempPreset);
        }
        final HashMap<String, TableInfo.Column> _columnsLimitPreset = new HashMap<String, TableInfo.Column>(7);
        _columnsLimitPreset.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("valueRaw", new TableInfo.Column("valueRaw", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLimitPreset.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLimitPreset = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLimitPreset = new HashSet<TableInfo.Index>(1);
        _indicesLimitPreset.add(new TableInfo.Index("index_limit_preset_kind_name", true, Arrays.asList("kind", "name"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoLimitPreset = new TableInfo("limit_preset", _columnsLimitPreset, _foreignKeysLimitPreset, _indicesLimitPreset);
        final TableInfo _existingLimitPreset = TableInfo.read(db, "limit_preset");
        if (!_infoLimitPreset.equals(_existingLimitPreset)) {
          return new RoomOpenHelper.ValidationResult(false, "limit_preset(com.quick.app.data.db.LimitPreset).\n"
                  + " Expected:\n" + _infoLimitPreset + "\n"
                  + " Found:\n" + _existingLimitPreset);
        }
        final HashMap<String, TableInfo.Column> _columnsDeviceSn = new HashMap<String, TableInfo.Column>(6);
        _columnsDeviceSn.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceSn.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceSn.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceSn.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceSn.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceSn.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDeviceSn = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDeviceSn = new HashSet<TableInfo.Index>(1);
        _indicesDeviceSn.add(new TableInfo.Index("index_device_sn_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        final TableInfo _infoDeviceSn = new TableInfo("device_sn", _columnsDeviceSn, _foreignKeysDeviceSn, _indicesDeviceSn);
        final TableInfo _existingDeviceSn = TableInfo.read(db, "device_sn");
        if (!_infoDeviceSn.equals(_existingDeviceSn)) {
          return new RoomOpenHelper.ValidationResult(false, "device_sn(com.quick.app.data.db.DeviceSn).\n"
                  + " Expected:\n" + _infoDeviceSn + "\n"
                  + " Found:\n" + _existingDeviceSn);
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
        final HashMap<String, TableInfo.Column> _columnsMeasurementRecord = new HashMap<String, TableInfo.Column>(18);
        _columnsMeasurementRecord.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("snapshotKey", new TableInfo.Column("snapshotKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("timestampMs", new TableInfo.Column("timestampMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("lineName", new TableInfo.Column("lineName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("modelName", new TableInfo.Column("modelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("stationName", new TableInfo.Column("stationName", "TEXT", true, 0, "''", TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("deviceInfo", new TableInfo.Column("deviceInfo", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("deviceIp", new TableInfo.Column("deviceIp", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("channel", new TableInfo.Column("channel", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("targetTemp", new TableInfo.Column("targetTemp", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("tempLow", new TableInfo.Column("tempLow", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("tempHigh", new TableInfo.Column("tempHigh", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("voltageLimitMv", new TableInfo.Column("voltageLimitMv", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("resistanceLimitOhm", new TableInfo.Column("resistanceLimitOhm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("measuredTemp", new TableInfo.Column("measuredTemp", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("measuredVoltageMv", new TableInfo.Column("measuredVoltageMv", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementRecord.put("measuredResistanceOhm", new TableInfo.Column("measuredResistanceOhm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
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
        final HashMap<String, TableInfo.Column> _columnsTorqueDict = new HashMap<String, TableInfo.Column>(8);
        _columnsTorqueDict.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("minValue", new TableInfo.Column("minValue", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueDict.put("maxValue", new TableInfo.Column("maxValue", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTorqueDict = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTorqueDict = new HashSet<TableInfo.Index>(1);
        _indicesTorqueDict.add(new TableInfo.Index("index_torque_dict_kind_name", true, Arrays.asList("kind", "name"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoTorqueDict = new TableInfo("torque_dict", _columnsTorqueDict, _foreignKeysTorqueDict, _indicesTorqueDict);
        final TableInfo _existingTorqueDict = TableInfo.read(db, "torque_dict");
        if (!_infoTorqueDict.equals(_existingTorqueDict)) {
          return new RoomOpenHelper.ValidationResult(false, "torque_dict(com.quick.app.data.db.TorqueDict).\n"
                  + " Expected:\n" + _infoTorqueDict + "\n"
                  + " Found:\n" + _existingTorqueDict);
        }
        final HashMap<String, TableInfo.Column> _columnsTorqueRecord = new HashMap<String, TableInfo.Column>(19);
        _columnsTorqueRecord.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("timestampMs", new TableInfo.Column("timestampMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("savedAtMs", new TableInfo.Column("savedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("lineName", new TableInfo.Column("lineName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("modelName", new TableInfo.Column("modelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("rangeName", new TableInfo.Column("rangeName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("rangeMin", new TableInfo.Column("rangeMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("rangeMax", new TableInfo.Column("rangeMax", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("deviceName", new TableInfo.Column("deviceName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("v1", new TableInfo.Column("v1", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("v2", new TableInfo.Column("v2", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("v3", new TableInfo.Column("v3", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("average", new TableInfo.Column("average", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("judge", new TableInfo.Column("judge", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("sampleCount", new TableInfo.Column("sampleCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("unitText", new TableInfo.Column("unitText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("rawText", new TableInfo.Column("rawText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTorqueRecord.put("rawHex", new TableInfo.Column("rawHex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTorqueRecord = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTorqueRecord = new HashSet<TableInfo.Index>(2);
        _indicesTorqueRecord.add(new TableInfo.Index("index_torque_record_sessionId", true, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        _indicesTorqueRecord.add(new TableInfo.Index("index_torque_record_timestampMs", false, Arrays.asList("timestampMs"), Arrays.asList("ASC")));
        final TableInfo _infoTorqueRecord = new TableInfo("torque_record", _columnsTorqueRecord, _foreignKeysTorqueRecord, _indicesTorqueRecord);
        final TableInfo _existingTorqueRecord = TableInfo.read(db, "torque_record");
        if (!_infoTorqueRecord.equals(_existingTorqueRecord)) {
          return new RoomOpenHelper.ValidationResult(false, "torque_record(com.quick.app.data.db.TorqueRecord).\n"
                  + " Expected:\n" + _infoTorqueRecord + "\n"
                  + " Found:\n" + _existingTorqueRecord);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "ab53cf5d9a0efe8c7ec8e8f6a055ec0b", "01e755a4e56a81f01e6ae2e0ed105ce3");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "line","model","station","temp_preset","limit_preset","device_sn","device_config","measurement_record","app_setting","torque_dict","torque_record");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `line`");
      _db.execSQL("DELETE FROM `model`");
      _db.execSQL("DELETE FROM `station`");
      _db.execSQL("DELETE FROM `temp_preset`");
      _db.execSQL("DELETE FROM `limit_preset`");
      _db.execSQL("DELETE FROM `device_sn`");
      _db.execSQL("DELETE FROM `device_config`");
      _db.execSQL("DELETE FROM `measurement_record`");
      _db.execSQL("DELETE FROM `app_setting`");
      _db.execSQL("DELETE FROM `torque_dict`");
      _db.execSQL("DELETE FROM `torque_record`");
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
    _typeConvertersMap.put(StationDao.class, StationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TempPresetDao.class, TempPresetDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LimitPresetDao.class, LimitPresetDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DeviceSnDao.class, DeviceSnDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConfigDao.class, ConfigDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingDao.class, SettingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RecordDao.class, RecordDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TorqueDictDao.class, TorqueDictDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TorqueRecordDao.class, TorqueRecordDao_Impl.getRequiredConverters());
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
  public StationDao stationDao() {
    if (_stationDao != null) {
      return _stationDao;
    } else {
      synchronized(this) {
        if(_stationDao == null) {
          _stationDao = new StationDao_Impl(this);
        }
        return _stationDao;
      }
    }
  }

  @Override
  public TempPresetDao tempPresetDao() {
    if (_tempPresetDao != null) {
      return _tempPresetDao;
    } else {
      synchronized(this) {
        if(_tempPresetDao == null) {
          _tempPresetDao = new TempPresetDao_Impl(this);
        }
        return _tempPresetDao;
      }
    }
  }

  @Override
  public LimitPresetDao limitPresetDao() {
    if (_limitPresetDao != null) {
      return _limitPresetDao;
    } else {
      synchronized(this) {
        if(_limitPresetDao == null) {
          _limitPresetDao = new LimitPresetDao_Impl(this);
        }
        return _limitPresetDao;
      }
    }
  }

  @Override
  public DeviceSnDao deviceSnDao() {
    if (_deviceSnDao != null) {
      return _deviceSnDao;
    } else {
      synchronized(this) {
        if(_deviceSnDao == null) {
          _deviceSnDao = new DeviceSnDao_Impl(this);
        }
        return _deviceSnDao;
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

  @Override
  public TorqueDictDao torqueDictDao() {
    if (_torqueDictDao != null) {
      return _torqueDictDao;
    } else {
      synchronized(this) {
        if(_torqueDictDao == null) {
          _torqueDictDao = new TorqueDictDao_Impl(this);
        }
        return _torqueDictDao;
      }
    }
  }

  @Override
  public TorqueRecordDao torqueRecordDao() {
    if (_torqueRecordDao != null) {
      return _torqueRecordDao;
    } else {
      synchronized(this) {
        if(_torqueRecordDao == null) {
          _torqueRecordDao = new TorqueRecordDao_Impl(this);
        }
        return _torqueRecordDao;
      }
    }
  }
}
