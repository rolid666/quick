package com.quick.app.net

/**
 * 192AF+（原 191AF+）系列保持寄存器定义 —— 以厂方手册《地址分配》表为准
 * （2026-09 用户提供的原始手册页：`piture/file.webp` 第 2 页「二、地址分配」）。
 *
 * ⚠️ 与上一版的差异（v3 曾按「0x1E = 结果保存标志」实现，那是误认）：
 *   结果块完整位于 **0x1F ~ 0x23**（测试上传标志 / 保存温度 / 保存电压 / 保存电阻 / 合格判定），
 *   而 v3 只读 0x00~0x1E（31 个寄存器），恰好停在结果块之前 —— 之前把 0x1E（电阻判断上限，
 *   默认 20 = 0x0014）当成了"保存标志=0x14、保持后自清"。详见 docs §19。
 *
 * MODBUS 层无变化：TCP 上 MBAP + 同样的 PDU（FC 0x03 读 / 0x06 写），单位号默认 1。
 */
object Registers {
    const val LIVE_TEMP = 0x00        // 实时温度, ℃, ×0.1
    const val UNIT = 0x01             // 温度单位 0=℃ 1=℉
    const val LIVE_VOLTAGE = 0x02     // 实时电压, mV, ×0.1
    const val LIVE_RESISTANCE = 0x03  // 实时电阻, Ω, ×0.1
    const val CHANNEL = 0x04          // 当前测量通道 0=温度 1=漏电压 2=地对地电阻
    // 0x05~0x09 备用

    const val INFO_START = 0x0A       // 设备信息(扫码), 32 字节 ASCII
    const val INFO_COUNT = 16         // 0x0A~0x19 = 16 寄存器 = 32 字节

    const val TARGET_TEMP = 0x1A      // 设定温度(扫码), ℃, ×1
    const val TEMP_LOW = 0x1B         // 温度判断下限, ℃, ×1
    const val TEMP_HIGH = 0x1C        // 温度判断上限, ℃, ×1
    const val VOLT_LIMIT = 0x1D       // 电压判断上限, mV, ×0.1（默认 20 → 2 mV）
    const val RES_LIMIT = 0x1E        // 电阻判断上限, Ω, ×0.1（默认 20 → 2 Ω）

    const val UPLOAD_FLAG = 0x1F      // 测试上传标志：0=无效 1=保存按钮按下（读取后自动清零）★触发
    const val SAVED_TEMP = 0x20       // 测试保存温度, ℃, ×1（标志=1 时有效）★测量温度真值
    const val SAVED_VOLTAGE = 0x21    // 测试保存电压, mV, ×0.1（标志=1 时有效）
    const val SAVED_RESISTANCE = 0x22 // 测试保存电阻, Ω, ×0.1（标志=1 时有效）
    const val JUDGE = 0x23            // 测试合格判定：0=NG 1=OK 2=无效（标志=1 时有效）★结果

    /** 完整表 0x00~0x23 共 36 个 —— 一次全读即拿到「触发标志 + 保存值 + 判定」的同帧原子快照 */
    const val TOTAL = 0x24

    /**
     * 降级长度：0x00~0x1E 共 31 个（v3 的读法）。
     * 若仪器固件没有 0x1F~0x23，全读会回异常码 02（非法数据地址）→ 控制器自动退到本长度重试，
     * 此时结果块不可读，只能用实时值兜底并在日志中明确告警（见 MeasurementController.runLoop）。
     */
    const val LEGACY_TOTAL = 0x1F

    const val DEFAULT_PORT = 502
    const val DEFAULT_UNIT_ID = 1
}

/** 测量通道（0x04）的显示名 */
fun channelText(v: Int?): String = when (v) {
    0 -> "温度"
    1 -> "漏电压"
    2 -> "地对地电阻"
    else -> "--"
}

/** 0x23 判定值的显示名（1=OK 0=NG 2=无效；其它值按无效处理，不臆测为合格） */
fun judgeText(v: Int?): String = when (v) {
    1 -> "OK"
    0 -> "NG"
    2 -> "无效"
    else -> "--"
}

/**
 * 一次全读后解析出的仪器快照（同一响应帧，字段间天然原子）。
 * [hasResultBlock] = 是否读到 0x1F~0x23：false 表示仪器只提供 0x00~0x1E（降级模式）。
 */
