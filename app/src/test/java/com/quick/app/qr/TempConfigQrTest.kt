package com.quick.app.qr

import com.quick.app.data.db.LIMIT_RMAX
import com.quick.app.data.db.LIMIT_VMAX
import com.quick.app.data.db.limitPresetName
import com.quick.app.data.db.limitUnit
import com.quick.app.data.db.limitValueText
import com.quick.app.data.db.tempPresetName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合配置二维码（温度 / 漏电压上限 / 接地电阻上限 / 设备编号）的内容格式测试。
 *
 * 事实来源：厂家《扫码配置指令》表 1 通用指令与组合配置示例
 * `SN=QK-HT-001,TSET=350,TMIN=340,TMAX=360`（用户 2026-09-21 提供截图）。
 *
 * ⚠️ 这一版**不再是**旧文本协议里的 `TSET=…,TERR=…` ——
 * 旧文档的地址表本身就是老固件（0x1B 误差范围），而新版指令集与真机寄存器一一对应
 * （TMIN/TMAX = 0x1B/0x1C，VMAX = 0x1D，RMAX = 0x1E）。判断依据见 QrGen 的类注释。
 *
 * 仪器侧解析是黑盒，格式一旦对不上就是**配置不生效**（而且不报错），所以这里逐字节固定住。
 */
class TempConfigQrTest {

    @Test
    fun `温度 + 设备编号 —— 与厂家示例同构`() {
        assertEquals(
            "SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370",
            QrGen.configPayload("QK-HT-001", setTemp = 350, tolerance = 20)
        )
    }

    @Test
    fun `只选温度 —— 不出 SN（编号为空时不改动仪器上的编号）`() {
        assertEquals(
            "TSET=350,TMIN=330,TMAX=370",
            QrGen.configPayload(setTemp = 350, tolerance = 20)
        )
    }

    @Test
    fun `只选漏电压上限 —— 只有 VMAX 一条，不顺手改温度`() {
        assertEquals("VMAX=2", QrGen.configPayload(vmaxRaw = 20))
    }

    @Test
    fun `只选接地电阻上限 —— 只有 RMAX 一条`() {
        assertEquals("RMAX=2", QrGen.configPayload(rmaxRaw = 20))
    }

    @Test
    fun `三项 + 设备编号全选 —— 顺序与厂家表格一致`() {
        assertEquals(
            "SN=A1,TSET=350,TMIN=330,TMAX=370,VMAX=2,RMAX=2.5",
            QrGen.configPayload("A1", setTemp = 350, tolerance = 20, vmaxRaw = 20, rmaxRaw = 25)
        )
    }

    @Test
    fun `只选设备编号 —— 也能出码（厂家单条指令示例就是一条 SN）`() {
        assertEquals("SN=QK-HT-001", QrGen.configPayload("QK-HT-001"))
    }

    @Test
    fun `什么都没选 —— 返回 null，不生成空二维码`() {
        assertNull(QrGen.configPayload())
        assertNull(QrGen.configPayload("   "))
    }

    @Test
    fun `设备编号两端空格被去掉`() {
        assertEquals(
            "SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370",
            QrGen.configPayload("  QK-HT-001  ", setTemp = 350, tolerance = 20)
        )
    }

    @Test
    fun `误差为 0 —— 上下限等于设定温度`() {
        assertEquals("TSET=350,TMIN=350,TMAX=350", QrGen.configPayload(setTemp = 350, tolerance = 0))
    }

    @Test
    fun `只有设定温度没有误差范围 —— 整组不出（宁缺勿错配）`() {
        assertNull(QrGen.configPayload(setTemp = 350))
        assertNull(QrGen.configPayload(tolerance = 20))
    }

    @Test
    fun `寄存器原值换算 —— 整数不带小数点，一位小数保留`() {
        assertEquals("2", limitValueText(20))
        assertEquals("2.5", limitValueText(25))
        assertEquals("0.1", limitValueText(1))
        assertEquals("10", limitValueText(100))
        // 二维码里必须是小数点（.），不能是逗号 —— 逗号是厂家规定的指令分隔符
        assertTrue(!limitValueText(25).contains(","))
        assertEquals("VMAX=2.5", QrGen.configPayload(vmaxRaw = 25))
        assertEquals("VMAX=0.1", QrGen.configPayload(vmaxRaw = 1))
    }

    /**
     * 仪器只认「英文状态下输入」的字符（协议原文），`±` / `Ω` 都是**显示用**符号，
     * 绝不能进二维码。这条测试挡的是「顺手把界面显示名塞进 payload」这类改动。
     */
    @Test
    fun `二维码内容必须是纯 ASCII`() {
        val payload = QrGen.configPayload("QK-HT-001", setTemp = 350, tolerance = 20, vmaxRaw = 25, rmaxRaw = 20)
        assertTrue("出现了非 ASCII 字符：$payload", payload!!.all { it.code in 32..126 })
        val onlyLimits = QrGen.configPayload(vmaxRaw = 25)!!
        assertTrue("出现了非 ASCII 字符：$onlyLimits", onlyLimits.all { it.code in 32..126 })
    }

    @Test
    fun `温度设置显示名是 350±20（± 为 U+00B1，仅用于界面）`() {
        assertEquals("350±20", tempPresetName(350, 20))
        assertEquals("420±10", tempPresetName(420, 10))
    }

    @Test
    fun `上限设置显示名带单位，名称可直接做唯一索引`() {
        assertEquals("2 mV", limitPresetName(LIMIT_VMAX, 20))
        assertEquals("2.5 mV", limitPresetName(LIMIT_VMAX, 25))
        assertEquals("2 Ω", limitPresetName(LIMIT_RMAX, 20))
        // 同一数值在两类里名称不同（带单位）→ 两类各有一档 2 不冲突
        assertTrue(limitPresetName(LIMIT_VMAX, 20) != limitPresetName(LIMIT_RMAX, 20))
        assertEquals("mV", limitUnit(LIMIT_VMAX))
        assertEquals("Ω", limitUnit(LIMIT_RMAX))
    }
}
