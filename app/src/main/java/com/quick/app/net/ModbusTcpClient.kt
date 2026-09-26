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
 *
 * **迟到应答（2026-09-26）**：本仪器会晚好几秒甚至几十秒才回上一笔（现场证据见 docs §28），
 * 迟到的应答可能落在**下一条 TCP 连接**上。所以 [exchange] 对「事务 ID 不是本轮」的帧
 * **读完即丢、继续等本轮**，而不是把连接判死 —— 旧实现每遇到一次迟到就断线重连，
 * 于是「断连」不断出现（`dropStats` 里的「其它通信失败／事务 ID 不匹配」）。
 * 丢弃的帧数记在 [staleFrames]，诊断页与断线统计一起显示。
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
     * 因「不是本轮应答」而被丢弃的迟到帧**累计**数（自本次进程启动）。
     *
     * 这是回答「链路到底稳不稳」的仪器：它一直涨 = 仪器侧经常迟到应答，
     * 但连接不再因此被拆掉（旧实现每次都断线重连，现场看到的就是「断连」）。
     */
    @Volatile
    var staleFrames: Long = 0L
        private set

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
                var stale = 0
                while (true) {
                    val head = ByteArray(7)
                    inpS.readFully(head)
                    val respTx = ((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)
                    val pid = ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
                    val len = ((head[4].toInt() and 0xFF) shl 8) or (head[5].toInt() and 0xFF)
                    val respUnit = head[6].toInt() and 0xFF

                    // ⚠️ 顺序（2026-09-26 修）：**先把整帧读完，再做强校验**。
                    // 反过来的话，校验失败抛异常时帧体还留在接收缓冲区里 —— 下一次 exchange 会
                    // 把那串残留当成 MBAP 头来解析（事务 ID 自然是垃圾）→ 又失败 → 又残留，
                    // 是个出不来的正反馈。长度决定帧体大小，只有长度这条必须先判。
                    if (len < 2 || len > MAX_MBAP_LEN) {
                        onFrame(FrameLog('←', head.hex(), "⚠ 长度异常 len=$len，帧边界已丢 → 只能丢弃本连接"))
                        throw IOException("响应长度异常: $len")
                    }
                    val rest = ByteArray(len - 1)
                    inpS.readFully(rest)
                    val full = head + rest

                    // ⚠️「不是本轮的应答」= 上一笔的**迟到应答**，丢弃后继续等，**绝不断线**。
                    // 现场证据（2026-09-26 用户截图）：一次读超时后重连，新连接上读到的第一帧
                    // 正好是上一笔的事务 ID（期望 0x52d 收到 0x52c），而那个 tx 的请求是在**上一条
                    // 连接**上发出的 —— 说明仪器侧（很可能是 Wi-Fi 透传模块）把串口迟到的应答
                    // 转发到了当时那条 TCP 连接上。
                    // 旧实现把它当致命错误 → 关连接重连 → 下一笔的应答又迟到 → 越连越乱。
                    if (respTx != tx) {
                        if (stale >= MAX_STALE_FRAMES) {
                            onFrame(
                                FrameLog(
                                    '←', full.hex(),
                                    "⚠ 第 ${stale + 1} 帧仍不是本轮的应答（tx 0x${tx.toString(16)}）→ 放弃本轮，交回上层重连"
                                )
                            )
                            throw IOException("连续 $MAX_STALE_FRAMES 帧都不是本轮的应答（tx 0x${tx.toString(16)}）→ 重连")
                        }
                        stale++
                        staleFrames++
                        onFrame(
                            FrameLog(
                                '←', full.hex(),
                                "⚠ 迟到帧：tx=0x${respTx.toString(16)} ≠ 本轮 0x${tx.toString(16)}，已丢弃并继续等本轮应答"
                            )
                        )
                        continue
                    }
                    // 到这里才确认「这一帧就是本轮的应答」；下面这些校验失败才算真的异常。
                    // 无论校验是否通过，帧都已经读完并记了日志 —— 这是诊断页要看的原始证据。
                    onFrame(FrameLog('←', full.hex()))
                    if (pid != 0) throw IOException("协议 ID 非 0: 0x${pid.toString(16)}")
                    if (respUnit != unitId) throw IOException("单元 ID 不匹配: $respUnit != $unitId")
                    val fc = rest[0].toInt() and 0xFF
                    if ((fc and 0x80) != 0) {
                        // 异常应答 = FC|0x80 + 异常码：缺异常码的畸形帧不能硬取 rest[1]
                        if (rest.size < 2) throw IOException("异常应答长度不足（只有功能码）")
                        throw ModbusException(rest[1].toInt() and 0xFF)
                    }
                    return rest.copyOfRange(1, rest.size)
                }
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

    companion object {
        /**
         * 一次请求里最多容忍几帧「不是本轮应答」的迟到帧。
         *
         * 每跳一帧都是白拿的（那帧已经在缓冲区里，读完就完事），但不能没有上限：
         * 连续这么多帧都对不上，说明两边的节奏已经乱了，继续读下去没有意义 ——
         * 抛出去让控制器断线重连（重连会把残留一并丢掉）。
         */
        const val MAX_STALE_FRAMES = 4

        /**
         * MBAP 长度字段的上限：MODBUS 规定 PDU ≤ 253 字节，加上单元 ID 1 字节 = 254。
         *
         * 本项目自己读 36 个寄存器时长度是 75，远远够用。设这个上限是为了**防失步**：
         * 万一接收到一段错位的字节，长度字段可能是任意 16 位值（最大 65535），
         * 照它去读就会卡在等一大堆永远不来的字节上，直到超时。
         */
        const val MAX_MBAP_LEN = 254
    }
}
