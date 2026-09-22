package com.quick.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * 扭力计数据（2026-09-21 新增，2026-09-22 按实测格式定稿）—— **与烙铁数据物理隔离**。
 *
 * 「隔离」的落法：**另起表**，不复用烙铁任何一张表、不共享任何一列（用户要求 1）。
 * 仍然放在**同一个数据库文件**里，因为：
 * 1. 删除历史密码是同一个（用户要求 6）—— 密码存在 app_setting，分库就得配两套；
 * 2. 一次迁移、一个备份文件、一套 DAO 事务边界，比两个 Room 实例少一半出错面；
 * 3. 「独立」的实质是**读写路径互不交叉**：扭力计的查询永远不会碰到 measurement_record，
 *    烙铁的查询也不会碰到 torque_record（UI/导出/删除各走各的 DAO）。
 * 万一将来真要分库（例如扭力计数据迁到另一台设备），只需换 Room 的 databaseBuilder 参数。
 */

// ---------- 单位与数值格式（唯一口径处，界面/入库/导出都走这里）----------

/**
 * 扭力计实测单位（2026-09-22 用户实测确认：`+5.40kgf*cm`）。
 * 星号是设备原样输出的写法，不改成 `·` —— QC 记录里跟设备显示一致最不容易出错。
 */
const val TORQUE_UNIT = "kgf*cm"

/**
 * 扭力数值文本：**固定 2 位小数**。
 * 设备实测就是 2 位（`5.40` / `6.23`），比它多写的位数是假精度；
 * 平均值同样按 2 位圆整后**再判定** —— 这样「屏幕上看到的数」与「屏幕上看到的 OK/NG」永远一致。
 * 空值给 "--"（理论上不会出现：只有四项选全才保存）。
 */
fun torqueValueText(v: Double?): String =
    if (v == null) "--" else String.format(Locale.US, "%.2f", v)

