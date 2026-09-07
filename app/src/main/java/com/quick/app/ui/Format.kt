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

/** 温度展示：仪器按整数 ℃ 报值（0x1D），此处按原样展示 */
fun tempText(v: Int?): String = if (v == null) "--" else "$v ℃"

fun liveTempText(v: Double?): String = if (v == null) "--" else String.format(Locale.US, "%.1f ℃", v)
