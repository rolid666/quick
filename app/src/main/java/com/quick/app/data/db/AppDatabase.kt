package com.quick.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Line::class, Model::class, DeviceConfig::class, MeasurementRecord::class, AppSetting::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lineDao(): LineDao
    abstract fun modelDao(): ModelDao
    abstract fun configDao(): ConfigDao
    abstract fun settingDao(): SettingDao
    abstract fun recordDao(): RecordDao

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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "measure.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()  // 兜底：仅调试期允许清库
                .build()
    }
}