data class DeviceSnapshot(
    val raw: IntArray,
    val hasResultBlock: Boolean,
    val liveTempC: Double?,          // 0x00 ×0.1 ℃
    val unit: Int,                   // 0x01（缺省按 0=℃ 处理）
    val liveVoltageMv: Double?,      // 0x02 ×0.1 mV
    val liveResistanceOhm: Double?,  // 0x03 ×0.1 Ω
    val channel: Int?,               // 0x04 当前测量通道
    val deviceInfo: String?,         // 0x0A~0x19 ASCII 设备信息(扫码)
    val targetTemp: Int?,            // 0x1A ℃
    val tempLow: Int?,               // 0x1B ℃
    val tempHigh: Int?,              // 0x1C ℃
    val voltageLimitMv: Double?,     // 0x1D ×0.1 mV
    val resistanceLimitOhm: Double?, // 0x1E ×0.1 Ω
    val uploadFlag: Int?,            // 0x1F 原始值（诊断显示用）
    val savedTempC: Int?,            // 0x20 ℃（仪器定格的测量温度）
    val savedVoltageMv: Double?,     // 0x21 ×0.1 mV
    val savedResistanceOhm: Double?, // 0x22 ×0.1 Ω
    val judge: Int?                  // 0x23 0=NG 1=OK 2=无效
) {
    /** 0x1F 非 0 = 仪器上刚按过保存 */
    val hasSaveFlag: Boolean get() = (uploadFlag ?: 0) != 0

    /** 判定是否合格：**仅 1 为 OK**；0=NG，2=无效（不臆测，非 1 一律不算合格） */
    val judgedOk: Boolean? get() = judge?.let { it == 1 }

    /** 判定为「无效」：仪器明确给出 2，或给出了未知值 */
    val judgedInvalid: Boolean get() = judge != null && judge != 0 && judge != 1

    /** 结果文本，与仪器判定一一对应 */
    val resultText: String get() = when (judge) {
        1 -> "OK"
        0 -> "NG"
        null -> "NG"
        else -> "无效"
    }

    /**
     * 触发值：完整表用 0x1F（测试上传标志）；
     * 降级模式（仪器无结果块）退回 0x1E 原始值 —— 早期实测曾观察到它随保存变化（未经验证），
     * 若它其实是恒定的判断上限，控制器侧的「首帧基线 + 值变化」判定不会误触发。
     */
    val triggerRaw: Int get() = if (hasResultBlock) (uploadFlag ?: 0) else (rawAt(Registers.RES_LIMIT) ?: 0)

    /** 记录用测量温度：优先仪器定格的 0x20；降级模式回落到实时温度 0x00（并在日志告警） */
    val measuredTempC: Double? get() = savedTempC?.toDouble() ?: liveTempC

    /**
     * 结果签名 —— **只含 0x20~0x23**（判定 + 保存温度/电压/电阻），即「只有按保存才会写」的四个寄存器。
     * 两个用途：
     *   ① 标志(0x1F)在固件不清零时同一笔会占多帧 → 签名相同则跳过，不重复入库；
     *   ② 标志已回 0 但签名变了 → 判定为「按了保存、本轮读晚了」，补收该笔（见控制器）。
     * **刻意不含**实时量(0x00/0x02/0x03)与 0x04 测量通道、0x1A 设定温度 ——
     * 这些在**不保存**时也会变（操作员切通道 / 改设定温度 / 探头温度漂移），纳入会把它们误当成新结果。
     */
    val contentSignature: String
        get() = "$judge|$savedTempC|$savedVoltageMv|$savedResistanceOhm"

    /** 原始寄存器（诊断页/降级解析用） */
    fun rawAt(addr: Int): Int? = raw.getOrNull(addr)?.let { it and 0xFFFF }

    companion object {
        fun parse(v: IntArray): DeviceSnapshot {
            fun r(i: Int): Int? = v.getOrNull(i)?.let { it and 0xFFFF }
            val unit = r(Registers.UNIT) ?: 0
            // 有结果块 = 帧里真的出现了 0x1F 及以上（降级帧 0x00~0x1E 共 31 个 → size=31，不含 0x1F）
            val hasBlock = v.size > Registers.UPLOAD_FLAG
            return DeviceSnapshot(
                raw = v,
                hasResultBlock = hasBlock,
                liveTempC = r(Registers.LIVE_TEMP)?.times(0.1),
                unit = unit,
                liveVoltageMv = r(Registers.LIVE_VOLTAGE)?.times(0.1),
                liveResistanceOhm = r(Registers.LIVE_RESISTANCE)?.times(0.1),
                channel = r(Registers.CHANNEL),
                deviceInfo = parseAscii(v, Registers.INFO_START, Registers.INFO_COUNT),
                targetTemp = r(Registers.TARGET_TEMP),
                tempLow = r(Registers.TEMP_LOW),
                tempHigh = r(Registers.TEMP_HIGH),
                voltageLimitMv = r(Registers.VOLT_LIMIT)?.times(0.1),
                resistanceLimitOhm = r(Registers.RES_LIMIT)?.times(0.1),
                uploadFlag = r(Registers.UPLOAD_FLAG),
                savedTempC = r(Registers.SAVED_TEMP),
                savedVoltageMv = r(Registers.SAVED_VOLTAGE)?.times(0.1),
                savedResistanceOhm = r(Registers.SAVED_RESISTANCE)?.times(0.1),
                judge = r(Registers.JUDGE)
            )
        }

        /** 寄存器按字节拼接（每寄存器高字节在前），按 ASCII 解析，截断尾部空白/0 */
        private fun parseAscii(v: IntArray, start: Int, count: Int): String? {
            val bytes = ByteArray(count * 2)
            var n = 0
            for (i in 0 until count) {
                val w = v.getOrNull(start + i)?.and(0xFFFF) ?: return null
                bytes[n++] = (w ushr 8).toByte()
                bytes[n++] = w.toByte()
            }
            var end = bytes.size
            while (end > 0) {
                val b = bytes[end - 1]
                if (b == 0.toByte() || b == 0x20.toByte() || b == 0xFF.toByte()) end-- else break
            }
            return if (end == 0) null else String(bytes, 0, end, Charsets.US_ASCII).trim()
        }
    }
}
