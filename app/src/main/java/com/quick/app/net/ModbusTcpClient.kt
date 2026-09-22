package com.quick.app.net

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** MODBUS 异常应答（功能码 0x80 + 异常码） */
class ModbusException(val code: Int) : IOException("MODBUS exception response, code=$code")

/** socket 级超时 */
class ModbusTimeoutException : IOException("MODBUS 响应超时")

/** 帧日志：dir 取值 '→'(发送) '←'(接收) '!' (异常/事件) */
data class FrameLog(val dir: Char, val hex: String, val note: String = "")

/**
 * 自实现 MODBUS TCP 客户端（纯 JVM，不依赖 Android）。
 *
 * - MBAP: 事务ID(2) + 协议ID(2, 恒0) + 长度(2, =UnitID+PDU) + 单元ID(1)
 * - MODBUS TCP 无 RTU CRC；请求-响应一对一，事务 ID 回显配对。
 * - 全部方法串行同步；调用方负责放 IO 线程。
 * - 只实现本项目需要的 FC 0x03 / 0x06；不做功能码臆造。
 */
class ModbusTcpClient(
    private val onFrame: (FrameLog) -> Unit = {}
) {
    private var sock: Socket? = null
    private var inp: DataInputStream? = null
    private var out: OutputStream? = null
    private var txCounter = 0
    private val lock = Any()

    /**
     * ⚠️ `Socket.isConnected` 只表示「曾经连上过」，**对端断开后它仍为 true**，
     * 所以这里只能用来排除「本地已关闭/半关闭」，**不能**用来判断仪器还在不在线。
     * 真正发现断线靠读操作抛 IOException/超时（见 MeasurementController.runLoop）。
     */
    val isConnected: Boolean
        get() = synchronized(lock) {
            sock?.let { it.isConnected && !it.isClosed && !it.isInputShutdown && !it.isOutputShutdown } ?: false
        }

    fun connect(host: String, port: Int, connectTimeoutMs: Int, soTimeoutMs: Int) {
        synchronized(lock) {
            closeLocked()
            val s = Socket()
            s.tcpNoDelay = true
            s.soTimeout = soTimeoutMs
            // 开着 keepalive：对端（仪器/AP）静默掉线时，由内核探活发现「半开连接」——
            // 否则只能等下一次请求超时才察觉（表现为「偶发断连」）
            s.keepAlive = true
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            sock = s
            inp = DataInputStream(s.getInputStream())
            out = s.getOutputStream()
        }
        onFrame(FrameLog('!', "", "连接 $host:$port 成功"))
    }

    fun disconnect() {
        synchronized(lock) { closeLocked() }
        onFrame(FrameLog('!', "", "连接已关闭"))
    }

    /** FC 0x03 读保持寄存器，返回大端序 IntArray（已 & 0xFFFF） */
    fun readHoldingRegisters(start: Int, quantity: Int, unitId: Int = Registers.DEFAULT_UNIT_ID): IntArray {
        val pdu = byteArrayOf(0x03, start.hi(), start.lo(), quantity.hi(), quantity.lo())
        val body = exchange(pdu, unitId)
        // 先判长度再取下标：畸形/错配的响应帧曾会在这里抛 ArrayIndexOutOfBoundsException，
        // 错误信息看不出是通信问题（现在统一成 IOException，控制器按「链路异常」处理）
        if (body.size < 1 + quantity * 2) {
            throw IOException("响应过短: ${body.size} 字节（期望 ${1 + quantity * 2}）")
        }
        val byteCount = body[0].toInt() and 0xFF
        if (byteCount != quantity * 2) throw IOException("响应字节数不符: $byteCount != ${quantity * 2}")
        return IntArray(quantity) { i ->
            val b = body[1 + i * 2].toInt() and 0xFF
            val a = body[2 + i * 2].toInt() and 0xFF
            (b shl 8) or a
        }
    }

    /** FC 0x06 写单个保持寄存器，返回仪器回显值 */
    fun writeSingleRegister(addr: Int, value: Int, unitId: Int = Registers.DEFAULT_UNIT_ID): Int {
        val pdu = byteArrayOf(0x06, addr.hi(), addr.lo(), value.hi(), value.lo())
        val body = exchange(pdu, unitId)
        if (body.size != 4) throw IOException("写寄存器响应长度异常")
        // FC06 回显 = FC + 地址(2) + 值(2)；exchange 已去掉功能码 → body[2..3] 为值
        return ((body[2].toInt() and 0xFF) shl 8) or (body[3].toInt() and 0xFF)
    }

    /** 发送请求并等待回显事务 ID 的响应，返回 PDU（已去掉功能码） */
    private fun exchange(pdu: ByteArray, unitId: Int): ByteArray {
        val tx = synchronized(lock) { txCounter = (txCounter + 1) and 0xFFFF; txCounter }
        synchronized(lock) {
            val s = sock ?: throw IOException("未连接")
            val outS = out ?: throw IOException("未连接")
            val frame = byteArrayOf(
                tx.hi(), tx.lo(),
                0, 0,
                ((pdu.size + 1) ushr 8).toByte(), (pdu.size + 1).toByte(), // Length = UnitID(1)+PDU
                unitId.toByte()
            ) + pdu
            onFrame(FrameLog('→', frame.hex()))
            outS.write(frame)
            outS.flush()

            val inpS = inp ?: throw IOException("未连接")
            try {
                val head = ByteArray(7)
                inpS.readFully(head)
                val respTx = ((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)
                val pid = ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
                val len = ((head[4].toInt() and 0xFF) shl 8) or (head[5].toInt() and 0xFF)
                val respUnit = head[6].toInt() and 0xFF
                if (pid != 0) throw IOException("协议 ID 非 0: 0x${pid.toString(16)}")
                if (respTx != tx) throw IOException("事务 ID 不匹配: 期望0x${tx.toString(16)} 收到0x${respTx.toString(16)}")
                if (respUnit != unitId) throw IOException("单元 ID 不匹配: $respUnit != $unitId")
                // Length 含单元 ID 本身（1 字节已读），剩余 = len - 1
                if (len < 2) throw IOException("响应长度异常: $len")
                val rest = ByteArray(len - 1)
                inpS.readFully(rest)
                onFrame(FrameLog('←', (head + rest).hex()))
                val fc = rest[0].toInt() and 0xFF
                if ((fc and 0x80) != 0) throw ModbusException(rest[1].toInt() and 0xFF)
                return rest.copyOfRange(1, rest.size)
            } catch (e: SocketTimeoutException) {
                onFrame(FrameLog('!', "", "响应超时(${s.soTimeout}ms)"))
                throw ModbusTimeoutException()
            }
        }
    }

    private fun closeLocked() {
        try { inp?.close() } catch (_: IOException) {}
        try { out?.close() } catch (_: IOException) {}
        try { sock?.close() } catch (_: IOException) {}
        inp = null; out = null; sock = null
    }

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }
    private fun Int.hi(): Byte = (this ushr 8).toByte()
    private fun Int.lo(): Byte = this.toByte()
}
