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
interface StationDao {
    @Query("SELECT * FROM station ORDER BY name")
    fun all(): Flow<List<Station>>

    @Query("SELECT * FROM station ORDER BY name")
    suspend fun snapshot(): List<Station>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(station: Station): Long

    @Update
    suspend fun update(station: Station)

    @Delete
    suspend fun delete(station: Station)

    @Query("SELECT * FROM station WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Station?

    /** 批量新增（导入配置用），逐个 IGNORE，返回成功数 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(stations: List<Station>): List<Long>
}

/** 温度设置（设定温度 + 误差范围）：按温度排序，方便操作员就近找 */
@Dao
interface TempPresetDao {
    @Query("SELECT * FROM temp_preset ORDER BY setTemp, tolerance")
    fun all(): Flow<List<TempPreset>>

    @Query("SELECT * FROM temp_preset ORDER BY setTemp, tolerance")
    suspend fun snapshot(): List<TempPreset>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(preset: TempPreset): Long

    @Update
    suspend fun update(preset: TempPreset)

    @Delete
    suspend fun delete(preset: TempPreset)

    @Query("SELECT * FROM temp_preset WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TempPreset?

    /** 批量新增（导入配置用），逐个 IGNORE，返回成功数 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(presets: List<TempPreset>): List<Long>
}

/**
 * 上限设置（漏电压上限 / 接地电阻上限）：按类别再按数值排序。
 * 同一张表放两类，靠 kind 区分 —— 两者字段完全相同，分表只会让迁移/导出多写一份。
 */
@Dao
interface LimitPresetDao {
    @Query("SELECT * FROM limit_preset ORDER BY kind, valueRaw")
    fun all(): Flow<List<LimitPreset>>

    @Query("SELECT * FROM limit_preset ORDER BY kind, valueRaw")
    suspend fun snapshot(): List<LimitPreset>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(preset: LimitPreset): Long

    @Update
    suspend fun update(preset: LimitPreset)

    @Delete
    suspend fun delete(preset: LimitPreset)

    @Query("SELECT * FROM limit_preset WHERE kind = :kind AND name = :name LIMIT 1")
    suspend fun find(kind: String, name: String): LimitPreset?

    /** 批量新增（导入配置用），逐个 IGNORE，返回成功数 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(presets: List<LimitPreset>): List<Long>
}

/** 设备编号字典（手工录入 + 仪器扫码写入后自动学习） */
@Dao
interface DeviceSnDao {
    @Query("SELECT * FROM device_sn ORDER BY name")
    fun all(): Flow<List<DeviceSn>>

    @Query("SELECT * FROM device_sn ORDER BY name")
    suspend fun snapshot(): List<DeviceSn>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sn: DeviceSn): Long

    @Update
    suspend fun update(sn: DeviceSn)

    @Delete
    suspend fun delete(sn: DeviceSn)

    @Query("SELECT * FROM device_sn WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): DeviceSn?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(sns: List<DeviceSn>): List<Long>
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
          AND (:station IS NULL OR stationName = :station)
          AND (:result IS NULL OR result = :result)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR stationName LIKE '%' || :kw || '%'
                       OR deviceInfo LIKE '%' || :kw || '%')
        ORDER BY timestampMs DESC, id DESC
        LIMIT 20000
        """
    )
    fun query(
        fromMs: Long?,
        toMs: Long?,
        line: String?,
        model: String?,
        station: String?,
        result: String?,
        kw: String?
    ): Flow<List<MeasurementRecord>>

    /** 条数与 [query] 同口径（含关键词）—— 界面上显示的数字就是列表里能看到的条数 */
    @Query(
        """
        SELECT COUNT(*) FROM measurement_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:station IS NULL OR stationName = :station)
          AND (:result IS NULL OR result = :result)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR stationName LIKE '%' || :kw || '%'
                       OR deviceInfo LIKE '%' || :kw || '%')
        """
    )
    fun count(
        fromMs: Long?, toMs: Long?, line: String?, model: String?, station: String?,
        result: String?, kw: String?
    ): Flow<Int>

    /** 单条删除（记录页「删除此条」，需密码） */
    @Query("DELETE FROM measurement_record WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * 按当前筛选批量删除（需密码）。条件与 [query] 完全一致 ——
     * 「看到的就是要删的」，避免筛选与删除口径不一致误删别的记录。
     */
    @Query(
        """
        DELETE FROM measurement_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:station IS NULL OR stationName = :station)
          AND (:result IS NULL OR result = :result)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR stationName LIKE '%' || :kw || '%'
                       OR deviceInfo LIKE '%' || :kw || '%')
        """
    )
    suspend fun deleteByFilter(
        fromMs: Long?, toMs: Long?, line: String?, model: String?, station: String?,
        result: String?, kw: String?
    ): Int

    @Query("SELECT * FROM measurement_record ORDER BY timestampMs DESC, id DESC LIMIT 1")
    fun lastOne(): Flow<MeasurementRecord?>

    @Query("SELECT * FROM measurement_record WHERE snapshotKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MeasurementRecord?

    @Query("DELETE FROM measurement_record WHERE snapshotKey = :key")
    suspend fun deleteByKey(key: String)
}