/** 数值的一般写法（去尾零）：0.50 → 0.5、6.00 → 6、6.25 → 6.25（用于扭矩范围名称） */
fun torqueNumText(v: Double): String {
    val s = String.format(Locale.US, "%.2f", v)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

/**
 * 扭矩范围的名称：**由上下限自动生成**（用户 2026-09-22 拍板，与「温度设置」「上限设置」同一套做法）。
 * 操作员只填两个数，不用打字、也不会写错单位。
 * 例：下限 0.5 + 上限 6 → `0.5~6.0 kgf*cm`
 */
fun torqueRangeName(min: Double, max: Double): String =
    "${torqueNumText(min)}~${torqueNumText(max)} $TORQUE_UNIT"

// ---------- 字典类别（四类字典共用一张表，靠 kind 区分，见 [TorqueDict]）----------

const val TQ_LINE = "line"
const val TQ_MODEL = "model"
const val TQ_RANGE = "range"
const val TQ_DEVICE = "device"

/** 字典类别的中文名（管理页页签 / 测量页下拉标签统一走这里，不各写一份） */
fun torqueDictLabel(kind: String): String = when (kind) {
    TQ_LINE -> "线别"
    TQ_MODEL -> "机种"
    TQ_RANGE -> "扭矩范围"
    else -> "设备信息"
}

/** 四个类别的固定顺序（界面按这个顺序排下拉/页签/导出列） */
val TORQUE_DICT_KINDS = listOf(TQ_LINE, TQ_MODEL, TQ_RANGE, TQ_DEVICE)

/**
 * 扭力计字典：线别 / 机种 / 扭矩范围 / 设备信息（用户要求 2）。
 *
 * 四者共用一张表用 kind 区分，但**「扭矩范围」多两个数**（2026-09-22 用户要求 5）：
 * `minValue` / `maxValue`（kgf*cm）—— 只有 TQ_RANGE 用得上，其余三类留空。
 * 判定 OK/NG 就是拿平均值跟这两个数比（用户要求 6），所以它们必须与名称一起落库。
 *
 * `name` 对 TQ_RANGE 由 [torqueRangeName] 生成；对另外三类是操作员输入的名字。
 *
 * (kind, name) 唯一索引：同一类别内同名即同一条，可直接做 Compose key。
 */
@Entity(tableName = "torque_dict", indices = [Index(value = ["kind", "name"], unique = true)])
data class TorqueDict(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo val kind: String,        // TQ_LINE / TQ_MODEL / TQ_RANGE / TQ_DEVICE
    @ColumnInfo val name: String,
    @ColumnInfo val enabled: Boolean = true,
    @ColumnInfo val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo val updatedAt: Long = System.currentTimeMillis(),
    /** 扭矩下限（kgf*cm）—— 仅 TQ_RANGE 有值 */
    @ColumnInfo val minValue: Double? = null,
    /** 扭矩上限（kgf*cm）—— 仅 TQ_RANGE 有值 */
    @ColumnInfo val maxValue: Double? = null
)

/**
 * 扭力计测量记录：**一次测量组 = 一行**（2026-09-22 用户要求 4）。
 *
 * 用户原话：「必须要有时间，线别，机种，扭矩范围，设备信息，**三次扭矩测量结果**，平均扭矩，判断结果」
 * —— 三笔结果是同一行的三列，所以记录也跟着变成「一组一行」：
 * - 三笔在 [v1] / [v2] / [v3]（顺序就是到达顺序）；
 * - [average] = 三笔平均（**2 位小数**，先圆整再判定，见 [torqueValueText]）；
 * - [judge] = 拿平均值跟所选扭矩范围的上下限比出来的 `OK` / `NG`（用户要求 6）；
 * - [rangeMin] / [rangeMax] 是**判定当时的上下限快照** —— 范围字典以后被改了，
 *   这条记录仍然能自证「当时凭什么判 OK」。QC 记录必须能自证。
 *
 * 一行只在**四项（线别/机种/扭矩范围/设备信息）全都选了**时才会写（用户 2026-09-22 硬要求），
 * 所以库里的每一行都信息完整、都有判定值。
 *
 * ⚠️ 三笔是**暂存**（用户要求：三次结果分开显示暂存，可「重测」清掉），
 * 收满三笔后等设定的延时（默认 5 秒）没有重测才自动落库 —— 所以
 * **未保存的组在数据库里不存在**，重启 App 也不会有半截记录。
 */
@Entity(
    tableName = "torque_record",
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index(value = ["timestampMs"])
    ]
)
data class TorqueRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 组号（一次测量组一个，形如 `s<毫秒>`）；唯一索引 = 幂等键，重复保存进不来 */
    @ColumnInfo val sessionId: String,
    /** **第三笔**的时间（用户 2026-09-22 拍板：记录/导出的「时间」取完成这一组的时刻） */
    @ColumnInfo val timestampMs: Long,
    /** 自动保存落库的时刻（第三笔 + 延时） */
    @ColumnInfo val savedAtMs: Long,
    @ColumnInfo val lineName: String,
    @ColumnInfo val modelName: String,
    @ColumnInfo val rangeName: String,       // 扭矩范围字典名（如 `0.5~6.0 kgf*cm`）
    @ColumnInfo val rangeMin: Double?,       // 判定用的下限快照（kgf*cm）
    @ColumnInfo val rangeMax: Double?,       // 判定用的上限快照（kgf*cm）
    @ColumnInfo val deviceName: String,      // 设备信息 = 电动螺丝机的 ID（扫码/手输）
    @ColumnInfo val v1: Double?,             // 第 1 笔（kgf*cm）
    @ColumnInfo val v2: Double?,             // 第 2 笔
    @ColumnInfo val v3: Double?,             // 第 3 笔
    @ColumnInfo val average: Double?,        // 三次平均（2 位小数）
    @ColumnInfo val judge: String,           // "OK" / "NG"；（旧数据迁移过来时为 ""＝未判定）
    @ColumnInfo val sampleCount: Int = 3,    // 参与平均的笔数
    @ColumnInfo val unitText: String = TORQUE_UNIT,  // 报文里的单位（设备原样）
    @ColumnInfo val rawText: String = "",    // 三笔报文原文（` | ` 分隔）
    @ColumnInfo val rawHex: String = ""      // 三笔报文字节 hex（逐字节可回溯）
) {
    val averageText: String get() = torqueValueText(average)
    val v1Text: String get() = torqueValueText(v1)
    val v2Text: String get() = torqueValueText(v2)
    val v3Text: String get() = torqueValueText(v3)

    /** 判定是否 OK；`null` = 没有判定（旧数据 / 没有范围快照） */
    val isOk: Boolean? get() = when (judge) {
        TORQUE_OK -> true
        TORQUE_NG -> false
        else -> null
    }

    /** 空伴生对象：`fromJson` 这类 JVM 静态扩展挂在这里（与 [MeasurementRecord] 同一做法） */
    companion object
}

const val TORQUE_OK = "OK"
const val TORQUE_NG = "NG"

/**
 * 判定：平均值落在所选扭矩范围内 → OK，否则 NG（用户要求 6：「位于所选扭矩范围内为OK，反之结果为NG」）。
 *
 * 边界取**闭区间**（等于上下限算 OK）—— 这是「范围内」的常规含义，
 * 且现场按标准值卡边界时，判成不合格会引起不必要的返工。
 * 三个值缺任何一个都返回 null（**不编造判定**）。
 */
fun torqueJudge(average: Double?, min: Double?, max: Double?): String? {
    if (average == null || min == null || max == null) return null
    return if (average >= min && average <= max) TORQUE_OK else TORQUE_NG
}
