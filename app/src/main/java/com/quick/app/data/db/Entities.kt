package com.quick.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quick.app.net.Registers

/**
 * 线别（配置数据，可导入导出；历史记录只存名称快照，删除不影响溯源）。
 * name 唯一索引：管理页列表用 name 做 Compose key，且 UI 多处按名查重 ——
 * 无约束时同名可重复入库导致列表渲染崩溃（v1 实机教训，v2 起强制）。
 */
@Entity(tableName = "line", indices = [Index(value = ["name"], unique = true)])
data class Line(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val name: String,
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

/** 机种（配置数据） */
@Entity(tableName = "model", indices = [Index(value = ["name"], unique = true)])
data class Model(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val name: String,
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

/** 仪器通信配置（单行 id=1） */
@Entity(tableName = "device_config")
data class DeviceConfig(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo val name: String = "191AF+",
    @ColumnInfo val ip: String = "",
    @ColumnInfo val port: Int = Registers.DEFAULT_PORT,
    @ColumnInfo val unitId: Int = Registers.DEFAULT_UNIT_ID,
    @ColumnInfo val pollIntervalMs: Long = 1000,   // 轮询周期（厂家建议约 1s）
    @ColumnInfo val timeoutMs: Long = 2000,        // 连接/读超时
    @ColumnInfo val autoStart: Boolean = true      // 打开 App 自动开始采集
)

/**
 * 测量记录（历史数据，绝不参与配置导出/导入）。
 * result 由仪器 0x1E 判定（0=NG 1=OK），App 只记录不判定。
 */
@Entity(
    tableName = "measurement_record",
    indices = [Index(value = ["snapshotKey"], unique = true), Index(value = ["timestampMs"])]
)
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val snapshotKey: String,   // 幂等键（防重复保存双保险）
    @ColumnInfo val timestampMs: Long,     // App 收到该结果的时刻
    @ColumnInfo val lineName: String,
    @ColumnInfo val modelName: String,
    @ColumnInfo val deviceSn: String?,     // 仪器 0x0A~0x19 读取的 SN
    @ColumnInfo val deviceIp: String,
    @ColumnInfo val setTemp: Int?,         // 0x1A ℃
    @ColumnInfo val measuredTemp: Int?,    // 0x1D ℃（系数 1）
    @ColumnInfo val tolerance: Int?,       // 0x1B ±℃
    @ColumnInfo val result: String         // "OK" / "NG"
) {
    val isOk: Boolean get() = result == "OK"

    companion object
}

/** 键值配置：上次选中的线别/机种、崩溃恢复用 pending 结果等 */
@Entity(tableName = "app_setting")
data class AppSetting(
    @PrimaryKey val key: String,
    @ColumnInfo val value: String
)
