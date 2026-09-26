package com.quick.app.torque

import kotlin.math.round

/**
 * 三次一组的**暂存组**（用户 2026-09-22 定稿的流程）：
 *
 * ```
 * 第 1 笔 → 暂存（界面显示 1/3，可「重测」清掉，也可用格子上的小叉只删这一笔）
 * 第 2 笔 → 暂存（2/3）
 * 第 3 笔 → 暂存（3/3）→ 起延时计时（默认 5 秒）
 *          ├─ 期间点了「重测」 → 全部清掉，重新从 1/3 开始
 *          ├─ 期间叉掉某一笔 → 本组不再满，计时作废；下一笔测完自动补回 3/3
 *          └─ 时间到且四项信息齐全 → 自动保存成**一行**（三笔 + 平均 + 判定）
 * ```
 *
 * 四条硬规则（都在这里、都被单测钉死）：
 * 1. **满组之后的第 4 笔不并入本组**（用户 2026-09-22 拍板）：只记一笔「已忽略」，
 *    页面上写明、日志里留痕 —— 设备万一重复发一次也不会污染记录。组照常保存。
 * 2. **平均值按 2 位小数圆整**（与设备输出精度、界面显示、导出口径一致）。
 * 3. 满组后**不清空**：调用方保存完再 [reset]，否则界面在保存前的一瞬间会看到空的。
 * 4. **单笔可删**（用户 2026-09-26）：[removeAt] 删掉一笔后，后面的笔**往前补位**
 *    （不留空洞），于是下一笔测量自然补齐到满组 —— 「下次记录自动补充清除的测量结果」。
 *
 * 纯逻辑、无副作用、无 Android 依赖。「能不能保存」由控制器判（涉及四项信息是否选全）。
 *
 * **线程**：本类自己的方法都在内部锁里读写状态 —— 收笔在读 USB 的 IO 线程，
 * 而「重测」「单笔删除」是界面线程点出来的（两者都不设密码），不加锁会同时改同一个队列。
 *
 * @param size 一组几笔（默认 3，做成参数是为了将来现场要改成 5 次时不用动逻辑）
 */
class TorqueSession(private val size: Int = 3) {

    /**
     * 一笔暂存。数值 / 报文原文 / 单位 / 时刻**放在一起**：
     * 单笔删除时四样一起走，不会出现「数删了、时刻还留着」这种对不上的状态。
     */
    private data class Sample(val value: Double, val text: String, val unit: String, val atMs: Long)

    private val samples = ArrayList<Sample>(size)

    /**
     * 所有状态都在这个锁里读写。为什么需要（2026-09-26 加锁）：
     * 收笔在**读 USB 的 IO 线程**上做，而「重测」和「单笔删除」是**界面线程**点出来的
     * （用户要求这两个都不设密码，点一下就生效）。两边同时改同一个 ArrayList 时，
     * 轻则丢一笔、重则抛异常崩掉这一次测量 —— 一组数据宁可慢一点也不能错。
     * 锁里只做纯内存操作（不回调、不 IO），不会与别的锁形成嵌套。
     */
    private val lock = Any()

    /** 本组已收笔数（0..size） */
    val count: Int get() = synchronized(lock) { samples.size }

    /** 是否已满组（满组后进入「等延时、可重测」的窗口） */
    val isFull: Boolean get() = synchronized(lock) { samples.size >= size }

    /** 本组数值快照（界面上三笔要分开显示） */
    val sample: List<Double> get() = synchronized(lock) { samples.map { it.value } }

    /** 本组每笔的报文原文（保存进记录，逐笔可回溯） */
    val sampleTexts: List<String> get() = synchronized(lock) { samples.map { it.text } }

    /** 满组后又被忽略的笔数（第 4 笔、第 5 笔……）—— 界面要如实显示 */
    @Volatile
    var ignoredCount: Int = 0
        private set

    /** 第 1 笔的时刻（组从什么时候开始测）；空组为 0。删笔后跟着重算。 */
    val startedAtMs: Long get() = synchronized(lock) { samples.firstOrNull()?.atMs ?: 0L }

    /** 最后一笔的时刻（记录里的「时间」取这个 —— 用户 2026-09-22 拍板取第三笔）；空组为 0 */
    val lastAtMs: Long get() = synchronized(lock) { samples.lastOrNull()?.atMs ?: 0L }

    /** 本组单位（取第一笔的；同一次测量不会换单位） */
    val unit: String get() = synchronized(lock) { samples.firstOrNull()?.unit ?: "" }

    /** 本组三笔的原始字节 hex（` | ` 分隔，逐字节可回溯） */
    fun rawHexJoined(hexOf: (String) -> String): String =
        synchronized(lock) { samples.joinToString(" | ") { hexOf(it.text) } }

    /**
     * 收一笔。返回这一步的进度。
     *
     * 满组后再来一笔 → **不并入**，只把 [ignoredCount] 加一并返回 `ignored = true`
     * 的步骤（页面据此显示「第 N 笔已忽略」）。
     */
    fun add(value: Double, text: String, unit: String, atMs: Long): SessionStep = synchronized(lock) {
        if (samples.size >= size) {
            ignoredCount++
            return@synchronized SessionStep(
                seq = 0, total = size, done = true, average = averageLocked(), ignored = true
            )
        }
        samples += Sample(value, text, unit, atMs)
        val done = samples.size >= size
        SessionStep(
            seq = samples.size,
            total = size,
            done = done,
            average = if (done) averageLocked() else null,
            ignored = false
        )
    }

    /**
     * 删掉第 [index] 笔（0 起算；界面上每格右上角的小叉，用户 2026-09-26 要求）。
     *
     * 语义（都被单测钉死）：
     * - 后面的笔**往前补位**，不留空洞 —— 所以本组不再满（[isFull] 变 false），
     *   下一笔测量会自然补到最后一格，这就是用户要的「下次记录自动补充」；
     * - [startedAtMs] / [lastAtMs] 按**剩下的**笔重算（删光则都回 0），
     *   不会留下「一笔都没有、时刻却是上一笔的」这种对不上的状态；
     * - [ignoredCount] **不动**：它记的是「这一组历史上被忽略过几笔」这个事实，
     *   删一笔并不能让那些笔没发生过。
     *
     * @return 是否真的删掉了（下标越界 = 什么都没干，返回 false）
     */
    fun removeAt(index: Int): Boolean = synchronized(lock) {
        if (index < 0 || index >= samples.size) return@synchronized false
        samples.removeAt(index)
        true
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
    fun average(): Double? = synchronized(lock) { averageLocked() }

    /** 调用方已持锁时用的平均值（避免重复加锁，逻辑与 [average] 完全同一份） */
    private fun averageLocked(): Double? {
        if (samples.size < size) return null
        return round(samples.map { it.value }.average() * 100.0) / 100.0
    }

    /** 重测：把这一组全部清掉（用户要求：重测按键无需密码，点了就清暂存结果） */
    fun reset() = synchronized(lock) {
        samples.clear()
        ignoredCount = 0
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
