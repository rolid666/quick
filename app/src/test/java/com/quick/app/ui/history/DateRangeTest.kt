package com.quick.app.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 日历 → 查询区间换算（东八区固定用 Asia/Shanghai，结果与跑测机器时区无关）。
 *
 * 这组测试守的是一条**会静默丢记录**的边界：把 DatePicker 的 UTC 毫秒当本地用，
 * 「选 16 日」会漏掉 16 日 00:00~08:00 的全部记录。
 */
class DateRangeTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun ms(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `本机当天0点 转成 DatePicker 需要的 UTC 当天0点`() {
        val localDay = ms("2026-09-15T16:00:00Z")          // = 2026-09-16 00:00 +08:00
        assertEquals(ms("2026-09-16T00:00:00Z"), DateRange.utcDayOf(localDay, shanghai))
    }

    @Test
    fun `DatePicker 返回的 UTC 当天0点 转回本机当天0点`() {
        val picked = ms("2026-09-16T00:00:00Z")            // 日历上选 2026-09-16
        assertEquals(ms("2026-09-15T16:00:00Z"), DateRange.localDayOf(picked, shanghai))
    }

    @Test
    fun `不换算就会偏移 8 小时 —— 回归防线`() {
        val picked = ms("2026-09-16T00:00:00Z")
        // 东八区下必须换算；相等说明又退回了「直接拿 UTC 当本地用」的写法
        assertNotEquals(picked, DateRange.localDayOf(picked, shanghai))
    }

    @Test
    fun `截止日期含当天最后一毫秒`() {
        val localDay = ms("2026-09-15T16:00:00Z")          // 2026-09-16 00:00 +08:00
        assertEquals(ms("2026-09-16T15:59:59.999Z"), DateRange.endOfDay(localDay, shanghai))
    }

    @Test
    fun `往返换算稳定`() {
        val localDay = ms("2026-09-15T16:00:00Z")
        assertEquals(localDay, DateRange.localDayOf(DateRange.utcDayOf(localDay, shanghai), shanghai))
    }

    @Test
    fun `选中当天凌晨3点的记录落在区间内`() {
        val start = DateRange.localDayOf(ms("2026-09-16T00:00:00Z"), shanghai)   // 选开始日 09-16
        val end = DateRange.endOfDay(DateRange.localDayOf(ms("2026-09-16T00:00:00Z"), shanghai))
        val rec = ms("2026-09-15T19:00:00Z")               // = 2026-09-16 03:00 +08:00
        assertTrue("凌晨记录必须在当天区间内（曾因时区偏移被漏掉）", rec in start..end)
    }

    @Test
    fun `相邻两天不互相越界`() {
        val d16 = DateRange.localDayOf(ms("2026-09-16T00:00:00Z"), shanghai)
        val d17 = DateRange.localDayOf(ms("2026-09-17T00:00:00Z"), shanghai)
        // 16 日区间上界 + 1ms 正好是 17 日 0 点，二者首尾相接且不重叠
        assertEquals(d17, DateRange.endOfDay(d16, shanghai) + 1)
    }
}
