package com.quick.app.torque.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.Locale

/**
 * FTDI 设备发现与识别。
 *
 * 厂商标识 **0x0403 = FTDI**。扭力计手册只说了「需安装 FTDI 芯片的 VCP 驱动」，
 * 没写具体型号 —— 所以这里把已知 PID 都列出来，诊断页直接显示认到了哪一颗，
 * 明天实测时**第一件事就是看这一行**（型号决定了波特率分频编码，见 [FtdiBaud] 的说明）。
 */
object FtdiUsb {

    const val VENDOR_ID = 0x0403

    /** PID → 芯片名（名称照 FTDI 官方型号写，方便直接去查对应数据手册） */
    val CHIPS: Map<Int, String> = linkedMapOf(
        0x6001 to "FT232R/FT232AM",      // 3 MHz 分频，本项目的默认假设
        0x6010 to "FT2232D",
        0x6011 to "FT4232H",             // ⚠ 12 MHz 分频，未验证
        0x6014 to "FT232H",              // ⚠ 12 MHz 分频，未验证
        0x6015 to "FT230X/FT231X/FT234X" // 3 MHz 分频（与 FT232R 同族）
    )

    fun chipName(productId: Int): String = CHIPS[productId] ?: "FTDI 未知型号(0x%04X)".format(Locale.US, productId)

    fun isFtdi(device: UsbDevice): Boolean = device.vendorId == VENDOR_ID

    /** 系统里所有 FTDI 设备 */
    fun devices(mgr: UsbManager): List<UsbDevice> =
        runCatching { mgr.deviceList.values.filter { isFtdi(it) } }.getOrDefault(emptyList())

    /**
     * 挑一个用。**优先已知型号**（FT232R 一族排在前面）——
     * 同一台平板上如果插了别的 FTDI 设备（调试串口之类），不至于挑错。
     */
    fun findDevice(mgr: UsbManager): UsbDevice? {
        val list = devices(mgr)
        return list.firstOrNull { CHIPS.containsKey(it.productId) } ?: list.firstOrNull()
    }

    /** 诊断页用的一行描述：VID/PID/芯片/接口数 */
    fun describe(device: UsbDevice): String =
        "VID_%04X PID_%04X  %s  接口 %d".format(
            Locale.US, device.vendorId, device.productId, chipName(device.productId), device.interfaceCount
        )
}
