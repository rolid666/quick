package com.quick.app.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * 帧编解码与快照解析。
 * 寄存器语义按厂方手册《地址分配》表（`piture/file.webp` 第 2 页）：
 * 0x00 实时温度 / 0x01 单位 / 0x02 实时电压 / 0x03 实时电阻 / 0x04 测量通道 /
 * 0x0A~0x19 设备信息 / 0x1A 设定温度 / 0x1B~0x1C 温度判断 / 0x1D 电压上限 / 0x1E 电阻上限 /
 * 0x1F 测试上传标志（★触发，读后清零）/ 0x20 保存温度 / 0x21 保存电压 / 0x22 保存电阻 / 0x23 判定。
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

    /** 按手册地址分配表造一帧"刚按过保存"的仪器数据 */
    private fun savedFrame(): IntArray {
        val regs = IntArray(Registers.TOTAL)
        regs[0x00] = 0x00EA      // 实时温度 23.4 ℃
        regs[0x01] = 0          // ℃
        regs[0x02] = 67         // 实时电压 6.7 mV
        regs[0x03] = 25         // 实时电阻 2.5 Ω
        regs[0x04] = 0          // 测量通道：温度
        val info = "QK-HT-001".toByteArray(Charsets.US_ASCII)
        val pad = ByteArray(Registers.INFO_COUNT * 2 - info.size)
        (info + pad).toList().chunked(2).forEachIndexed { i, b ->
            regs[Registers.INFO_START + i] = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        }
        regs[0x1A] = 350        // 设定温度
        regs[0x1B] = 340        // 温度判断下限
        regs[0x1C] = 360        // 温度判断上限
        regs[0x1D] = 20         // 电压判断上限 2.0 mV
        regs[0x1E] = 20         // 电阻判断上限 2.0 Ω
        regs[0x1F] = 1          // ★测试上传标志：保存按钮按下
        regs[0x20] = 351        // ★测试保存温度（仪器定格值）
        regs[0x21] = 5          // ★测试保存电压 0.5 mV
        regs[0x22] = 10         // ★测试保存电阻 1.0 Ω
        regs[0x23] = 1          // ★判定 OK
        return regs
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
            // 厂家 TCP 示例: 00 01 00 00 00 06 01 03 00 00 00 0A（读 10 个）；
            // 本项目全读 0x00~0x23 共 36 个 = 0x0024；首笔事务 ID = 0001（与厂家示例一致）
            val expected = byteArrayOf(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x24
            )
            assertArrayEquals("请求帧应严格等于 MBAP(7) + FC03 PDU(5)", expected, req)
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `按手册地址分配表解析整帧结果`() {
        val regs = savedFrame()
        val dev = MockDevice { _, tx, unit -> readResponse(tx, unit, regs) }.also { it.start() }
        val client = ModbusTcpClient()
        try {
            client.connect("127.0.0.1", dev.port, 2000, 1000)
            val got = client.readHoldingRegisters(0, Registers.TOTAL)
            val snap = DeviceSnapshot.parse(got)
            assertEquals(36, got.size)
            assertTrue(snap.hasResultBlock)

            // 实时量（×0.1 缩放）
            assertEquals(23.4, snap.liveTempC!!, 0.001)
            assertEquals(0, snap.unit)
            assertEquals(6.7, snap.liveVoltageMv!!, 0.001)
            assertEquals(2.5, snap.liveResistanceOhm!!, 0.001)
            assertEquals(0, snap.channel)
            assertEquals("QK-HT-001", snap.deviceInfo)

            // 判据参数（整数 ℃ + ×0.1 上限）
            assertEquals(350, snap.targetTemp)
            assertEquals(340, snap.tempLow)
            assertEquals(360, snap.tempHigh)
            assertEquals(2.0, snap.voltageLimitMv!!, 0.001)
            assertEquals(2.0, snap.resistanceLimitOhm!!, 0.001)

            // 结果块（0x1F 标志=1 时有效）
            assertEquals(1, snap.uploadFlag)
            assertTrue(snap.hasSaveFlag)
            assertEquals(351, snap.savedTempC)
            assertEquals(0.5, snap.savedVoltageMv!!, 0.001)
            assertEquals(1.0, snap.savedResistanceOhm!!, 0.001)
            assertEquals(1, snap.judge)
            assertEquals("OK", snap.resultText)
            assertEquals(true, snap.judgedOk)

            // 记录用测量温度 = 仪器定格的 0x20（不是实时温度 23.4）
            assertEquals(351.0, snap.measuredTempC!!, 0.001)
        } finally {
            client.disconnect()
            dev.stop()
        }
    }

    @Test
    fun `判定 0x23 三种取值分别对应 OK NG 无效`() {
        // 手册：0=NG、1=OK、2=无效 —— 2 绝不能当成 OK（用户实测中确实出现过 2）
        for ((v, expect) in listOf(0 to "NG", 1 to "OK", 2 to "无效")) {
            val regs = IntArray(Registers.TOTAL)
            regs[Registers.JUDGE] = v
            val snap = DeviceSnapshot.parse(regs)
            assertEquals("0x23=$v 时应为 $expect", expect, snap.resultText)
            assertEquals(v == 1, snap.judgedOk)
            assertEquals(v == 2, snap.judgedInvalid)
        }
    }

    @Test
    fun `上传标志未按下时不触发`() {
        val regs = IntArray(Registers.TOTAL)   // 全 0 = 仪器待机
        val snap = DeviceSnapshot.parse(regs)
        assertEquals(0, snap.uploadFlag)
        assertTrue(!snap.hasSaveFlag)
        assertEquals(0, snap.triggerRaw)
        assertNull(snap.deviceInfo)            // 空字符串 → null，不占列表
    }

    @Test
    fun `老固件只给 0x00~0x1E 时降级解析不崩且结果块为空`() {
        val regs = IntArray(Registers.LEGACY_TOTAL)   // 31 个：无结果块
        regs[0x00] = 0x00EA
        regs[Registers.RES_LIMIT] = 20                // 此模式下 0x1E 是电阻判断上限
        val snap = DeviceSnapshot.parse(regs)
        assertTrue(!snap.hasResultBlock)
        assertNull(snap.uploadFlag)
        assertNull(snap.judge)
        assertEquals(23.4, snap.measuredTempC!!, 0.001)   // 无定格值 → 回落实时温度
        assertEquals(20, snap.triggerRaw)                 // 降级触发值退回 0x1E
    }

    @Test
    fun `内容签名只随保存字段变化 不随实时量与通道变化`() {
        // 用途：① 0x1F 保持约 1 s（实测），0.9 s 轮询可能读到两帧 → 签名相同即跳过；
        //      ② 标志已回 0 但签名变了 → 补收「读晚了」的那一笔。
        // 因此签名**只能**含「按保存才会写」的 0x20~0x23：实时量持续在变，切通道/改设定温度
        // 都不代表新结果 —— 纳入任何一个都会造成与操作不符的重复记录（补收通道尤其危险）。
        fun snapOf(
            judge: Int, savedTemp: Int, savedV: Int,
            liveTemp: Int = 218, liveV: Int = 12, channel: Int = 0, target: Int = 350
        ): DeviceSnapshot {
            val regs = IntArray(Registers.TOTAL)
            regs[Registers.JUDGE] = judge
            regs[Registers.SAVED_TEMP] = savedTemp
            regs[Registers.SAVED_VOLTAGE] = savedV
            regs[Registers.LIVE_TEMP] = liveTemp
            regs[Registers.LIVE_VOLTAGE] = liveV
            regs[Registers.CHANNEL] = channel
            regs[Registers.TARGET_TEMP] = target
            return DeviceSnapshot.parse(regs)
        }
        val base = snapOf(1, 351, 5)
        assertEquals("实时温度/电压变化 → 签名不变", base.contentSignature,
            snapOf(1, 351, 5, liveTemp = 3510, liveV = 99).contentSignature)
        assertEquals("操作员切测量通道 → 签名不变", base.contentSignature,
            snapOf(1, 351, 5, channel = 2).contentSignature)
        assertEquals("操作员改设定温度 → 签名不变", base.contentSignature,
            snapOf(1, 351, 5, target = 400).contentSignature)
        assertNotEquals("判定变化 → 签名变化", base.contentSignature, snapOf(0, 351, 5).contentSignature)
        assertNotEquals("保存温度变化 → 签名变化", base.contentSignature, snapOf(1, 352, 5).contentSignature)
        assertNotEquals("保存电压变化 → 签名变化", base.contentSignature, snapOf(1, 351, 6).contentSignature)
    }

    @Test
    fun `异常应答抛出 ModbusException`() {
        val dev = MockDevice { req, tx, unit ->
            // 非法地址: FC 0x83 + code 0x02（老固件读到 0x1F 之外即为此种应答）
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
