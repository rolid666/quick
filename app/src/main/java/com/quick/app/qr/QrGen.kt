package com.quick.app.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.quick.app.data.db.limitValueText

/**
 * 192AF+（原 191AF+）配网二维码生成。
 *
 * 厂家《扫码配置指令》：
 * - 无线网络：WifiName / WifiPassword / ServerIP / ServerPort / GateWay（MB-TCP 下）
 * - 通用：SN / TSET / TMIN / TMAX / VMAX / RMAX
 * 多指令用英文逗号组合；MB-TCP 模式下 ServerIP = 仪器自己的静态 IP（非上位机）。
 * 全部字符必须是**英文状态下输入**的（协议原文），中文标点/全角字符会让整条指令失效。
 *
 * ⚠️ **两版指令集，以新版为准（2026-09-21 定）**：
 * - 旧版（文本《191AF 通讯协议 V1.1》表 1）：`SN / TSET / TERR`，示例 `SN=QK-HT-001,TSET=350,TERR=20`；
 * - 新版（用户提供的《扫码配置指令》表 1）：`SN / TSET / TMAX / TMIN / VMAX / RMAX`，
 *   示例 `SN=QK-HT-001,TSET=350,TMIN=340,TMAX=360`，**没有 TERR**。
 *
 * 判断依据：旧文档的**地址分配表本身就是老固件版**（0x1B 误差范围、0x1C 标志、无电压/电阻寄存器），
 * 而厂家新版指令集与真机寄存器一一对上 ——
 * `TMIN`/`TMAX` = 0x1B/0x1C（温度判断下限/上限）、`VMAX` = 0x1D（电压判断上限）、`RMAX` = 0x1E（电阻判断上限）；
 * 旧固件那个「误差范围」是单个 ± 值，才对得上旧版的 `TERR`。
 * 所以本机固件（0x1F~0x23 结果块那一代）应当吃 TMIN/TMAX 这一版。
 * 尚未真机扫码验证 —— 若扫 `TSET=350,TMIN=330,TMAX=370` 后仪器 0x1B/0x1C 不变，
 * 就说明该退回 TERR，改 [configPayload] 一处即可（见 docs §23）。
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

    /**
     * 组合配置码（厂家《扫码配置指令》表 1 通用指令）—— 把选中的项一次配给仪器。
     *
     * 顺序固定，与厂家表格自上而下一致（人工核对二维码内容时一眼能对上）：
     * `SN` → `TSET` → `TMIN` → `TMAX` → `VMAX` → `RMAX`
     *
     * - `SN`   设备信息（0x0A~0x19），最长 16 字符（32 字节 ASCII 的字符串形式）
     * - `TSET` 设定温度（0x1A）℃
     * - `TMIN` 温度下限（0x1B）℃ ＝ 设定温度 − 误差范围
     * - `TMAX` 温度上限（0x1C）℃ ＝ 设定温度 + 误差范围
     * - `VMAX` 漏电压上限（0x1D）mV ＝ 寄存器原值 ÷ 10
     * - `RMAX` 接地电阻上限（0x1E）Ω ＝ 寄存器原值 ÷ 10
     *
     * **哪些项进二维码由调用方决定**（用户 2026-09 要求：温度/漏电压/电阻这三项任意一项有选择就能出码）：
     * 传 null 的项**整条不输出**，仪器侧该项保持原值不变 ——
     * 例如只选了漏电压上限，得到的就是 `VMAX=2`，不会顺手把温度也改掉。
     *
     * 六项全空则返回 **null**（调用方据此禁用「配置二维码」按钮，不生成空码）。
     *
     * 换算在函数内部完成（不在界面上算），单元测试把每个组合逐字节钉死 —— 见 TempConfigQrTest。
     */
    fun configPayload(
        deviceSn: String? = null,
        setTemp: Int? = null,
        tolerance: Int? = null,
        vmaxRaw: Int? = null,
        rmaxRaw: Int? = null
    ): String? {
        val parts = mutableListOf<String>()
        val sn = deviceSn?.trim().orEmpty()
        if (sn.isNotEmpty()) parts += "SN=$sn"
        // 温度三项要成组：只有设定温度没有误差范围算不出上下限，宁可整组不出（宁缺勿错配）
        if (setTemp != null && tolerance != null) {
            parts += "TSET=$setTemp"
            parts += "TMIN=${setTemp - tolerance}"
            parts += "TMAX=${setTemp + tolerance}"
        }
        if (vmaxRaw != null) parts += "VMAX=${limitValueText(vmaxRaw)}"
        if (rmaxRaw != null) parts += "RMAX=${limitValueText(rmaxRaw)}"
        return if (parts.isEmpty()) null else parts.joinToString(",")
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
