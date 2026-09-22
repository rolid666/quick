package com.quick.app.torque

import kotlin.math.round

/**
 * 三次一组的**暂存组**（用户 2026-09-22 定稿的流程）：
 *
 * ```
 * 第 1 笔 → 暂存（界面显示 1/3，可「重测」清掉）
 * 第 2 笔 → 暂存（2/3）
 * 第 3 笔 → 暂存（3/3）→ 起延时计时（默认 5 秒）
 *          ├─ 期间点了「重测」 → 全部清掉，重新从 1/3 开始
 *          └─ 时间到且四项信息齐全 → 自动保存成**一行**（三笔 + 平均 + 判定）
 * ```
 *
 * 三条硬规则（都在这里、都被单测钉死）：
 * 1. **满组之后的第 4 笔不并入本组**（用户 2026-09-22 拍板）：只记一笔「已忽略」，
 *    页面上写明、日志里留痕 —— 设备万一重复发一次也不会污染记录。组照常保存。
 * 2. **平均值按 2 位小数圆整**（与设备输出精度、界面显示、导出口径一致）。
 * 3. 满组后**不清空**：调用方保存完再 [reset]，否则界面在保存前的一瞬间会看到空的。
 *
 * 纯逻辑、无副作用、无 Android 依赖。「能不能保存」由控制器判（涉及四项信息是否选全）。
 *
 * @param size 一组几笔（默认 3，做成参数是为了将来现场要改成 5 次时不用动逻辑）
 */
class TorqueSession(private val size: Int = 3) {

    private val values = ArrayList<Double>(size)
    private val texts = ArrayList<String>(size)
    private val units = ArrayList<String>(size)

    /** 本组已收笔数（0..size） */
    val count: Int get() = values.size

    /** 是否已满组（满组后进入「等延时、可重测」的窗口） */
    val isFull: Boolean get() = values.size >= size

    /** 本组数值快照（界面上三笔要分开显示） */
    val sample: List<Double> get() = values.toList()

    /** 本组每笔的报文原文（保存进记录，逐笔可回溯） */
    val sampleTexts: List<String> get() = texts.toList()

    /** 满组后又被忽略的笔数（第 4 笔、第 5 笔……）—— 界面要如实显示 */
    var ignoredCount: Int = 0
        private set

    /** 第 1 笔的时刻（组从什么时候开始测） */
    var startedAtMs: Long = 0L
        private set

    /** 最后一笔的时刻（记录里的「时间」取这个 —— 用户 2026-09-22 拍板取第三笔） */
    var lastAtMs: Long = 0L
        private set

    /** 本组单位（取第一笔的；同一次测量不会换单位） */
    val unit: String get() = units.firstOrNull() ?: ""

    /** 本组三笔的原始字节 hex（` | ` 分隔，逐字节可回溯） */
    fun rawHexJoined(hexOf: (String) -> String): String =
        texts.joinToString(" | ") { hexOf(it) }

    /**
     * 收一笔。返回这一步的进度。
     *
     * 满组后再来一笔 → **不并入**，只把 [ignoredCount] 加一并返回 `ignored = true`
     * 的步骤（页面据此显示「第 N 笔已忽略」）。
     */
    fun add(value: Double, text: String, unit: String, atMs: Long): SessionStep {
        if (isFull) {
            ignoredCount++
            return SessionStep(seq = 0, total = size, done = true, average = average(), ignored = true)
        }
        if (values.isEmpty()) startedAtMs = atMs
        values += value
        texts += text
        units += unit
        lastAtMs = atMs
        val done = isFull
        return SessionStep(
            seq = values.size,
            total = size,
            done = done,
            average = if (done) average() else null,
            ignored = false
        )
    }

    /**
     * 本组平均值：**满组才有**（未满组返回 null）。
     *
     * 半组的平均数不是「这一组的平均值」—— 真把它显示出来，操作员会当成最终值看
     * （第 1 笔刚测完就显示一个「平均」，第 2 笔进来它又变了）。宁可不显示。
     *
     * 数值**按 2 位小数四舍五入**：设备输出就是 2 位（`5.40`），
     * 不圆整的话界面上会出现 `5.003333333333333`，而判定又是拿这个数去比上下限的
     * （先圆整再判定，「屏幕上看到的数」与「屏幕上看到的 OK/NG」才永远一致）。
     */
    fun average(): Double? {
        if (!isFull) return null
        return round(values.average() * 100.0) / 100.0
    }

    /** 重测：把这一组全部清掉（用户要求：重测按键无需密码，点了就清暂存结果） */
    fun reset() {
        values.clear()
        texts.clear()
        units.clear()
        ignoredCount = 0
        startedAtMs = 0L
        lastAtMs = 0L
    }

    /** 一组收笔后的进度信息 */
    data class SessionStep(
        val seq: Int,             // 本笔是第几笔（1..total）；被忽略时 = 0
        val total: Int,           // 一组几笔
        val done: Boolean,        // 是否满组（满组 → 起延时计时）
        val average: Double?,     // 满组时的平均值；未满组为 null
        val ignored: Boolean      // 本笔因为「本组已满」被忽略
    )
}
