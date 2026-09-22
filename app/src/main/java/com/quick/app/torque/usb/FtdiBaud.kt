package com.quick.app.torque.usb

import kotlin.math.floor

/**
 * FTDI 芯片的波特率分频编码（**纯计算，无 Android 依赖**，单测覆盖）。
 *
 * 依据 FTDI 应用笔记 AN_232R-01《Baud Rate Generator》：
 * - FT232R / FT230X / FT231X 内部基准时钟 **3 MHz**，`分频值 = 3000000 / 波特率`；
 * - 分频值写进「Set Baud Rate」命令的 wValue（16 位）；
 * - 16 位里 **bit0..13 = 整数部分**，**bit14..15 = 小数部分**，小数只能取
 *   `0 / 0.125 / 0.25 / 0.5` 四档（编码 00 / 01 / 10 / 11）。
 *
 * 扭力计固定 **19200**：`3000000 / 19200 = 156.25` → 整数 156 = 0x9C，
 * 小数 0.25 → 编码 2 → `0x9C | (2 << 14)` = **0x809C**。
 * 其余档位由同一条规则算出，与 AN_232R-01 表里的常见值一致（单测逐个钉死）：
 * 9600 → 0xC138、38400 → 0x404E、115200 → 0x001A。
 *
 * ⚠️ FT232H / FT2232H / FT4232H 是**另一套编码**（12 MHz 基准 + 3 位小数），
 * 本机扭力计手册写的是「FTDI Chips Virtual COM Port Drivers」+ 19200，
 * 现场几乎必定是 FT232R 一族；若明天实测发现 PID 是 0x6010/0x6011/0x6014，
 * 这里要按对应芯片的公式再补一条分支（诊断页会直接把 PID 打出来）。
 */
object FtdiBaud {

    /** FT232R 一族的分频基准时钟 */
    const val XTAL_232R = 3_000_000

    /** 小数部分编码（2 位）：0 / 0.125 / 0.25 / 0.5 */
    private val FRACTIONS = doubleArrayOf(0.0, 0.125, 0.25, 0.5)

    /**
     * 波特率 → 写进 wValue 的 16 位分频值。
     *
     * 小数取「不超过实际需要」的那一档（向下取整），于是实际波特率 **≥ 请求值**、
     * 误差最大不超过 0.5 个分频刻度 —— ASCII 短报文（几十字节）对这点误差完全不敏感。
     */
    fun divisorFor(baud: Int): Int {
        require(baud > 0) { "波特率必须为正数：$baud" }
        val exact = XTAL_232R.toDouble() / baud
        val whole = floor(exact).toInt()
        val frac = exact - whole
        var code = 0
        for (i in FRACTIONS.indices) if (frac >= FRACTIONS[i]) code = i
        return (whole and 0x3FFF) or (code shl 14)
    }

    /** 小数位是否被精确表达（19200 是精确的；用它来判断「设备真的跑在请求的波特率上」） */
    fun isExact(baud: Int): Boolean {
        val exact = XTAL_232R.toDouble() / baud
        val frac = exact - floor(exact)
        return FRACTIONS.any { kotlin.math.abs(it - frac) < 1e-9 }
    }

    /** 实际达成的波特率（诊断页显示用：让人一眼看出偏差有多大） */
    fun actualBaud(baud: Int): Double {
        val d = divisorFor(baud)
        val whole = d and 0x3FFF
        val frac = FRACTIONS[(d ushr 14) and 0x03]
        return XTAL_232R / (whole + frac)
    }

    /** 常用档位 → 期望的分频值（单测用；也是给未来的人一张对照表） */
    val KNOWN: Map<Int, Int> = mapOf(
        300 to 0x2710, 600 to 0x1388, 1200 to 0x09C4, 2400 to 0x04E2, 4800 to 0x0271,
        9600 to 0xC138, 19200 to 0x809C, 38400 to 0x404E, 57600 to 0x0034, 115200 to 0x001A,
        230400 to 0x000D
    )
}
