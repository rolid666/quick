package com.quick.app.torque

/**
 * 扫到的二维码/条码内容 → 设备信息名称（纯逻辑，单测覆盖）。
 *
 * 为什么不是「扫到啥就存啥」：本应用的二维码本来就有一套约定 ——
 * 厂家《扫码配置指令》里设备编号是 `SN=QK-HT-001`，多指令时用英文逗号连接
 * （如 `SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370`）。
 * 扫到这种复合内容时，操作员要的是**编号本身**，不是整串指令。
 *
 * 规则：
 * 1. 内容里含 `=`（说明是一条指令串）→ **只认 `SN=` 的值**（不分大小写，取到下一个逗号为止）；
 *    指令串里没有 SN（或 SN 是空的）→ null —— 绝不能把 `TSET=350,TMIN=330` 整串存成设备名；
 * 2. 不含 `=` → 整串就是名字（纯编号/条码标签都走这条），内部空白折成一个空格；
 * 3. 空内容 → null（调用方据此不新增）。
 */
object TorqueScan {

    private const val MAX_LEN = 64

    fun extractName(payload: String?): String? {
        val raw = payload?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // 指令串（含 '='）里只有 SN 的值能当名字；普通标签则整串都是名字
        val name = (if (raw.contains('=')) snValue(raw) else raw)
            ?.replace(Regex("\\s+"), " ")?.trim()
        if (name.isNullOrEmpty()) return null
        return if (name.length > MAX_LEN) name.substring(0, MAX_LEN) else name
    }

    /** 从 `SN=xxx,...` 里取 `xxx`；没有 SN 项则返回 null */
    private fun snValue(text: String): String? {
        val idx = text.indexOf("SN=", ignoreCase = true)
        if (idx < 0) return null
        val rest = text.substring(idx + 3)
        val end = rest.indexOf(',')
        val v = (if (end >= 0) rest.substring(0, end) else rest).trim()
        return v.ifEmpty { null }
    }
}
