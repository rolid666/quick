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
 * | SetDataBits  | 0x04 | **0x1008** = 8 数据位 + 2 停止位 + 无校验 | 手册：1+8+2 无校验 |
 * | SetLatency   | 0x09 | 毫秒（1~255） | 取 1ms：数据一到就上送，不用等满 16ms |
 * | SetBitMode   | 0x0B | 0x0000 = 复位为普通 UART | 仅 H/D 系列需要 |
 *
 * **读到的每个 USB 包开头有 2 个 modem 状态字节**（FTDI 固定行为：一个包 = 2 状态 + 最多 62 数据），
 * 解析前必须剥掉，否则每隔 62 字节就会混进 0x01 0x60 两个垃圾字节 ——
 * 这正是「自己写驱动」最容易踩且最难查的坑，[stripStatusBytes] 单测覆盖。
 *
 * 怎么剥见 [stripStatusBytes]：与成熟库 usb-serial-for-android 的 `FtdiSerialDriver.readFilter()`
 * **逐句等价**（2026-09-24 取到该库源码核对过，见 docs §26，那里也写清了「按包长步进」在什么情况下才有意义）。
 *
 * 芯片已于 2026-09-23 真机确认是 **FT232R（PID 6001）**：3 MHz 分频、64 字节包，与 [FtdiBaud] 的假设一致。
 */
class FtdiSerialPort private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    val packetSize: Int,
    val chipName: String,
    val baud: Int
) {

    @Volatile private var open = true

    val isOpen: Boolean get() = open

    /** 剥掉本机包长下每个 USB 包开头的 2 个状态字节（读循环每包调一次） */
    fun stripStatus(raw: ByteArray): ByteArray = stripStatusBytes(raw, packetSize)

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

        /**
         * `SET_DATA_REQUEST`(0x04) 的 wValue —— FTDI 的位域（FT232/FT245 Device API 与 libftdi 一致）：
         *
         * | 位 | 含义 |
         * |---|---|
         * | 0–7 | 数据位（5/6/7/8） |
         * | 8–10 | 校验（0=无 1=奇 2=偶 3=Mark 4=Space） |
         * | **11–13** | **停止位（0=1 位、1=1.5 位、2=2 位）** |
         * | 14 | 发送 break |
         * | 15 | 保留 |
         *
         * 所以手册的「1 起始位 + 8 数据位 + 2 停止位 + 无校验」= `8 or (2 shl 11)` = **0x1008**。
         *
         * ⚠️ **2026-09-23 修**：原先写的是 `8 or (2 shl 8)` = 0x0208 —— 停止位放错了位域，
         * 落在**校验位**上，实际把芯片配成了「**8 数据位 + 偶校验 + 1 停止位**」，
         * 而设备发的是无校验。成熟库 usb-serial-for-android 用的就是 0x1008（`config |= 0x1000`）。
         */
        const val DATA_8N2 = 8 or (2 shl 11)

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
            // 按端点包长读：剥状态字节要靠这个长度才能对上包边界（见 stripStatusBytes）
            val size = if (epIn.maxPacketSize >= 64) epIn.maxPacketSize else 64
            return FtdiSerialPort(
                conn, iface, epIn, size,
                chipName = FtdiUsb.chipName(device.productId),
                baud = baud
            )
        }

        /**
         * 剥掉 FTDI 每个包开头的 2 个 modem 状态字节（`01` + 状态位，实测是 `01 60`）。
         *
         * 按 [packetSize] 步进逐包剥 —— 与 usb-serial-for-android 的
         * `FtdiSerialDriver.readFilter()` 逐句等价（`srcPos += maxPacketSize`，每处留 2 字节头），
         * 那条 `while (nread == READ_HEADER_LENGTH)` 的读循环也印证了「只有状态的 2 字节包」是真实存在的。
         *
         * ⚠️ **说清适用边界，别把这条当成「修了乱码」**（2026-09-24）：本驱动的读缓冲正好是
         * **一个包长**（[readPacket] 只请求 `packetSize` 字节），而 USB 短包会立刻结束一次
         * `bulkTransfer`，所以一次读最多只装得下**一个**包 —— 这种情况下「只剥前 2 字节」和
         * 「按包长步进」结果完全相同。步进写法在这里是**保险**：缓冲一旦被加大（比如以后
         * 一次读 512 字节），一次读就会装进多个 64 字节整包，那时只有步进才对。
         * 反过来说：「数据中间有乱码」**不是**这段代码造成的，别再从这儿找原因（docs §26）。
         *
         * 某个包剩下的不足 2 字节 = 这一包只有状态、没有数据 → 跳过，后面的照剥；
         * 整段 ≤2 字节则返回空。库在这里是抛 `IOException("Expected at least 2 bytes")`，
         * 我们选择**跳过**：现场宁可少 2 字节，也不能因为一个畸形包把整条链路判成断开。
         */
        fun stripStatusBytes(bytes: ByteArray, packetSize: Int = 64): ByteArray {
            if (bytes.size <= 2) return EMPTY
            val step = if (packetSize > 2) packetSize else 64
            val out = ByteArray(bytes.size)
            var dest = 0
            var src = 0
            while (src < bytes.size) {
                val end = (src + step).coerceAtMost(bytes.size)
                val len = end - src - 2            // 这个包的数据部分（可能为 0：只有状态）
                if (len > 0) {
                    System.arraycopy(bytes, src + 2, out, dest, len)
                    dest += len
                }
                src += step
            }
            return out.copyOf(dest)
        }
    }
}
