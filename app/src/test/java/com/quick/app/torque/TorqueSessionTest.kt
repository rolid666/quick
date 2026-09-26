package com.quick.app.torque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一组三笔的暂存逻辑（2026-09-22 用户定稿的流程）。
 *
 * 这里钉的是四条硬规则：
 * 1. 三笔分开暂存、**满组后的第 4 笔不并入本组**（只记一笔「已忽略」）；
 * 2. 平均值**先按 2 位小数圆整**（判定用的就是这个圆整后的数）；
 * 3. 满组后**不清空**（要等控制器保存完再清），重测才清；
 * 4. **单笔可删**（2026-09-26 用户要求）：删掉一笔后后面的笔往前补位、本组不再满，
 *    下一笔测量自动补齐 —— 「下次记录自动补充清除的测量结果」。
 */
class TorqueSessionTest {

    private val s = TorqueSession(3)

    private fun add(v: Double) = s.add(v, "+%.2fkgf*cm".format(v), "kgf*cm", 1000L + s.count)

    @Test
    fun `三笔依次进来 进度是 1 2 3`() {
        assertEquals(1, add(5.40).seq)
        assertEquals(2, add(6.23).seq)
        val third = add(3.38)
        assertEquals(3, third.seq)
        assertTrue(third.done)
        assertEquals(3, s.count)
        assertTrue(s.isFull)
    }

    @Test
    fun `未满组时没有平均值`() {
        assertNull(add(5.40).average)
        assertNull(s.average())
    }

    @Test
    fun `平均值按两位小数圆整`() {
        add(5.40); add(6.23); add(3.38)
        // (5.40+6.23+3.38)/3 = 5.0033333… → 5.00（不圆整的话界面上会出现 5.003333333333333）
        assertEquals(5.00, s.average()!!, 1e-9)
    }

    @Test
    fun `平均值四舍五入到两位`() {
        add(1.00); add(1.00); add(2.01)
        // 1.3366666… → 1.34
        assertEquals(1.34, s.average()!!, 1e-9)
    }

    @Test
    fun `满组后的第 4 笔被忽略 不并入本组`() {
        add(5.40); add(6.23); add(3.38)
        val step = add(9.99)
        assertTrue(step.ignored)
        assertTrue(step.done)
        assertEquals(3, s.count)                       // 还是三笔
        assertEquals(1, s.ignoredCount)
        assertEquals(listOf(5.40, 6.23, 3.38), s.sample)
        assertEquals(5.00, s.average()!!, 1e-9)        // 平均值不受第 4 笔影响
    }

    @Test
    fun `连续忽略多笔 计数如实累加`() {
        add(1.00); add(1.00); add(1.00)
        add(9.0); add(9.0); add(9.0)
        assertEquals(3, s.ignoredCount)
        assertEquals(3, s.count)
    }

    @Test
    fun `保存前不清空 重测才清空`() {
        add(5.40); add(6.23); add(3.38)
        assertTrue(s.isFull)                            // 满组后仍在（界面在保存前一刻不能看到空的）
        s.reset()
        assertEquals(0, s.count)
        assertFalse(s.isFull)
        assertNull(s.average())
        assertEquals(0, s.ignoredCount)
        assertEquals(0L, s.lastAtMs)
    }

    @Test
    fun `时间取第一笔与最后一笔`() {
        s.add(5.40, "a", "kgf*cm", 100L)
        s.add(6.23, "b", "kgf*cm", 200L)
        s.add(3.38, "c", "kgf*cm", 300L)
        assertEquals(100L, s.startedAtMs)
        assertEquals(300L, s.lastAtMs)                  // 记录/导出的「时间」取第三笔（用户拍板）
    }

    @Test
    fun `每笔原文都留着 且能拼成 hex`() {
        s.add(5.40, "+5.40kgf*cm", "kgf*cm", 100L)
        s.add(6.23, "+6.23kgf*cm", "kgf*cm", 200L)
        s.add(3.38, "+3.38kgf*cm", "kgf*cm", 300L)
        assertEquals(listOf("+5.40kgf*cm", "+6.23kgf*cm", "+3.38kgf*cm"), s.sampleTexts)
        val joined = s.rawHexJoined { TorqueStreamParser.hex(it.toByteArray(Charsets.ISO_8859_1)) }
        assertEquals("2B 35 2E 34 30 6B 67 66 2A 63 6D", joined.split(" | ")[0])
        assertEquals(3, joined.split(" | ").size)
    }

    @Test
    fun `单位取第一笔`() {
        s.add(5.40, "+5.40kgf*cm", "kgf*cm", 100L)
        s.add(6.23, "+6.23kgf*cm", "kgf*cm", 200L)
        assertEquals("kgf*cm", s.unit)
    }

    @Test
    fun `三笔全一样 平均就是那个数`() {
        add(2.50); add(2.50); add(2.50)
        assertEquals(2.50, s.average()!!, 1e-9)
    }

