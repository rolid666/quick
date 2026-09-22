package com.quick.app.torque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扭力计解析测试 —— 全部围绕**实测确认的真实格式** `+5.40kgf*cm`（用户 2026-09-22 截图）。
 *
 * 这些用例钉的是「现场会怎么坏」：设备不发换行符、两块数据挤在一次读里、
 * 数值被切在两个包之间、单位写法变体、开机横幅混进来。
 */
class TorqueParserTest {

    private val p = TorqueStreamParser()

    private fun feed(s: String) = p.feed(s.toByteArray(Charsets.ISO_8859_1))

    @Test
    fun `实测格式 一笔就是一串 ASCII`() {
        val rs = feed("+5.40kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(5.40, rs[0].value, 1e-9)
        assertEquals("kgf*cm", rs[0].unit)
        assertEquals("+5.40kgf*cm", rs[0].rawLine)
    }

    @Test
    fun `串口助手里那种带空格的写法也认`() {
        val rs = feed("+ 5.40 kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(5.40, rs[0].value, 1e-9)
        assertEquals("kgf*cm", rs[0].unit)
    }

    @Test
    fun `没有换行符也能解析 且不丢最后一笔`() {
        // 设备实测就是不发送换行；旧实现按行分割会把整串当一行
        val rs = feed("+5.40kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(0, p.pendingBytes)
    }

    @Test
    fun `一次读到两笔 要认出两笔`() {
        val rs = feed("+5.40kgf*cm+6.23kgf*cm")
        assertEquals(2, rs.size)
        assertEquals(5.40, rs[0].value, 1e-9)
        assertEquals(6.23, rs[1].value, 1e-9)
        assertEquals(0, p.pendingBytes)
    }

    @Test
    fun `一笔被切成两个包 拼起来后才认`() {
        assertEquals(0, feed("+5.4").size)
        val rs = feed("0kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(5.40, rs[0].value, 1e-9)
    }

    @Test
    fun `CRLF 分割的连续三笔`() {
        val rs = feed("+5.40kgf*cm\r\n+6.23kgf*cm\r\n+3.38kgf*cm\r\n")
        assertEquals(3, rs.size)
        assertEquals(listOf(5.40, 6.23, 3.38), rs.map { it.value })
        assertEquals(0, p.pendingBytes)   // 空白/控制字节要跟着被清掉
    }

    @Test
    fun `负值带符号`() {
        val rs = feed("-2.05kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(-2.05, rs[0].value, 1e-9)
    }

    @Test
    fun `单位写法变体归一化`() {
        assertEquals("kgf*cm", TorqueStreamParser.normalizeUnit("kgf.cm"))
        assertEquals("kgf*cm", TorqueStreamParser.normalizeUnit("KGF-CM"))
        assertEquals("kgf*cm", TorqueStreamParser.normalizeUnit("kgf*cm"))
        assertEquals("N*m", TorqueStreamParser.normalizeUnit("N.m"))
        assertEquals("cN*m", TorqueStreamParser.normalizeUnit("cn*m"))
        assertEquals("mN*m", TorqueStreamParser.normalizeUnit("mN*m"))
        assertEquals("lbf*in", TorqueStreamParser.normalizeUnit("lbf-in"))
    }

    @Test
    fun `N m 与 mN m 不会互相切错`() {
        val rs = feed("12.5 mN*m")
        assertEquals(1, rs.size)
        assertEquals(12.5, rs[0].value, 1e-9)
        assertEquals("mN*m", rs[0].unit)
    }

    @Test
    fun `开机横幅不会被当成读数 并且留在缓冲里如实报告`() {
        val rs = feed("TORQUE METER V1.2 READY")
        assertEquals(0, rs.size)
        assertTrue(p.pendingBytes > 0)
        assertEquals("TORQUE METER V1.2 READY", p.pendingText())
    }

    @Test
    fun `横幅后面跟的读数照样能认出来`() {
        assertEquals(0, feed("READY").size)
        val rs = feed("+1.00kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(1.00, rs[0].value, 1e-9)
    }

    @Test
    fun `原始字节 hex 与原文一一对应`() {
        val rs = feed("+5.40kgf*cm")
        assertEquals("2B 35 2E 34 30 6B 67 66 2A 63 6D", rs[0].rawHex)
    }

    @Test
    fun `缓冲超过上限时丢最旧的一半并如实计数`() {
        val small = TorqueStreamParser(maxBufferBytes = 64)
        val noise = "X".repeat(200)
        val rs = small.feed(noise.toByteArray(Charsets.ISO_8859_1))
        assertEquals(0, rs.size)
        assertTrue(small.droppedBytes > 0)
        assertTrue(small.pendingBytes <= 64)
    }

    @Test
    fun `reset 清空缓冲 旧残留不会串到新数据上`() {
        feed("+5.4")
        assertTrue(p.pendingBytes > 0)
        p.reset()
        assertEquals(0, p.pendingBytes)
        // 关键：重连/换设备后，那半个旧数不能跟新数据拼成 5.40 —— 只认新来的 0.00
        val rs = feed("0kgf*cm")
        assertEquals(1, rs.size)
        assertEquals(0.0, rs[0].value, 1e-9)
    }

    @Test
    fun `printable 把控制字符转义出来`() {
        // 控制字符与非 ASCII 字节转成 \xHH（大写）；CR/LF 用 \r \n
        assertEquals("\\x01\\x7F", TorqueStreamParser.printable(byteArrayOf(0x01, 0x7F)))
        assertEquals("\\x80", TorqueStreamParser.printable(byteArrayOf(0x80.toByte())))
        assertEquals("A\\r\\n", TorqueStreamParser.printable("A\r\n".toByteArray(Charsets.ISO_8859_1)))
        // 可打印 ASCII 原样输出 —— 诊断页看到的就该是设备发来的那句原文
        assertEquals("+5.40kgf*cm",
            TorqueStreamParser.printable("+5.40kgf*cm".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun `不是读数的数字串不会被误认`() {
        // 没有单位 → 一笔都不认（诊断页会看到缓冲堆着这几个字节，一眼知道格式变了）
        assertEquals(0, feed("12345").size)
        assertTrue(p.pendingBytes > 0)
    }
}
