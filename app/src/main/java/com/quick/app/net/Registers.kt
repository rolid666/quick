package com.quick.app.net

/**
 * 191AF+ 保持寄存器定义 —— **实机实测修正版（2026-09）**。
 *
 * 厂家《191AF 系列通讯协议 V1.1》多处与实机不符，地址语义以实测为准
 * （对照表见 README「寄存器对照」/ docs/可行性分析与通讯规格-V1.0.md §18）。
 * 实测来源逐条标注；仍未测出的项标注「待实测」，不做臆测。
 */
object Registers {
    const val TEMP = 0x00          // 实时温度, ℃, ×0.1（实测）
    const val UNIT = 0x01          // 温度单位 0=℃ 1=℉（实测）
    const val LEAKAGE = 0x02       // 漏地电压实时值, mV, ×0.1（实测：67 = 6.7 mV）
    const val AUTO_OFF = 0x03      // 自动关机时间, 分钟（实测）
    const val JUDGE = 0x04         // 判定结果：0=NG，非 0=OK（实测；实测 OK 值见过 1 与 2）
    const val INFO_START = 0x0A    // 设备信息字符串, 32 字节 ASCII（实测：设备显示字符串，作设备标识）
    const val INFO_COUNT = 16      // 0x0A~0x19 = 16 寄存器 = 32 字节
    const val UNKNOWN_1A = 0x1A    // 待实测（文档标"设定温度"，实测不符；暂不使用）
    const val TARGET_TEMP = 0x1B   // 设定温度目标值, ℃, ×1（实测）
    const val TEMP_LOW = 0x1C      // 温度下限, ℃（实测；文档标"测试上传标志"）
    const val TEMP_HIGH = 0x1D     // 温度上限, ℃（实测；文档标"保存温度"）
    const val SAVE_FLAG = 0x1E     // 结果保存标志：未按=0；按下=0x0014(20)，保持一段时间后自清（实测）

    /** 寄存器总量：0x00~0x1E 共 31 个，可一次读全表 */
    const val TOTAL = 0x1F

    const val DEFAULT_PORT = 502
    const val DEFAULT_UNIT_ID = 1
}

/** 一次全读（0x00 起 TOTAL 个）后解析出的仪器快照（同一响应帧，字段间天然原子） */
data class DeviceSnapshot(
    val raw: IntArray,
    val liveTempC: Double?,   // 0x00 ×0.1 ℃
    val unit: Int,            // 0x01（缺省按 0=℃ 处理）
    val leakageMv: Double?,   // 0x02 ×0.1 mV
    val autoOffMin: Int?,     // 0x03 分钟
    val judgedOk: Boolean?,   // 0x04：0=NG，非 0=OK
    val deviceInfo: String?,  // 0x0A~0x19 ASCII 设备显示字符串
    val targetTemp: Int?,     // 0x1B ℃
    val tempLow: Int?,        // 0x1C ℃
    val tempHigh: Int?,       // 0x1D ℃
    val saveFlagRaw: Int?,    // 0x1E 原始值（诊断显示用）
) {
    /** 0x1E 非 0 = 仪器上刚按过保存（保持时长未实测，读取侧用「值变化」去重） */
    val hasSaveFlag: Boolean get() = (saveFlagRaw ?: 0) != 0

    /**
     * 结果内容签名（判定 + 目标温度 + 上下限）—— 只取「按保存才会变」的字段。
     * 用途：0x1E 保持期内操作员又按了一次保存时，标志不会回 0，仅靠值变化会漏掉第二笔
     * （典型：先 NG，随即复测 OK）；此时判定/目标若已改变，说明确有新结果。
     * 不含实时温度与漏地电压：两者持续变化，纳入会令保持期内反复触发。
     */
    val contentSignature: String get() = "$judgedOk|$targetTemp|$tempLow|$tempHigh"

    companion object {
        fun parse(v: IntArray): DeviceSnapshot {
            fun r(i: Int): Int? = v.getOrNull(i)?.let { it and 0xFFFF }
            val unit = r(Registers.UNIT) ?: 0
            return DeviceSnapshot(
                raw = v,
                liveTempC = r(Registers.TEMP)?.times(0.1),
                unit = unit,
                leakageMv = r(Registers.LEAKAGE)?.times(0.1),
                autoOffMin = r(Registers.AUTO_OFF),
                judgedOk = r(Registers.JUDGE)?.let { it != 0 },
                deviceInfo = parseAscii(v, Registers.INFO_START, Registers.INFO_COUNT),
                targetTemp = r(Registers.TARGET_TEMP),
                tempLow = r(Registers.TEMP_LOW),
                tempHigh = r(Registers.TEMP_HIGH),
                saveFlagRaw = r(Registers.SAVE_FLAG)
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
