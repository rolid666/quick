package com.quick.app.net

/**
 * 191AF+ 保持寄存器定义 —— 全部来自厂家《191AF 系列通讯协议 V1.1》。
 * 不做任何文档之外的假设：地址、缩放、读写性均为厂家文档明确内容。
 */
object Registers {
    const val TEMP = 0x00          // 实时温度, ℃, 系数 0.1 → 值*0.1, R
    const val UNIT = 0x01          // 温度单位 0=℃ 1=℉, R/W
    const val SAVE_MODE = 0x02     // 结果保存 0=手动 1=自动, R/W
    const val AUTO_OFF = 0x03      // 自动关机 0/5/10/15 分钟(0=不关机), R/W
    const val INFO_START = 0x0A    // 设备信息(扫码 SN), 字符串按字节解析, R
    const val INFO_COUNT = 16      // 0x0A~0x19 = 16 寄存器 = 32 字节
    const val SET_TEMP = 0x1A      // 设定温度(扫码 TSET), ℃, 系数 1, R
    const val TOLERANCE = 0x1B     // 误差范围(扫码 TERR), ℃(±), 系数 1, R
    const val RESULT_FLAG = 0x1C   // 测试上传标志 0=无效 1=保存按钮按下, 读取后自动清零, R
    const val SAVED_TEMP = 0x1D    // 测试保存温度, ℃, 系数 1, 标志为 1 时有效, R
    const val JUDGE = 0x1E         // 测试合格判定 0=NG 1=OK, 标志为 1 时有效, R

    /** 寄存器总量：示例文档明示"寄存器总数为 001FH"，地址 0x00~0x1E，可一次读全表 */
    const val TOTAL = 0x1F

    const val DEFAULT_PORT = 502
    const val DEFAULT_UNIT_ID = 1
}

/** 一次全读（0x00 起 TOTAL 个）后解析出的仪器快照 */
data class DeviceSnapshot(
    val raw: IntArray,
    val liveTempC: Double?,   // 0x00 ×0.1
    val unit: Int,            // 0x01（缺省按 0=℃ 处理）
    val saveMode: Int?,       // 0x02
    val autoOffMin: Int?,     // 0x03
    val deviceSn: String?,    // 0x0A~0x19 按字节 ASCII
    val setTemp: Int?,        // 0x1A ℃ 整数
    val tolerance: Int?,      // 0x1B ±℃ 整数
    val hasResult: Boolean,   // 0x1C == 1
    val savedTemp: Int?,      // 0x1D ℃ 整数（系数 1，与 0x00 的 0.1 系数严格区分）
    val judgedOk: Boolean?    // 0x1E true=OK false=NG
) {
    companion object {
        fun parse(v: IntArray): DeviceSnapshot {
            fun r(i: Int): Int? = v.getOrNull(i)?.let { it and 0xFFFF }
            val unit = r(Registers.UNIT) ?: 0
            return DeviceSnapshot(
                raw = v,
                liveTempC = r(Registers.TEMP)?.times(0.1),
                unit = unit,
                saveMode = r(Registers.SAVE_MODE),
                autoOffMin = r(Registers.AUTO_OFF),
                deviceSn = parseAscii(v, Registers.INFO_START, Registers.INFO_COUNT),
                setTemp = r(Registers.SET_TEMP),
                tolerance = r(Registers.TOLERANCE),
                hasResult = r(Registers.RESULT_FLAG) == 1,
                savedTemp = r(Registers.SAVED_TEMP),
                judgedOk = r(Registers.JUDGE)?.let { it == 1 }
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
