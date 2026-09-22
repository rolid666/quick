package com.quick.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 扭力计字典 DAO（线别/机种/扭矩范围/设备信息四类共用）。
 * 所有查询都带 kind —— 类别之间不会互相串（这正是合表的唯一代价，写死在 SQL 里）。
 */
@Dao
interface TorqueDictDao {
    @Query("SELECT * FROM torque_dict WHERE kind = :kind ORDER BY name")
    fun all(kind: String): Flow<List<TorqueDict>>

    @Query("SELECT * FROM torque_dict ORDER BY kind, name")
    fun allKinds(): Flow<List<TorqueDict>>

    @Query("SELECT * FROM torque_dict WHERE kind = :kind ORDER BY name")
    suspend fun snapshot(kind: String): List<TorqueDict>

    @Query("SELECT * FROM torque_dict ORDER BY kind, name")
    suspend fun snapshotAll(): List<TorqueDict>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TorqueDict): Long

    @Update
    suspend fun update(item: TorqueDict)

    @Query("DELETE FROM torque_dict WHERE kind = :kind AND name = :name")
    suspend fun delete(kind: String, name: String): Int

    @Query("SELECT * FROM torque_dict WHERE kind = :kind AND name = :name LIMIT 1")
    suspend fun find(kind: String, name: String): TorqueDict?

    /** 批量新增（导入配置用），逐个 IGNORE，返回成功数 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<TorqueDict>): List<Long>
}

/**
 * 扭力计记录 DAO —— 与烙铁的 [RecordDao] **完全独立**：
 * 两个 DAO 操作两张表，任何一条 SQL 都不跨表（用户要求 1：数据不能混在一起）。
 * 查询/计数/删除的条件逐字一致（与烙铁同一个纪律：「看到的就是要删的」）。
 *
 * 一行 = 一次测量组（三笔 + 平均 + 判定），见 [TorqueRecord]。
 */
@Dao
interface TorqueRecordDao {
    /** 组号重复时 IGNORE（返回 -1）：一次测量组只会有一行，双保险 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: TorqueRecord): Long

    @Query(
        """
        SELECT * FROM torque_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:range IS NULL OR rangeName = :range)
          AND (:device IS NULL OR deviceName = :device)
          AND (:judge IS NULL OR judge = :judge)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR rangeName LIKE '%' || :kw || '%'
                       OR deviceName LIKE '%' || :kw || '%'
                       OR rawText LIKE '%' || :kw || '%')
        ORDER BY timestampMs DESC, id DESC
        LIMIT 20000
        """
    )
    fun query(
        fromMs: Long?,
        toMs: Long?,
        line: String?,
        model: String?,
        range: String?,
        device: String?,
        judge: String?,
        kw: String?
    ): Flow<List<TorqueRecord>>

    /** 条数与 [query] 同口径（含关键词）—— 界面上显示的数字就是列表里能看到的条数 */
    @Query(
        """
        SELECT COUNT(*) FROM torque_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:range IS NULL OR rangeName = :range)
          AND (:device IS NULL OR deviceName = :device)
          AND (:judge IS NULL OR judge = :judge)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR rangeName LIKE '%' || :kw || '%'
                       OR deviceName LIKE '%' || :kw || '%'
                       OR rawText LIKE '%' || :kw || '%')
        """
    )
    fun count(
        fromMs: Long?, toMs: Long?, line: String?, model: String?, range: String?,
        device: String?, judge: String?, kw: String?
    ): Flow<Int>

    /** 单条删除（记录页「删除此条」，需密码） */
    @Query("DELETE FROM torque_record WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** 按当前筛选批量删除（需密码）。条件与 [query] 完全一致 ——「看到的就是要删的」 */
    @Query(
        """
        DELETE FROM torque_record
        WHERE (:fromMs IS NULL OR timestampMs >= :fromMs)
          AND (:toMs IS NULL OR timestampMs <= :toMs)
          AND (:line IS NULL OR lineName = :line)
          AND (:model IS NULL OR modelName = :model)
          AND (:range IS NULL OR rangeName = :range)
          AND (:device IS NULL OR deviceName = :device)
          AND (:judge IS NULL OR judge = :judge)
          AND (:kw IS NULL OR lineName LIKE '%' || :kw || '%'
                       OR modelName LIKE '%' || :kw || '%'
                       OR rangeName LIKE '%' || :kw || '%'
                       OR deviceName LIKE '%' || :kw || '%'
                       OR rawText LIKE '%' || :kw || '%')
        """
    )
    suspend fun deleteByFilter(
        fromMs: Long?, toMs: Long?, line: String?, model: String?, range: String?,
        device: String?, judge: String?, kw: String?
    ): Int

    @Query("SELECT * FROM torque_record ORDER BY timestampMs DESC, id DESC LIMIT 1")
    fun lastOne(): Flow<TorqueRecord?>

    @Query("SELECT COUNT(*) FROM torque_record")
    fun total(): Flow<Int>
}
