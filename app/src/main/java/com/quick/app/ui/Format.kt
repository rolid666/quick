package com.quick.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HMS = SimpleDateFormat("HH:mm:ss", Locale.US)
private val FULL = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)

fun hms(ms: Long): String = synchronized(HMS) { HMS.format(Date(ms)) }
fun fullTime(ms: Long): String = synchronized(FULL) { FULL.format(Date(ms)) }
fun dateOnly(ms: Long): String = synchronized(DATE) { DATE.format(Date(ms)) }

/** 测量温度：寄存器 ×0.1 ℃，按 0.1 ℃ 展示（null → "--"） */
fun tempText(v: Double?): String = if (v == null) "--" else String.format(Locale.US, "%.1f ℃", v)

/** 温度类整数：目标温度 0x1B / 上下限 0x1C~0x1D，仪器按整数 ℃ 报值 */
fun intTempText(v: Int?): String = if (v == null) "--" else "$v ℃"

/** 实时温度（同 tempText，语义别名，便于阅读） */
fun liveTempText(v: Double?): String = tempText(v)

/** 漏地电压：寄存器 ×0.1 mV */
fun leakText(v: Double?): String = if (v == null) "--" else String.format(Locale.US, "%.1f mV", v)

/** 温度范围："下限 ~ 上限 ℃"，两端都缺省时为 "--" */
fun rangeText(low: Int?, high: Int?): String =
    if (low == null && high == null) "--" else "${low ?: "--"} ~ ${high ?: "--"} ℃"