    @Test
    fun `改成五次一组时逻辑不变`() {
        val five = TorqueSession(5)
        repeat(4) { five.add(1.0, "+1.00kgf*cm", "kgf*cm", 10L * it) }
        assertFalse(five.isFull)
        val last = five.add(1.0, "+1.00kgf*cm", "kgf*cm", 50L)
        assertTrue(last.done)
        assertEquals(5, last.seq)
        assertEquals(5, last.total)
    }

    // ---------- 单笔删除（用户 2026-09-26：每个记录右上角的小叉） ----------

    @Test
    fun `删掉中间一笔 后面的笔往前补位 本组不再满`() {
        s.add(5.40, "+5.40kgf*cm", "kgf*cm", 100L)
        s.add(6.23, "+6.23kgf*cm", "kgf*cm", 200L)
        s.add(3.38, "+3.38kgf*cm", "kgf*cm", 300L)

        assertTrue(s.removeAt(1))                    // 删第 2 笔
        assertEquals(2, s.count)
        assertFalse(s.isFull)
        assertEquals(listOf(5.40, 3.38), s.sample)   // 第 3 笔补到了第 2 格，不留空洞
        assertEquals(listOf("+5.40kgf*cm", "+3.38kgf*cm"), s.sampleTexts)
        assertNull(s.average())                      // 不满组 → 没有平均值（不能显示半组的平均）
    }

    @Test
    fun `删一笔后 下一笔自动补齐到满组并按新三笔算平均`() {
        s.add(5.40, "+5.40kgf*cm", "kgf*cm", 100L)
        s.add(6.23, "+6.23kgf*cm", "kgf*cm", 200L)
        s.add(3.38, "+3.38kgf*cm", "kgf*cm", 300L)
        s.removeAt(2)                                // 第 3 笔测坏了，叉掉

        val step = s.add(4.00, "+4.00kgf*cm", "kgf*cm", 400L)
        assertEquals(3, step.seq)                    // 补回第 3 格
        assertTrue(step.done)
        assertTrue(s.isFull)
        assertEquals(listOf(5.40, 6.23, 4.00), s.sample)
        assertEquals(5.21, s.average()!!, 1e-9)      // (5.40+6.23+4.00)/3 = 5.21，被删的 3.38 不参与
    }

    @Test
    fun `删一笔后 时间跟着剩下的笔重算`() {
        s.add(5.40, "a", "kgf*cm", 100L)
        s.add(6.23, "b", "kgf*cm", 200L)
        s.add(3.38, "c", "kgf*cm", 300L)

        s.removeAt(2)                                // 删最后一笔 → 记录的时间退回第 2 笔
        assertEquals(100L, s.startedAtMs)
        assertEquals(200L, s.lastAtMs)

        s.removeAt(0)                                // 删第一笔 → 开始时刻改成剩下那笔
        assertEquals(200L, s.startedAtMs)
        assertEquals(200L, s.lastAtMs)
    }

    @Test
    fun `三笔全删光 时刻回到 0 单位也空`() {
        s.add(5.40, "+5.40kgf*cm", "kgf*cm", 100L)
        s.add(6.23, "+6.23kgf*cm", "kgf*cm", 200L)
        s.add(3.38, "+3.38kgf*cm", "kgf*cm", 300L)
        assertTrue(s.removeAt(0))
        assertTrue(s.removeAt(0))
        assertTrue(s.removeAt(0))

        assertEquals(0, s.count)
        assertFalse(s.isFull)
        assertEquals(0L, s.startedAtMs)               // 一笔都没有 → 不能留着上一笔的时刻
        assertEquals(0L, s.lastAtMs)
        assertEquals("", s.unit)
        assertNull(s.average())
    }

    @Test
    fun `下标越界返回 false 且什么都不动`() {
        add(5.40); add(6.23)
        assertFalse(s.removeAt(-1))
        assertFalse(s.removeAt(2))                    // 只有 2 笔，下标 2 不存在
        assertEquals(2, s.count)
        assertEquals(listOf(5.40, 6.23), s.sample)
    }

    @Test
    fun `空组上删笔也不会出错`() {
        assertFalse(s.removeAt(0))
        assertEquals(0, s.count)
        assertEquals(0L, s.lastAtMs)
    }

    @Test
    fun `满组后删一笔 后面被忽略的笔不会自动补进来`() {
        add(1.00); add(1.00); add(1.00)
        add(9.99)                                     // 第 4 笔已忽略
        assertEquals(1, s.ignoredCount)

        s.removeAt(0)
        assertEquals(2, s.count)
        assertEquals(1, s.ignoredCount)               // 「忽略过几笔」是发生过的事实，删笔不能抹掉
        assertNull(s.average())                       // 删一笔后仍然不满组 → 没有平均值
    }
}
