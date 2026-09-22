package com.quick.app.torque.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FTDI 波特率分频编码 + 状态字节剥离 —— **扭力计能不能通信，全押在这两个纯函数上**。
 *
 * 分频值写错 = 一个字节都收不到（而且是静默的，界面上只显示「已连接」）：
 * 明天真机连不上的第一嫌疑就是这里。所以 KNOWN 表逐档钉死，19200 单列出来重点标。
 *
 * 依据 FTDI AN_232R-01：FT232R 一族基准时钟 3 MHz，bit0..13 = 整数部分，
 * bit14..15 = 小数编码 {0, 0.125, 0.25, 0.5}。
 */
class FtdiBaudTest {

    @Test
    fun `19200 —— 扭力计手册写死的档位，必须是 0x809C`() {
        // 3000000 / 19200 = 156.25 → 整数 156 = 0x9C，小数 0.25 → 编码 2 → 0x9C | (2 shl 14)
        assertEquals(0x809C, FtdiBaud.divisorFor(19200))
        assertTrue("19200 是精确档（3MHz 能被它整除到 0.25 档）", FtdiBaud.isExact(19200))
        assertEquals(19200.0, FtdiBaud.actualBaud(19200), 1e-9)
    }

    @Test
    fun `KNOWN 表逐个核对 —— 与 FTDI 应用笔记的常见档位一致`() {
        for ((baud, expected) in FtdiBaud.KNOWN) {
            assertEquals("波特率 $baud", expected, FtdiBaud.divisorFor(baud))
        }
    }

    @Test
    fun `小数编码四档都能取到`() {
        // 3000000/9600 = 312.5   → 小数 0.5   → 编码 3
        assertEquals(3, (FtdiBaud.divisorFor(9600) ushr 14) and 0x03)
        // 3000000/19200 = 156.25 → 小数 0.25  → 编码 2
        assertEquals(2, (FtdiBaud.divisorFor(19200) ushr 14) and 0x03)
        // 3000000/38400 = 78.125 → 小数 0.125 → 编码 1
        assertEquals(1, (FtdiBaud.divisorFor(38400) ushr 14) and 0x03)
        // 3000000/115200 = 26.04 → 小数 < 0.125 → 编码 0
        assertEquals(0, (FtdiBaud.divisorFor(115200) ushr 14) and 0x03)
    }

    @Test
    fun `整数部分占低 14 位 —— 低位不会被小数编码撞掉`() {
        assertEquals(156, FtdiBaud.divisorFor(19200) and 0x3FFF)
        assertEquals(312, FtdiBaud.divisorFor(9600) and 0x3FFF)
        assertEquals(26, FtdiBaud.divisorFor(115200) and 0x3FFF)
    }

    @Test
    fun `偏差只向「更快」的一侧 —— 不会把设备拖慢`() {
        // 小数向下取整 ⇒ 分母 ≤ 真实值 ⇒ 实际波特率 ≥ 请求值
        assertTrue(FtdiBaud.actualBaud(57600) >= 57600.0)
        assertTrue(FtdiBaud.actualBaud(230400) >= 230400.0)
        // 短报文（几十字节）对这点偏差不敏感，但不该离谱
        assertTrue(FtdiBaud.actualBaud(57600) < 57600.0 * 1.05)
    }

    @Test
    fun `非精确档能被识别出来（诊断页要显示「近似 + 实际值」）`() {
        assertTrue("9600 → 312.5，小数正好 0.5", FtdiBaud.isExact(9600))
        assertTrue("19200 → 156.25，小数正好 0.25", FtdiBaud.isExact(19200))
        assertFalse("115200 → 26.0417，落在两档之间", FtdiBaud.isExact(115200))
        assertFalse("57600 → 52.0833，落在两档之间", FtdiBaud.isExact(57600))
    }

    // ---------- 状态字节剥离 ----------

    @Test
    fun `剥掉每包开头的 2 个状态字节`() {
        // 真实场景：一包 = 2 状态字节 + 数据。不剥的话每隔 62 字节就混进 0x01 0x60
        val packet = byteArrayOf(0x01, 0x60, 0x31, 0x2E, 0x32, 0x33, 0x34)
        val data = FtdiSerialPort.stripStatusBytes(packet)
        assertEquals(5, data.size)
        assertEquals("1.234", String(data, Charsets.US_ASCII))
    }

    @Test
    fun `只有状态没有数据的包 —— 返回空，不当成数据`() {
        assertEquals(0, FtdiSerialPort.stripStatusBytes(byteArrayOf(0x01, 0x60)).size)
        assertEquals(0, FtdiSerialPort.stripStatusBytes(ByteArray(0)).size)
        assertEquals(0, FtdiSerialPort.stripStatusBytes(byteArrayOf(0x01)).size)
    }

    @Test
    fun `恰好 3 字节的包 —— 只剩 1 个有效字节`() {
        val data = FtdiSerialPort.stripStatusBytes(byteArrayOf(0x01, 0x60, 0x0A))
        assertEquals(1, data.size)
        assertEquals(0x0A, data[0].toInt())
    }

    @Test
    fun `8N2 的 wValue = 0x0208`() {
        // 8 数据位 | 2 停止位 << 8 | 无校验 << 11
        assertEquals(0x0208, FtdiSerialPort.DATA_8N2)
    }
}
