package com.quick.app.torque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扫码内容 → 设备信息名（用户要求 4：设备信息要能调后置摄像头扫码添加）。
 *
 * 关键在于「扫到的不等于要存的」：本应用自己的配置二维码是 `SN=QK-HT-001,TSET=350,…`
 * 这种复合指令串（厂家《扫码配置指令》），扫到它时操作员要的是**编号本身**。
 * 存整串的后果是设备信息字典里出现一堆带温度的怪名字，而且一条也选不中。
 */
class TorqueScanTest {

    @Test
    fun `扫到本应用自己的复合配置码 —— 只取 SN 的值`() {
        assertEquals("QK-HT-001", TorqueScan.extractName("SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370"))
        assertEquals("QK-HT-001", TorqueScan.extractName("SN=QK-HT-001,TSET=350"))
    }

    @Test
    fun `SN 写在中间也能取到，且到下一个逗号为止`() {
        assertEquals("QK-HT-002", TorqueScan.extractName("TSET=350,SN=QK-HT-002,TMAX=370"))
    }

    @Test
    fun `大小写不敏感 —— 现场贴的标签什么写法都有`() {
        assertEquals("QK-HT-003", TorqueScan.extractName("sn=QK-HT-003,tset=350"))
    }

    @Test
    fun `SN 在串尾（后面没有逗号）—— 取到结尾`() {
        assertEquals("QK-HT-004", TorqueScan.extractName("TSET=350,SN=QK-HT-004"))
    }

    @Test
    fun `没有 SN —— 整串就是名字`() {
        assertEquals("TQ-2026-001", TorqueScan.extractName("TQ-2026-001"))
        assertEquals("设备A", TorqueScan.extractName("  设备A  "))
    }

    @Test
    fun `换行与连续空格折成一个空格 —— 名字里不能带控制字符`() {
        assertEquals("TQ 2026 001", TorqueScan.extractName("TQ\n2026\t001"))
    }

    @Test
    fun `空内容不新增（返回 null，调用方据此什么都不做）`() {
        assertNull(TorqueScan.extractName(null))
        assertNull(TorqueScan.extractName(""))
        assertNull(TorqueScan.extractName("   "))
        assertNull("SN= 后面是空的，不算一个名字", TorqueScan.extractName("SN=,TSET=350"))
    }

    @Test
    fun `超长内容截断 —— 不让一个乱码二维码撑爆字典`() {
        val long = "X".repeat(200)
        assertEquals(64, TorqueScan.extractName(long)!!.length)
        assertTrue(TorqueScan.extractName(long)!!.all { it == 'X' })
    }
}
