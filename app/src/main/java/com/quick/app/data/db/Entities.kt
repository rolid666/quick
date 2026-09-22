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

/**
 * 站别（工位）—— 与线别/机种**平级**的独立字典（2026-09 需求：三者在测量页并排选择）。
 * 与线别/机种一样：仅作配置列表，历史记录只存名称快照，删除不影响溯源。
 */
@Entity(tableName = "station", indices = [Index(value = ["name"], unique = true)])
data class Station(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val name: String,
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 温度设置：**设定温度 + 误差范围**（2026-09 需求）。
 * 对应厂家《扫码配置指令》表 1 的三条命令 `TSET`（设定温度）/ `TMIN`（温度下限）/ `TMAX`（温度上限），
 * 组合配置码形如 `SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370`。
 * 界面仍按「设定温度 ± 误差范围」输入（操作员习惯），上下限由 App 换算。
 *
 * name 由两个数字自动生成（"350±20"）—— 新增时只输两个数字，不输名字；
 * 也正因为它是生成值，它天然唯一（同名 = 同参数），可直接做唯一索引。
 */
@Entity(tableName = "temp_preset", indices = [Index(value = ["name"], unique = true)])
data class TempPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val name: String,        // "350±20"（显示名，由 tempPresetName() 生成）
    @ColumnInfo val setTemp: Int,        // TSET 设定温度 ℃
    @ColumnInfo val tolerance: Int,      // 误差范围 ±℃（换算成 TMIN/TMAX 后才进二维码）
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

/** 温度设置的显示名：`350±20`（管理页/测量页/二维码提示统一口径，不各写一份） */
fun tempPresetName(setTemp: Int, tolerance: Int): String = "$setTemp±$tolerance"

/** 上限设置类别：漏电压上限（0x1D）/ 接地电阻上限（0x1E）—— 两个都是"配到仪器里"的参数 */
const val LIMIT_VMAX = "vmax"
const val LIMIT_RMAX = "rmax"

/**
 * 上限设置（漏电压上限 / 接地电阻上限）—— 与「温度设置」并列的字典（2026-09 需求）。
 *
 * 厂家《扫码配置指令》表 1 通用指令：`VMAX=2`（漏电压上限，mV）、`RMAX=2`（接地电阻上限，Ω）；
 * 对应真机寄存器 0x1D / 0x1E（均为 ×0.1 缩放，出厂默认原值 20 = 2.0 mV / 2.0 Ω）。
 *
 * **store 的是寄存器原值**（×0.1），与 0x1D/0x1E 同一口径 —— 扫码后可直接拿「仪器状态」里的
 * 电压上限/电阻上限核对；二维码里输出的是**整单位值**（原值 ÷10），与厂家示例 VMAX=2 对齐。
 *
 * name 由 kind + 数值生成（"2 mV" / "2.5 Ω"）：同一类别内同名即同参数，可直接做唯一索引；
 * 带上 kind 是为了让漏电压和电阻能各自有一档相同数值。
 */
@Entity(tableName = "limit_preset", indices = [Index(value = ["kind", "name"], unique = true)])
data class LimitPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val kind: String,        // LIMIT_VMAX / LIMIT_RMAX
    @ColumnInfo val name: String,        // "2 mV" / "2.5 Ω"（由 limitPresetName() 生成）
    @ColumnInfo val valueRaw: Int,       // 寄存器原值（×0.1）：20 = 2.0 mV / 2.0 Ω
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

/** 上限设置的单位：漏电压 mV / 接地电阻 Ω（与真机手册 0x1D/0x1E 的表头一致） */
fun limitUnit(kind: String): String = if (kind == LIMIT_VMAX) "mV" else "Ω"

/** 该类别对应的仪器寄存器地址（界面提示用：扫码后去这里核对） */
fun limitRegAddr(kind: String): String = if (kind == LIMIT_VMAX) "0x1D" else "0x1E"

/**
 * 寄存器原值（×0.1）→ 整单位文本：20 → `2`，25 → `2.5`。
 * 纯整数运算，不用 String.format —— 后者受系统区域设置影响，某些区域会输出 `2,5`（逗号）。
 * 二维码里要求「英文状态下输入」的字符，逗号会直接毁掉整条指令。
 */
fun limitValueText(valueRaw: Int): String {
    val whole = valueRaw / 10
    val frac = valueRaw % 10
    return if (frac == 0) whole.toString() else "$whole.$frac"
}

/** 上限设置的显示名：`2 mV` / `2.5 Ω`（管理页/测量页/二维码提示统一口径） */
fun limitPresetName(kind: String, valueRaw: Int): String = "${limitValueText(valueRaw)} ${limitUnit(kind)}"

/**
 * 设备编号（SN）—— 与线别/机种/站别一样是字典，但多一个「来源」字段：
 * - [SN_MANUAL]：管理页/测量页手工录入；
 * - [SN_LEARNED]：**保存数据时从仪器 0x0A~0x19 读到后自动学习**（2026-09 需求）——
 *   编号常常是在仪器上扫码输入的，App 主动记下来，下次就能跟温度一起生成配置二维码，
 *   不用再单独给仪器配一次。
 */
@Entity(tableName = "device_sn", indices = [Index(value = ["name"], unique = true)])
data class DeviceSn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val name: String,
    @ColumnInfo val source: String = SN_MANUAL,
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis()
)

