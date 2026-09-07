package com.quick.app.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 191AF+ 配网二维码生成。
 *
 * 厂家《191AF 系列通讯协议 V1.1》扫码配置指令：
 * - 无线网络：WifiName / WifiPassword / ServerIP / ServerPort / GateWay（MB-TCP 下）
 * - 通用：SN / TSET / TERR
 * 多指令用英文逗号组合；MB-TCP 模式下 ServerIP = 仪器自己的静态 IP（非上位机）。
 */
object QrGen {

    /** 无线网络配置码（MB-TCP）：gateway 为空时不输出 GateWay 项 */
    fun wifiConfigPayload(
        wifiName: String,
        wifiPassword: String,
        instrumentIp: String,
        port: Int,
        gateway: String? = null
    ): String {
        val parts = mutableListOf(
            "WifiName=$wifiName",
            "WifiPassword=$wifiPassword",
            "ServerIP=$instrumentIp",
            "ServerPort=$port"
        )
        if (!gateway.isNullOrBlank()) parts += "GateWay=$gateway"
        return parts.joinToString(",")
    }

    fun qrBitmap(content: String, sizePx: Int = 900): Bitmap {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1)
        val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx)
            for (y in 0 until sizePx)
                bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        return bmp
    }
}
