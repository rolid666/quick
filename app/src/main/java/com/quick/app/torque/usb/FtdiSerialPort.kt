package com.quick.app.torque.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.util.Locale

/**
 * 最小 FTDI 串口实现 —— 只做扭力计这一件事：**打开 → 配成 19200 8N2 → 读**。
 *
 * 为什么自己写而不用 usb-serial-for-android：
 * 该库只发布在 JitPack，本机网络下 JitPack 及其国内镜像全都取不到（docs §24），
 * 而现场要的是「能编译、能装、明天能测数据」，不能把构建押在一个拉不下来的包上。
 * 覆盖面因此**故意收窄**：只支持 FT232R 一族的 3 MHz 分频（见 [FtdiBaud]），
 * 其余芯片会在日志里明确说明「未验证」，而不是假装支持。
 *
 * 协议依据（FTDI 官方文档 AN_232R-01 / D2XX 的 VCP 控制命令，全部是厂商控制传输）：
 * | 命令 | bRequest | wValue | 说明 |
 * |---|---|---|---|
 * | Reset        | 0x00 | 0x0000 复位 / 0x0001 清 RX / 0x0002 清 TX | SIO_RESET |
 * | SetModemCtrl | 0x01 | 0x0303 = DTR+RTS 有效 | 有些仪器不拉 DTR/RTS 就不发数据 |
 * | SetFlowCtrl  | 0x02 | 0x0000 无流控 | 手册未提流控 → 关 |
 * | SetBaudRate  | 0x03 | 分频值（低 16 位）/ wIndex = 高 16 位 | 见 [FtdiBaud] |
 * | SetDataBits  | 0x04 | 0x0208 = 8 数据位 + **2 停止位** + 无校验 | 手册：1+8+2 无校验 |
 * | SetLatency   | 0x09 | 毫秒（1~255） | 取 1ms：数据一到就上送，不用等满 16ms |
 * | SetBitMode   | 0x0B | 0x0000 = 复位为普通 UART | 仅 H/D 系列需要 |
 *
 * **读到的每个 USB 包开头有 2 个 modem 状态字节**（FTDI 固定行为：一个包 = 2 状态 + 最多 62 数据），
 * 解析前必须剥掉，否则每隔 62 字节就会混进 0x01 0x60 两个垃圾字节 ——
 * 这正是「自己写驱动」最容易踩且最难查的坑，[stripStatusBytes] 单测覆盖。
 */
