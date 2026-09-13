package com.quick.app.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * 帧编解码与快照解析 —— 用厂家《191AF/192AF 测试仪通讯示例》给出的
 * 真实报文结构与示例值验证（无真机时先锁定协议实现）。
 */
class ModbusTcpClientTest {

    /** 极简假仪器：监听本机随机端口，按请求回响应 */
    private class MockDevice(val onRequest: (request: ByteArray, tx: Int, unit: Int) -> ByteArray) {
        val server = ServerSocket(0)
        val port: Int get() = server.localPort
        private val pool = Executors.newSingleThreadExecutor()
        val requests = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())

        fun start() {
            pool.execute {
                while (!server.isClosed) {
                    try {
                        val sock = server.accept()
                        handle(sock)
                        sock.close()
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        private fun handle(sock: Socket) {
            val inp = DataInputStream(sock.getInputStream())
            val out = DataOutputStream(sock.getOutputStream())
            while (true) {
                val head = ByteArray(7)
                inp.readFully(head) // 阻塞至连接关闭抛异常
                val tx = ((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)
                val unit = head[6].toInt() and 0xFF
                val len = ((head[4].toInt() and 0xFF) shl 8) or (head[5].toInt() and 0xFF)
                val pdu = ByteArray(len - 1)
                inp.readFully(pdu)
                val full = head + pdu
                requests += full
                val resp = onRequest(full, tx, unit)
                out.write(resp)
                out.flush()
            }
        }

        fun stop() {
            runCatching { server.close() }
            pool.shutdownNow()
        }
    }

    /** 组装 FC03 正常读响应（PDU：fc + 字节数 + 数据），MBAP Length = 1(unit) + PDU */
    private fun readResponse(tx: Int, unit: Int, regs: IntArray): ByteArray {
        val pduBody = ByteArray(2 + regs.size * 2)
        pduBody[0] = 0x03
        pduBody[1] = (regs.size * 2).toByte()
        regs.forEachIndexed { i, v ->
            pduBody[2 + i * 2] = (v ushr 8).toByte()
            pduBody[3 + i * 2] = v.toByte()
        }
        val pdu = pduBody
        val pduLen = pdu.size
        return byteArrayOf(
            (tx ushr 8).toByte(), tx.toByte(),
            0, 0,
            ((pduLen + 1) ushr 8).toByte(), (pduLen + 1).toByte(),
            unit.toByte()
        ) + pdu
    }

    @Test
    fun `全读请求字节与厂家报文格式一致`() {
        val dev = MockDevice { _, tx, _ ->
            // 应答同事务 ID
            readResponse(tx, 1, IntArray(Registers.TOTAL))
        }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 2000, 1000)
            client.readHoldingRegisters(0, Registers.TOTAL)
            val req = dev.requests.first()
            // 厂家 TCP 示例: 00 01 00 00 00 06 01 03 00 00 00 0A（读10个）；全读为 0x001F
            // 首笔事务 ID = 0001（与厂家示例一致）
            val expected = byteArrayOf(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x1F
            )
            assertArrayEquals("请求帧应严格等于 MBAP(7) + FC03 PDU(5)", expected, req)
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `按实机实测映射解析寄存器`() {
        // 实机实测（2026-09，已取代厂家文档语义，见 Registers.kt）：
        // 0x00=0x00EA(234 → 23.4℃)、0x01=0(℃)、0x02=67(6.7mV)、0x03=15(min)、0x04=1(OK)、
        // 0x0A 起 "QK-HT-001"、0x1B=350(目标)、0x1C=280(下限)、0x1D=320(上限)、0x1E=0x14(刚按下保存)
        val regs = IntArray(Registers.TOTAL)
        regs[0x00] = 0x00EA
        regs[0x01] = 0
        regs[0x02] = 67
        regs[0x03] = 15
        regs[0x04] = 1
        val snBytes = "QK-HT-001".toByteArray(Charsets.US_ASCII)
        val pad = ByteArray(32 - snBytes.size)
        val snRegs = (snBytes + pad).toList().chunked(2).map { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
        snRegs.forEachIndexed { i, v -> regs[0x0A + i] = v }
        regs[0x1B] = 350
        regs[0x1C] = 280
        regs[0x1D] = 320
        regs[0x1E] = 0x14

        val dev = MockDevice { _, tx, unit -> readResponse(tx, unit, regs) }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 2000, 1000)
            val got = client.readHoldingRegisters(0, Registers.TOTAL)
            val snap = DeviceSnapshot.parse(got)
            assertEquals(31, got.size)
            assertEquals(23.4, snap.liveTempC!!, 0.001)   // 0x00 ×0.1
            assertEquals(0, snap.unit)
            assertEquals(6.7, snap.leakageMv!!, 0.001)    // 0x02 ×0.1 mV
            assertEquals(15, snap.autoOffMin)
            assertEquals(true, snap.judgedOk)             // 0x04 非 0 = OK
            assertEquals("QK-HT-001", snap.deviceInfo)    // 0x0A~0x19 ASCII
            assertEquals(350, snap.targetTemp)            // 0x1B ×1，不缩放
            assertEquals(280, snap.tempLow)
            assertEquals(320, snap.tempHigh)
            assertEquals(0x14, snap.saveFlagRaw)
            assertTrue(snap.hasSaveFlag)
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `判定 0x04 为 0 是 NG 非 0 是 OK`() {
        // 实测：0=NG；OK 实测见过 1 与 2 两种值，故一律按「非 0」处理，不写死具体数值
        for (v in intArrayOf(0, 1, 2)) {
            val regs = IntArray(Registers.TOTAL)
            regs[Registers.JUDGE] = v
            val snap = DeviceSnapshot.parse(regs)
            assertEquals("0x04=$v 时判定应为 ${v != 0}", v != 0, snap.judgedOk)
        }
    }

    @Test
    fun `保存标志未按下时不触发`() {
        val regs = IntArray(Registers.TOTAL)   // 全 0 = 仪器待机
        val snap = DeviceSnapshot.parse(regs)
        assertEquals(0, snap.saveFlagRaw)
        assertTrue(!snap.hasSaveFlag)
        assertEquals(null, snap.deviceInfo)    // 空字符串 → null，不占列表
    }

    @Test
    fun `内容签名只随判定与目标变化 不随实时温度或漏压变化`() {
        // 用途：0x1E 保持期内又按一次保存时标志不回 0，靠签名差异识别第二笔（如 NG 后复测 OK）；
        // 而实时温度与漏地电压持续变化，若纳入签名会导致保持期内反复误触发。
        fun snapOf(judge: Int, target: Int, live: Int, leak: Int): DeviceSnapshot {
            val regs = IntArray(Registers.TOTAL)
            regs[Registers.JUDGE] = judge
            regs[Registers.TARGET_TEMP] = target
            regs[Registers.TEMP] = live
            regs[Registers.LEAKAGE] = leak
            return DeviceSnapshot.parse(regs)
        }
        val base = snapOf(0, 350, 218, 12)
        assertEquals(base.contentSignature, snapOf(0, 350, 3510, 99).contentSignature)
        assertNotEquals(base.contentSignature, snapOf(1, 350, 218, 12).contentSignature)
        assertNotEquals(base.contentSignature, snapOf(0, 360, 218, 12).contentSignature)
    }

    @Test
    fun `异常应答抛出 ModbusException`() {
        val dev = MockDevice { req, tx, unit ->
            // 非法地址: FC 0x83 + code 0x02
            byteArrayOf(
                (tx ushr 8).toByte(), tx.toByte(), 0, 0, 0, 3, unit.toByte(), 0x83.toByte(), 0x02
            )
        }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 2000, 1000)
            try {
                client.readHoldingRegisters(0, Registers.TOTAL)
                throw AssertionError("应抛出 ModbusException")
            } catch (e: ModbusException) {
                assertEquals(2, e.code)
            }
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `仪器不应答时抛出超时`() {
        val dev = MockDevice { _, _, _ -> Thread.sleep(2000); ByteArray(0) }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 500, 200)  // SO_TIMEOUT=200ms
            try {
                client.readHoldingRegisters(0, Registers.TOTAL)
                throw AssertionError("应抛出 ModbusTimeoutException")
            } catch (e: ModbusTimeoutException) {
                // expected
            }
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `FC06 写寄存器回显正确`() {
        val dev = MockDevice { req, tx, unit ->
            // FC06 响应 = 请求 PDU 原样（回显），构造 MBAP + 06 + 地址 + 值
            val pdu = req.copyOfRange(7, req.size)
            byteArrayOf(
                (tx ushr 8).toByte(), tx.toByte(), 0, 0, 0, (pdu.size + 1).toByte(), unit.toByte()
            ) + pdu
        }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 2000, 1000)
            val echo = client.writeSingleRegister(0x01, 0)
            assertEquals(0, echo)
        } finally {
            client.disconnect()
            dev.stop()
        }
    }
}
