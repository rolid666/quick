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
    fun `两个 64 字节整包 —— 第二包开头的 01 60 也要剥掉`() {
        // 这条守的是「按包长步进」这个写法本身（与 usb-serial-for-android 的 readFilter 逐句等价）。
        // 注意：一次 bulkTransfer 要装下两个整包，读缓冲必须大于一个包长；
        // 本驱动现在只读 packetSize（=64），走不到这条路径 —— 所以这是**保险**，不是修 bug。
        val a = ByteArray(62) { 0x41 }        // 'A'
        val b = ByteArray(62) { 0x42 }        // 'B'
        val two = byteArrayOf(0x01, 0x60) + a + byteArrayOf(0x01, 0x60) + b
        assertEquals(128, two.size)

        val data = FtdiSerialPort.stripStatusBytes(two)
        assertEquals(124, data.size)
        assertEquals('A'.code, data[0].toInt())
        assertEquals('A'.code, data[61].toInt())
        assertEquals("第二包的数据要紧接着第一包，中间不能夹 01 60", 'B'.code, data[62].toInt())
        assertEquals('B'.code, data[123].toInt())
        assertFalse("结果里不该再出现状态字节", data.any { it == 0x01.toByte() || it == 0x60.toByte() })
    }

    @Test
    fun `按连接自己的包长剥（不是写死 64）`() {
        // 纯算术检查：换包长时边界要跟着走
        val p = byteArrayOf(0x01, 0x60) + ByteArray(14) { 0x44 }   // 'D'
        val three = p + p + p
        assertEquals(48, three.size)

        assertEquals(42, FtdiSerialPort.stripStatusBytes(three, packetSize = 16).size)
        assertTrue(FtdiSerialPort.stripStatusBytes(three, packetSize = 16).all { it == 0x44.toByte() })
        // 包长给错（当成 64）时只剥掉开头 2 字节 —— 这条断言固定「包长必须给对」这件事
        assertEquals(46, FtdiSerialPort.stripStatusBytes(three, packetSize = 64).size)
    }

    @Test
    fun `包长给成 0 或负数 —— 退回 64，不能死循环`() {
        val a = ByteArray(62) { 0x41 }
        val two = byteArrayOf(0x01, 0x60) + a + byteArrayOf(0x01, 0x60) + a
        assertEquals(124, FtdiSerialPort.stripStatusBytes(two, packetSize = 0).size)
        assertEquals(124, FtdiSerialPort.stripStatusBytes(two, packetSize = -8).size)
    }

    @Test
    fun `尾包不足 2 字节的余量 —— 跳过，不当数据（库在这里会抛异常，我们选择跳过）`() {
        // bulkTransfer 正常返回整包，这个余量只可能出现在异常截断时；
        // 不足 2 字节就不可能是「数据」，丢掉比当成数据安全
        val a = ByteArray(62) { 0x41 }
        val tail = byteArrayOf(0x01, 0x60) + a + byteArrayOf(0x77)
        assertEquals(62, FtdiSerialPort.stripStatusBytes(tail).size)
    }

    @Test
    fun `8N2 的 wValue = 0x1008`() {
        // 位域：0–7 数据位（8）| 8–10 校验（0=无）| 11–13 停止位（2=两位）
        // ⚠ 2026-09-23 修：之前写的是 8 or (2 shl 8) = 0x0208 —— 停止位放错到校验位，
        // 实际配成了「8 数据位 + 偶校验 + 1 停止位」。这个测试当时把错的固定成「正确」了。
        // 依据：FTDI 的 SET_DATA_REQUEST 位域，以及 usb-serial-for-android 的
        // setParameters()：无校验不加位、奇 0x100 / 偶 0x200、两位停止位 |= 0x1000。
        assertEquals(2 shl 11, FtdiSerialPort.DATA_8N2 and (7 shl 11))
        assertEquals(0, FtdiSerialPort.DATA_8N2 and (7 shl 8))
        assertEquals(0x1008, FtdiSerialPort.DATA_8N2)
    }
}
