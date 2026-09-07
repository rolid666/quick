package com.quick.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Line::class, Model::class, DeviceConfig::class, MeasurementRecord::class, AppSetting::class],
    version = 2,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "measure.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()  // 兜底：仅调试期允许清库
                .build()
    }
}
