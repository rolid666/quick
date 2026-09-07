package com.quick.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LineDao {
    @Query("SELECT * FROM line ORDER BY name")
    fun all(): Flow<List<Line>>

    @Query("SELECT * FROM line ORDER BY name")
    suspend fun snapshot(): List<Line>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(line: Line): Long

    @Update
    suspend fun update(line: Line)

    @Delete
    suspend fun delete(line: Line)

    @Query("SELECT * FROM line WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Line?
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM model ORDER BY name")
    fun all(): Flow<List<Model>>

    @Query("SELECT * FROM model ORDER BY name")
    suspend fun snapshot(): List<Model>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(model: Model): Long

    @Update
    suspend fun update(model: Model)

    @Delete
    suspend fun delete(model: Model)

    @Query("SELECT * FROM model WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Model?

    /** 批量新增（导入配置用），逐个 IGNORE，返回成功数 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(models: List<Model>): List<Long>
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM device_config WHERE id = 1")
    fun get(): Flow<DeviceConfig?>

    @Query("SELECT * FROM device_config WHERE id = 1")
    suspend fun getOnce(): DeviceConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cfg: DeviceConfig)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_setting")
    suspend fun all(): List<AppSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSetting)

    @Query("DELETE FROM app_setting WHERE key = :key")
    suspend fun delete(key: String)
}

@Dao
interface RecordDao {
    /** 幂等键重复时 IGNORE（返回 -1），视为已存在即成功 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: MeasurementRecord): Long

    @Query(
        """
        SELECT * FROM measurement_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:result IS NULL OR result = :result)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR deviceSn LIKE '%' || :kw || '%')
        ORDER BY timestampMs DESC, id DESC
        LIMIT 20000
        """
    )
    fun query(
        fromMs: Long?,
        toMs: Long?,
        line: String?,
        model: String?,
        result: String?,
        kw: String?
    ): Flow<List<MeasurementRecord>>

    @Query(
        """
        SELECT COUNT(*) FROM measurement_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:result IS NULL OR result = :result)
        """
    )
    fun count(fromMs: Long?, toMs: Long?, line: String?, model: String?, result: String?): Flow<Int>

    @Query("SELECT * FROM measurement_record ORDER BY timestampMs DESC, id DESC LIMIT 1")
    fun lastOne(): Flow<MeasurementRecord?>

    @Query("SELECT * FROM measurement_record WHERE snapshotKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MeasurementRecord?

    @Query("DELETE FROM measurement_record WHERE snapshotKey = :key")
    suspend fun deleteByKey(key: String)
}
