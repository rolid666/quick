package com.quick.app.torque

import org.junit.Assert.assertArrayEquals
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
    fun `坏字节严格解码要返回 null —— 不能靠替换字符装作解开了`() {
        // 0xE4 是 3 字节序列的开头，后面缺两个续字节 → 非法
        assertEquals(null, TorqueStreamParser.utf8OrNull(byteArrayOf(0xE4.toByte())))
        // 0xFF 在 UTF-8 里永远非法
        assertEquals(null, TorqueStreamParser.utf8OrNull(byteArrayOf(0xFF.toByte(), 0x20, 0x41)))
        assertEquals(null, TorqueStreamParser.droppedTextOrNull(byteArrayOf(0xE4.toByte())))
        // 纯 ASCII 的噪声能解通，但不该被当成「设备在发文字」
        assertEquals(null, TorqueStreamParser.droppedTextOrNull(byteArrayOf(0x01, 0x7F)))
    }

    @Test
    fun `不是读数的数字串不会被误认`() {
        // 没有单位 → 一笔都不认（诊断页会看到缓冲堆着这几个字节，一眼知道格式变了）
        assertEquals(0, feed("12345").size)
        assertTrue(p.pendingBytes > 0)
    }

    // ---------- 协议字符白名单（用户 2026-09-25 定：只留 + 5.40 kgf*cm 这类读数）----------

    /** 现场那串：读数后面跟着 `\x18 \x0E \x0F`（用户 2026-09-25 反馈的原始现象） */
    private fun wireWithNoise(): ByteArray =
        "+ 5.40 kgf*cm".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x18, 0x0E, 0x0F)

    @Test
    fun `白名单只留协议字符 —— 18 0E 0F 这类噪声一个都不放进去`() {
        val wire = wireWithNoise()
        val clean = TorqueStreamParser.keepProtocol(wire)
        assertEquals("+ 5.40 kgf*cm", String(clean, Charsets.ISO_8859_1))
        assertEquals(3, wire.size - clean.size)
        // 滤掉的字节要能单独拿出来（诊断页显示「滤了什么」靠它）
        assertArrayEquals(byteArrayOf(0x18, 0x0E, 0x0F), TorqueStreamParser.notProtocol(wire))
        assertEquals(null, TorqueStreamParser.droppedTextOrNull(wire))
    }

    @Test
    fun `噪声夹在读数中间 —— 滤掉之后照样认得出 不过滤就整笔丢掉`() {
        val ks = "+ 5.40 k".toByteArray(Charsets.ISO_8859_1)
        val gfcm = "gf*cm".toByteArray(Charsets.ISO_8859_1)
        val wire = ks + byteArrayOf(0x18, 0x0E, 0x0F) + gfcm

        // 对照：不过滤的话 `k\x18\x0E\x0Fgf` 把单位切碎了，整笔读数认不出来、还堆在缓冲里
        val raw = TorqueStreamParser()
        assertEquals(0, raw.feed(wire).size)
        assertTrue(raw.pendingBytes > 0)

        // 过滤后：读数认得出，缓冲不残留
        val rs = p.feed(TorqueStreamParser.keepProtocol(wire))
        assertEquals(1, rs.size)
        assertEquals(5.40, rs[0].value, 1e-9)
        assertEquals(0, p.pendingBytes)
    }

    @Test
    fun `换行符在白名单里 —— 设备真发 CRLF 也不会被当成噪声滤掉`() {
        val wire = "+5.40kgf*cm\r\n".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x18)
        assertEquals("+5.40kgf*cm\r\n",
            String(TorqueStreamParser.keepProtocol(wire), Charsets.ISO_8859_1))
    }

    @Test
    fun `中间点 · 在白名单里 —— kgf·cm 这种写法不能因为过滤而失配`() {
        // ISO-8859-1 的 ·（0xB7）：必须留着，否则一种本来认得的单位写法突然失配
        assertEquals("+5.40kgf·cm",
            String(TorqueStreamParser.keepProtocol("+5.40kgf·cm".toByteArray(Charsets.ISO_8859_1)),
                Charsets.ISO_8859_1))
        val rs = p.feed(TorqueStreamParser.keepProtocol("+5.40kgf·cm".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(1, rs.size)
        assertEquals("kgf*cm", rs[0].unit)
        // 设备若发 UTF-8 的 ·（C2 B7）：C2 被当噪声滤掉、B7 留下 —— 反而认得出来
        val utf8 = TorqueStreamParser.keepProtocol("+5.40kgf·cm".toByteArray(Charsets.UTF_8))
        assertEquals(1, p.feed(utf8).size)
    }

    @Test
    fun `整包都是噪声 —— 过滤后为空 解析器什么也拿不到`() {
        val wire = byteArrayOf(0x18, 0x0E, 0x0F, 0x00, 0xFF.toByte())
        val clean = TorqueStreamParser.keepProtocol(wire)
        assertEquals(0, clean.size)
        assertEquals(0, p.feed(clean).size)
        assertEquals(0, p.pendingBytes)
        assertEquals(5, wire.size - clean.size)
    }

    @Test
    fun `滤掉的字节若能严格解成文字 说明设备换了说法 不是链路噪声`() {
        val cn = "扭力值".toByteArray(Charsets.UTF_8)
        assertEquals("设备发的合法 UTF-8 要能解出来", "扭力值", TorqueStreamParser.utf8OrNull(cn))
        assertTrue(TorqueStreamParser.hasNonAscii("扭力值"))
        // 读数后面跟着一句中文 → 滤掉的正是这句话，诊断页要把它显出来（别把「设备说话」当「线路坏」）
        val wire = "+5.40kgf*cm".toByteArray(Charsets.ISO_8859_1) + cn
        assertEquals("扭力值", TorqueStreamParser.droppedTextOrNull(wire))
    }

    // ---------- 只记正数（用户 2026-09-26）----------

    /** 直接按解析结果判：`+5.40kgf*cm` → 5.40，`-0.05kgf*cm` → -0.05 */
    private fun recordable(text: String): Boolean {
        val rs = p.feed(text.toByteArray(Charsets.ISO_8859_1))
        assertEquals("这段应该能解析出一笔：$text", 1, rs.size)
        return rs[0].isRecordableTorque()
    }

    @Test
    fun `正数读数照常记录`() {
        assertTrue(recordable("+5.40kgf*cm"))
        assertTrue("0.01 也是正数", recordable("+0.01kgf*cm"))
    }

    @Test
    fun `负数读数跳过 反扭松与清除键的输出都算`() {
        assertTrue(!recordable("-0.05kgf*cm"))
        assertTrue(!recordable("-5.40kgf*cm"))
    }

    @Test
    fun `0 也跳过 —— 真正的拧紧测不出零扭力 而 0 混进平均会把一组判成 NG`() {
        assertTrue(!recordable("+0.00kgf*cm"))
        assertTrue(!recordable("-0.00kgf*cm"))   // 负零按数值比也是 0
        assertTrue(!recordable("0.00kgf*cm"))    // 没有符号的 0
    }
}
