package com.quick.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扭力计数据口径（单位 / 数值格式 / 范围命名 / 判定）—— 用户 2026-09-22 的要求 5、6。
 *
 * 这些函数是**界面、入库、导出、备份共用的唯一口径**，
 * 所以每一条都值得钉住：口径一旦分叉，屏幕上、Excel 里、备份里会出现三种说法。
 */
class TorqueDataTest {

    // ---------- 数值格式 ----------

    @Test
    fun `数值固定两位小数`() {
        assertEquals("5.40", torqueValueText(5.4))
        assertEquals("0.00", torqueValueText(0.0))
        assertEquals("12.35", torqueValueText(12.345))
        assertEquals("-2.05", torqueValueText(-2.05))
    }

    @Test
    fun `空值显示两个短横 不显示 0`() {
        // 0.00 是「测出来就是 0」，-- 是「没有这个数」—— 两者绝不能混
        assertEquals("--", torqueValueText(null))
    }

    @Test
    fun `范围名称里的数字去尾零`() {
        assertEquals("0.5", torqueNumText(0.5))
        assertEquals("6", torqueNumText(6.0))
        assertEquals("6.25", torqueNumText(6.25))
    }

    // ---------- 范围命名（用户要求 5：填两个数，名称自动生成）----------

    @Test
    fun `范围名称由上下限自动生成 并带单位`() {
        assertEquals("0.5~6 kgf*cm", torqueRangeName(0.5, 6.0))
        assertEquals("1~5 kgf*cm", torqueRangeName(1.0, 5.0))
        assertEquals("2.25~8.5 kgf*cm", torqueRangeName(2.25, 8.5))
    }

    @Test
    fun `同样的两个数永远生成同一个名字`() {
        // 名字是唯一索引的一部分：生成规则必须稳定，否则导入备份会重复建条目
        assertEquals(torqueRangeName(0.5, 6.0), torqueRangeName(0.50, 6.00))
    }

    @Test
    fun `单位常量与设备实测一致`() {
        assertEquals("kgf*cm", TORQUE_UNIT)
    }

    // ---------- 判定（用户要求 6：范围内 OK，否则 NG）----------

    @Test
    fun `平均值在范围内判 OK`() {
        assertEquals(TORQUE_OK, torqueJudge(5.00, 0.5, 6.0))
        assertEquals(TORQUE_OK, torqueJudge(0.5, 0.5, 6.0))
        assertEquals(TORQUE_OK, torqueJudge(6.0, 0.5, 6.0))
    }

    @Test
    fun `边界算 OK 闭区间`() {
        assertEquals(TORQUE_OK, torqueJudge(1.0, 1.0, 1.0))
    }

    @Test
    fun `超出范围判 NG`() {
        assertEquals(TORQUE_NG, torqueJudge(6.01, 0.5, 6.0))
        assertEquals(TORQUE_NG, torqueJudge(0.49, 0.5, 6.0))
        assertEquals(TORQUE_NG, torqueJudge(-1.0, 0.5, 6.0))
    }

    @Test
    fun `缺任何一个值都不判定 不编造`() {
        assertNull(torqueJudge(null, 0.5, 6.0))
        assertNull(torqueJudge(5.0, null, 6.0))
        assertNull(torqueJudge(5.0, 0.5, null))
    }

    // ---------- 记录 ----------

    @Test
    fun `记录的显示文本与判定`() {
        val rec = TorqueRecord(
            sessionId = "s1", timestampMs = 1L, savedAtMs = 2L,
            lineName = "A线", modelName = "M1", rangeName = "0.5~6 kgf*cm",
            rangeMin = 0.5, rangeMax = 6.0, deviceName = "QK-HT-001",
            v1 = 5.40, v2 = 6.23, v3 = 3.38, average = 5.00,
            judge = TORQUE_OK, unitText = TORQUE_UNIT
        )
        assertEquals("5.00", rec.averageText)
        assertEquals("5.40", rec.v1Text)
        assertEquals("3.38", rec.v3Text)
        assertEquals(true, rec.isOk)
        assertEquals(3, rec.sampleCount)
    }

    @Test
    fun `旧数据没有判定时 isOk 为 null`() {
        val old = TorqueRecord(
            sessionId = "s0", timestampMs = 1L, savedAtMs = 2L,
            lineName = "", modelName = "", rangeName = "", deviceName = "",
            rangeMin = null, rangeMax = null,
            v1 = null, v2 = null, v3 = null, average = null,
            judge = ""
        )
        assertNull(old.isOk)
        assertFalse(old.isOk == true)
        assertEquals("--", old.averageText)
    }

    @Test
    fun `字典类别的中文名齐全`() {
        assertEquals("线别", torqueDictLabel(TQ_LINE))
        assertEquals("机种", torqueDictLabel(TQ_MODEL))
        assertEquals("扭矩范围", torqueDictLabel(TQ_RANGE))
        assertEquals("设备信息", torqueDictLabel(TQ_DEVICE))
        assertTrue(TORQUE_DICT_KINDS.containsAll(listOf(TQ_LINE, TQ_MODEL, TQ_RANGE, TQ_DEVICE)))
    }
}
