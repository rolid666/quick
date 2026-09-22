package com.quick.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Line::class, Model::class, Station::class, TempPreset::class, LimitPreset::class,
        DeviceSn::class, DeviceConfig::class, MeasurementRecord::class, AppSetting::class,
        // 扭力计（2026-09-21）：字典 + 记录各一张，与上面烙铁的表互不共用
        TorqueDict::class, TorqueRecord::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lineDao(): LineDao
    abstract fun modelDao(): ModelDao
    abstract fun stationDao(): StationDao
    abstract fun tempPresetDao(): TempPresetDao
    abstract fun limitPresetDao(): LimitPresetDao
    abstract fun deviceSnDao(): DeviceSnDao
    abstract fun configDao(): ConfigDao
    abstract fun settingDao(): SettingDao
    abstract fun recordDao(): RecordDao
    abstract fun torqueDictDao(): TorqueDictDao
    abstract fun torqueRecordDao(): TorqueRecordDao

    companion object {
        /**
         * v1→v2：line/model 的 name 加唯一约束。
         * v1 无约束时同名重复入库会导致管理页列表（name 作 key）渲染崩溃，
         * 迁移先把历史重复行去重（每组保留最早一条 id），再建唯一索引。
         * 索引名必须与 Room 默认规则一致：index_<表名>_<列名>。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM line WHERE id NOT IN (SELECT MIN(id) FROM line GROUP BY name)")
                db.execSQL("DELETE FROM model WHERE id NOT IN (SELECT MIN(id) FROM model GROUP BY name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_line_name` ON `line` (`name`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_model_name` ON `model` (`name`)")
            }
        }

        /**
         * v2→v3：measurement_record 按实机实测寄存器语义重构
         * （deviceSn→deviceInfo、setTemp→targetTemp、measuredTemp INT→REAL 支持 0.1℃、
         * 删 tolerance、加 tempLow/tempHigh/leakageMv）。
         * SQLite 不能改列名/类型 → 重建表并拷贝旧数据（旧 tolerance 语义已错，丢弃）。
         * 同时把旧默认轮询 1000ms 提密到 500ms（0x1E 保持时长未知，抓取窗口取小）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `measurement_record_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `snapshotKey` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `lineName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `deviceInfo` TEXT,
                        `deviceIp` TEXT NOT NULL,
                        `targetTemp` INTEGER,
                        `tempLow` INTEGER,
                        `tempHigh` INTEGER,
                        `measuredTemp` REAL,
                        `leakageMv` REAL,
                        `result` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `measurement_record_new`
                        (id, snapshotKey, timestampMs, lineName, modelName, deviceInfo, deviceIp,
                         targetTemp, tempLow, tempHigh, measuredTemp, leakageMv, result)
                    SELECT id, snapshotKey, timestampMs, lineName, modelName, deviceSn, deviceIp,
                           setTemp, NULL, NULL, measuredTemp, NULL, result
                    FROM measurement_record
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `measurement_record`")
                db.execSQL("ALTER TABLE `measurement_record_new` RENAME TO `measurement_record`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_record_snapshotKey` ON `measurement_record` (`snapshotKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_record_timestampMs` ON `measurement_record` (`timestampMs`)")
                db.execSQL("UPDATE `device_config` SET `pollIntervalMs` = 500 WHERE `pollIntervalMs` = 1000")
            }
        }

        /**
         * v3→v4：measurement_record 按厂方手册地址分配表（piture/file.webp）重构。
         * 新增：channel(0x04)、voltageLimitMv(0x1D)、resistanceLimitOhm(0x1E)、
         *       measuredVoltageMv(0x21)、measuredResistanceOhm(0x22)；
         * 删除：leakageMv —— 旧的 0x02 实时电压被 0x21 测试保存电压取代，
         *       旧值（触发瞬间的实时电压）迁到 measuredVoltageMv 保留，不丢历史；
         * measuredTemp 语义由「0x00 实时温度」变为「0x20 仪器定格保存温度」（数值原样保留）。
         * SQLite 不能改列集 → 重建表。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `measurement_record_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `snapshotKey` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `lineName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `deviceInfo` TEXT,
                        `deviceIp` TEXT NOT NULL,
                        `channel` INTEGER,
                        `targetTemp` INTEGER,
                        `tempLow` INTEGER,
                        `tempHigh` INTEGER,
                        `voltageLimitMv` REAL,
                        `resistanceLimitOhm` REAL,
                        `measuredTemp` REAL,
                        `measuredVoltageMv` REAL,
                        `measuredResistanceOhm` REAL,
                        `result` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `measurement_record_new`
                        (id, snapshotKey, timestampMs, lineName, modelName, deviceInfo, deviceIp,
                         channel, targetTemp, tempLow, tempHigh, voltageLimitMv, resistanceLimitOhm,
                         measuredTemp, measuredVoltageMv, measuredResistanceOhm, result)
                    SELECT id, snapshotKey, timestampMs, lineName, modelName, deviceInfo, deviceIp,
                           NULL, targetTemp, tempLow, tempHigh, NULL, NULL,
                           measuredTemp, leakageMv, NULL, result
                    FROM measurement_record
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `measurement_record`")
                db.execSQL("ALTER TABLE `measurement_record_new` RENAME TO `measurement_record`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_record_snapshotKey` ON `measurement_record` (`snapshotKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_record_timestampMs` ON `measurement_record` (`timestampMs`)")
            }
        }

        /**
         * v4→v5：新增「站别」字典（与线别/机种平级的独立表），measurement_record 增列 stationName。
         * 加列用 ALTER TABLE ADD COLUMN（不动已有数据），旧记录站别为空串。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_station_name` ON `station` (`name`)")
                db.execSQL("ALTER TABLE `measurement_record` ADD COLUMN `stationName` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v5→v6：新增「温度设置」与「设备编号」两个字典（2026-09 需求）。
         * 纯新增两张表，不动 measurement_record —— 设备编号本来就存在 deviceInfo 列里
         * （仪器 0x0A~0x19 与手选编号同列，取用时二选一，见 MeasurementController.storeResult）。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `temp_preset` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `setTemp` INTEGER NOT NULL,
                        `tolerance` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_temp_preset_name` ON `temp_preset` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `device_sn` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_sn_name` ON `device_sn` (`name`)")
            }
        }

        /**
         * v6→v7：新增「上限设置」字典（漏电压上限 VMAX / 接地电阻上限 RMAX，2026-09 需求）。
         * 纯新增一张表，不动 measurement_record —— 上限值本来就随每笔结果快照在
         * voltageLimitOhm/resistanceLimitOhm 列里，历史不受影响。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `limit_preset` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `valueRaw` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_limit_preset_kind_name` " +
                        "ON `limit_preset` (`kind`, `name`)"
                )
            }
        }

        /**
         * v7→v8：新增扭力计两张表（2026-09-21 需求：主界面分烙铁/扭力计两块）。
         *
         * **纯新增，一个字段都不动烙铁的表** —— 这是「添加新界面不能影响原本烙铁功能」
         * 在数据库层的具体保证：老数据一个字节不变，回退版本也不会因为多两张表读不出来。
         *
         * 两张表的建表语句必须与 Room 由数据类生成的语句**逐字一致**
         * （列顺序、NOT NULL、索引名 `index_<表>_<列>`），否则全新安装与升级安装两套 schema
         * 不一致，Room 的 schema 校验会在下次启动时报错。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `torque_dict` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_torque_dict_kind_name` " +
                        "ON `torque_dict` (`kind`, `name`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `torque_record` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `snapshotKey` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `lineName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `rangeName` TEXT NOT NULL,
                        `deviceName` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `seqInSession` INTEGER NOT NULL,
                        `isAverage` INTEGER NOT NULL,
                        `measuredValue` REAL,
                        `sampleCount` INTEGER NOT NULL,
                        `unitText` TEXT NOT NULL,
                        `judgeText` TEXT NOT NULL,
                        `rawLine` TEXT NOT NULL,
                        `rawHex` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_torque_record_snapshotKey` " +
                        "ON `torque_record` (`snapshotKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_torque_record_timestampMs` " +
                        "ON `torque_record` (`timestampMs`)"
                )
            }
        }

        /**
         * v8→v9：扭力计按实测格式定稿（2026-09-22 用户反馈）。
         *
         * 两处改动，**仍然一个字段都不动烙铁的表**：
         * 1. `torque_dict` **加两列**（`minValue` / `maxValue`，扭矩上下限，kgf*cm）——
         *    纯 ALTER ADD COLUMN，老字典一条不丢（线别/机种/设备信息的这两列留空）；
         * 2. `torque_record` **改成「一组一行」**：三笔结果变成同一行的 v1/v2/v3 三列，
         *    加上平均、判定、上下限快照（用户要求：「三次扭矩测量结果 / 平均扭矩 / 判断结果」）。
         *    旧结构是「一笔一行 + 一行平均」，SQLite 改不了列，所以**重建表并聚合迁移**：
         *    按 sessionId 分组，把 seq1/2/3 的值还原到三列、平均行还原到 average。
         *    判据（范围上下限）旧数据里没有 —— 留 NULL 且 judge 留空（**不编造判定**），
         *    界面上显示「未判定」。
         *
         * 建表语句必须与 Room 由数据类生成的语句**逐字一致**，否则升级安装与全新安装
         * 两套 schema 不一致，Room 校验会在下次启动时报错。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) 扭矩范围的两个数（可空：只有 TQ_RANGE 有值）
                db.execSQL("ALTER TABLE `torque_dict` ADD COLUMN `minValue` REAL")
                db.execSQL("ALTER TABLE `torque_dict` ADD COLUMN `maxValue` REAL")

                // 2) torque_record：一笔一行 → 一组一行（重建 + 聚合）
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `torque_record_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `savedAtMs` INTEGER NOT NULL,
                        `lineName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `rangeName` TEXT NOT NULL,
                        `rangeMin` REAL,
                        `rangeMax` REAL,
                        `deviceName` TEXT NOT NULL,
                        `v1` REAL,
                        `v2` REAL,
                        `v3` REAL,
                        `average` REAL,
                        `judge` TEXT NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `unitText` TEXT NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `rawHex` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `torque_record_new`
                        (`sessionId`, `timestampMs`, `savedAtMs`, `lineName`, `modelName`, `rangeName`,
                         `rangeMin`, `rangeMax`, `deviceName`, `v1`, `v2`, `v3`, `average`, `judge`,
                         `sampleCount`, `unitText`, `rawText`, `rawHex`)
                    SELECT `sessionId`,
                           MAX(`timestampMs`), MAX(`timestampMs`),
                           MAX(`lineName`), MAX(`modelName`), MAX(`rangeName`),
                           NULL, NULL, MAX(`deviceName`),
                           MAX(CASE WHEN `seqInSession` = 1 THEN `measuredValue` END),
                           MAX(CASE WHEN `seqInSession` = 2 THEN `measuredValue` END),
                           MAX(CASE WHEN `seqInSession` = 3 THEN `measuredValue` END),
                           MAX(CASE WHEN `isAverage` = 1 THEN `measuredValue` END),
                           '',
                           MAX(CASE WHEN `isAverage` = 1 THEN `sampleCount` ELSE 1 END),
                           MAX(`unitText`), MAX(`rawLine`), MAX(`rawHex`)
                    FROM `torque_record`
                    GROUP BY `sessionId`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `torque_record`")
                db.execSQL("ALTER TABLE `torque_record_new` RENAME TO `torque_record`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_torque_record_sessionId` " +
                        "ON `torque_record` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_torque_record_timestampMs` " +
                        "ON `torque_record` (`timestampMs`)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "measure.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                .fallbackToDestructiveMigration()  // 兜底：仅调试期允许清库
                .build()
    }
}
