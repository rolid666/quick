package com.quick.app.ui.history

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 日历上选中的「那一天」 ⇄ 查询用的时间戳区间。
 *
 * ⚠️ Material3 `DatePicker` 的 `selectedDateMillis` 是 **UTC 当天 0 点**，
 * 而筛选要的是**本机时区**的「当天 0 点 ~ 当天最后一毫秒」。
 * 若把 UTC 值直接当本地时间用，东八区会整体偏移 8 小时 ——
 * 「选 9 月 16 日」实际只查到 16 日 08:00 以后，当天早上 00:00~08:00 的记录被静默漏掉。
 * 所以两个方向的换算都必须显式做（见 [DateRangeTest]）。
 *
 * [zone] 参数只为测试可注入；业务调用一律用本机时区。
 */
internal object DateRange {

    /** DatePicker 返回的 UTC 当天 0 点 → 本机时区「当天 0 点」 */
    fun localDayOf(utcDayMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(utcDayMs).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** 本机时区「当天 0 点」 → DatePicker 回显需要的 UTC 当天 0 点 */
    fun utcDayOf(localDayMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(localDayMs).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 「截止日期」的查询上界：**含当天**（当天 23:59:59.999），按 zone 计算以兼容夏令时 */
    fun endOfDay(localDayMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(localDayMs).atZone(zone).toLocalDate()
            .atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
}