const val SN_MANUAL = "manual"
const val SN_LEARNED = "learned"

/** 仪器通信配置（单行 id=1） */
@Entity(tableName = "device_config")
data class DeviceConfig(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo val name: String = "192AF+",
    @ColumnInfo val ip: String = "",
    @ColumnInfo val port: Int = Registers.DEFAULT_PORT,
    @ColumnInfo val unitId: Int = Registers.DEFAULT_UNIT_ID,
    @ColumnInfo val pollIntervalMs: Long = 900,    // 轮询周期：实测 0x1F 保持约 1s 后自清，须抢在窗口内（0.9s）；漏读由签名补收
    @ColumnInfo val timeoutMs: Long = 2000,        // 连接/读超时
    @ColumnInfo val autoStart: Boolean = true      // 打开 App 自动开始采集
)

/**
 * 测量记录（历史数据，绝不参与配置导出/导入）。
 * 字段与厂方手册地址一一对应（见 Registers.kt）；result 由仪器 0x23 判定，App 只记录不判定。
 * 「测试上传标志=1 时有效」的保存值（0x20~0x23）是每笔结果的真实内容，
 * 判定上限（0x1D/0x1E）同时快照下来，便于事后核对判据。
 */
@Entity(
    tableName = "measurement_record",
    indices = [Index(value = ["snapshotKey"], unique = true), Index(value = ["timestampMs"])]
)
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val snapshotKey: String,       // 幂等键（防重复保存双保险）
    @ColumnInfo val timestampMs: Long,         // App 收到该结果的时刻
    @ColumnInfo val lineName: String,
    @ColumnInfo val modelName: String,
    // 站别（与线别/机种平级的字典，v5 新增；旧数据为 ""）。
    // defaultValue 必须与 MIGRATION_4_5 的 ALTER TABLE ... DEFAULT '' 完全一致，
    // 否则「全新安装的建表语句」与「迁移后的表结构」两套 schema 校验口径不同。
    @ColumnInfo(defaultValue = "''") val stationName: String = "",
    @ColumnInfo val deviceInfo: String?,       // 0x0A~0x19 设备信息(扫码) 字符串
    @ColumnInfo val deviceIp: String,
    @ColumnInfo val channel: Int?,             // 0x04 当前测量通道 0=温度 1=漏电压 2=地对地电阻
    @ColumnInfo val targetTemp: Int?,          // 0x1A 设定温度 ℃
    @ColumnInfo val tempLow: Int?,             // 0x1B 温度判断下限 ℃
    @ColumnInfo val tempHigh: Int?,            // 0x1C 温度判断上限 ℃
    @ColumnInfo val voltageLimitMv: Double?,   // 0x1D 电压判断上限 mV（×0.1）
    @ColumnInfo val resistanceLimitOhm: Double?, // 0x1E 电阻判断上限 Ω（×0.1）
    @ColumnInfo val measuredTemp: Double?,     // 0x20 测试保存温度 ℃（仪器定格值）
    @ColumnInfo val measuredVoltageMv: Double?, // 0x21 测试保存电压 mV（×0.1）
    @ColumnInfo val measuredResistanceOhm: Double?, // 0x22 测试保存电阻 Ω（×0.1）
    @ColumnInfo val result: String             // 0x23 "OK" / "NG" / "无效"
) {
    val isOk: Boolean get() = result == "OK"
    val isInvalid: Boolean get() = result == "无效"

    companion object
}

/** 键值配置：上次选中的线别/机种、崩溃恢复用 pending 结果等 */
@Entity(tableName = "app_setting")
data class AppSetting(
    @PrimaryKey val key: String,
    @ColumnInfo val value: String
)