class FtdiSerialPort private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val packetSize: Int,
    val chipName: String,
    val baud: Int
) {

    @Volatile private var open = true

    val isOpen: Boolean get() = open

    /** 一行诊断说明（诊断页/测量页显示「现在连的是什么」） */
    fun describe(): String =
        "$chipName  ${baud}bps 8N2（分频 0x${String.format(Locale.US, "%04X", FtdiBaud.divisorFor(baud))}" +
            "，包长 $packetSize）"

    /**
     * 配置芯片。返回**逐步执行日志**：诊断页原样显示 ——
     * 明天真机连不上时，一眼能看出是哪一步没成（而不是只说「打开失败」）。
     */
    fun configure(): List<String> {
        val steps = ArrayList<String>(8)
        fun step(name: String, request: Int, value: Int, index: Int = 0) {
            val r = conn.controlTransfer(REQ_HOST_TO_DEVICE, request, value, index, null, 0, CTRL_TIMEOUT_MS)
            steps += if (r >= 0) "✔ $name" else "✘ $name（返回 $r）"
        }
        step("复位芯片", REQ_RESET, 0x0000)
        // FT232H/FT2232H/FT4232H 必须显式复位到 UART 模式，否则读写全无数据
        if (chipName.contains("FT232H") || chipName.contains("FT2232") || chipName.contains("FT4232")) {
            step("复位为普通 UART（BitMode 0）", REQ_SET_BITMODE, 0x0000)
        }
        step("拉 DTR+RTS", REQ_SET_MODEM_CTRL, 0x0303)
        val divisor = FtdiBaud.divisorFor(baud)
        step(
            "波特率 $baud（分频 0x${String.format(Locale.US, "%04X", divisor)}）",
            REQ_SET_BAUD, divisor and 0xFFFF, (divisor ushr 16) and 0xFFFF
        )
        step("数据位 8 + 停止位 2 + 无校验", REQ_SET_DATA, DATA_8N2)
        step("关闭流控", REQ_SET_FLOW_CTRL, 0x0000)
        step("延迟计时器 ${LATENCY_MS}ms", REQ_SET_LATENCY, LATENCY_MS)
        step("清空接收缓冲", REQ_RESET, 0x0001)
        step("清空发送缓冲", REQ_RESET, 0x0002)
        return steps
    }

    /**
     * 读一个 USB 包（阻塞，最多等 timeoutMs）。返回**原始字节**（含 2 个状态字节），
     * 空数组 = 这段时间内没有数据（正常现象，不是错误）。
     */
    fun readPacket(timeoutMs: Int): ByteArray {
        if (!open) return EMPTY
        val buf = ByteArray(packetSize)
        val n = try {
            conn.bulkTransfer(epIn, buf, buf.size, timeoutMs)
        } catch (_: Exception) {
            -1                                  // 拔线/被系统回收：当作断开，由上层重连
        }
        if (n <= 0) return EMPTY
        return buf.copyOf(n)
    }

    fun close() {
        if (!open) return
        open = false
        runCatching { conn.releaseInterface(iface) }
        runCatching { conn.close() }
    }

    companion object {

        private const val REQ_HOST_TO_DEVICE = 0x40   // 主机→设备 / 厂商自定义 / 设备
        private const val REQ_RESET = 0x00
        private const val REQ_SET_MODEM_CTRL = 0x01
        private const val REQ_SET_FLOW_CTRL = 0x02
        private const val REQ_SET_BAUD = 0x03
        private const val REQ_SET_DATA = 0x04
        private const val REQ_SET_LATENCY = 0x09
        private const val REQ_SET_BITMODE = 0x0B
        private const val CTRL_TIMEOUT_MS = 1000
        private const val LATENCY_MS = 1

        /** wValue：数据位 8 | 停止位 2 << 8 | 校验 0 << 11（手册：8N2 无校验） */
        const val DATA_8N2 = 8 or (2 shl 8)

        private val EMPTY = ByteArray(0)

        /**
         * 打开并声明接口。返回 null = 声明失败（多半是别的程序占着 / 权限没给）。
         */
        fun open(conn: UsbDeviceConnection?, device: UsbDevice, baud: Int): FtdiSerialPort? {
            if (conn == null) return null
            // 逐个接口找「带 bulk IN 端点」的那个并声明 —— FT232R 只有接口 0（数据口），
            // FT2232/FT4232 有多个接口（每通道一个），写死接口 0 在双通道芯片上会挑错通道
            var iface: UsbInterface? = null
            var epIn: UsbEndpoint? = null
            for (i in 0 until device.interfaceCount) {
                val it0 = device.getInterface(i)
                val ep = (0 until it0.endpointCount)
                    .map { it0.getEndpoint(it) }
                    .firstOrNull {
                        it.direction == UsbConstants.USB_DIR_IN &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    } ?: continue
                if (conn.claimInterface(it0, true)) {
                    iface = it0
                    epIn = ep
                    break
                }
            }
            if (iface == null || epIn == null) {
                runCatching { conn.close() }
                return null
            }
            // 一个 USB 包 = 2 状态字节 + 数据，所以按端点包长读、读回来就剥前 2 字节
            val size = if (epIn.maxPacketSize >= 64) epIn.maxPacketSize else 64
            return FtdiSerialPort(
                conn, iface, epIn, size,
                chipName = FtdiUsb.chipName(device.productId),
                baud = baud
            )
        }

        /**
         * 剥掉 FTDI 每个包开头的 2 个 modem 状态字节（0x01 + 状态位）。
         * 包长 ≤2 时说明这一包只有状态、没有数据 → 返回空。
         */
        fun stripStatusBytes(packet: ByteArray): ByteArray =
            if (packet.size <= 2) EMPTY else packet.copyOfRange(2, packet.size)
    }
}
