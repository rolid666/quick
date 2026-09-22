package com.quick.app.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.quick.app.QuickApp
import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.export.XlsxExporter
import com.quick.app.net.channelText
import com.quick.app.security.DeletePassword
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.dateOnly
import com.quick.app.ui.fullTime
import com.quick.app.ui.intTempText
import com.quick.app.ui.ohmText
import com.quick.app.ui.rangeText
import com.quick.app.ui.tempText
import com.quick.app.ui.voltText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OkGreen = Color(0xFF2E7D32)
private val NgRed = Color(0xFFC62828)
private val InvalidGrey = Color(0xFF616161)

/** 结果配色/文案：仪器 0x23 的三种取值各有一种展示，不把「无效」混入 OK 或 NG */
private fun resultColorOf(rec: MeasurementRecord): Color = when {
    rec.isOk -> OkGreen
    rec.isInvalid -> InvalidGrey
    else -> NgRed
}

private fun resultLabelOf(rec: MeasurementRecord): String = when {
    rec.isOk -> "✓ OK"
    rec.isInvalid -> "— 无效"
    else -> "✕ NG"
}

/** 线别 / 机种 / 站别一行文字，空值用 — 占位 */
private fun seatText(rec: MeasurementRecord): String =
    "${rec.lineName.ifBlank { "—" }} / ${rec.modelName.ifBlank { "—" }} / ${rec.stationName.ifBlank { "—" }}"

/**
 * 筛选条件（全部为空 = 不过滤）。
 * 日期用「本机时区当天 00:00:00」存储 —— 直接对应日历上选中的那一天，便于回显；
 * 查询时截止日期换算成当天 23:59:59.999（含当天），换算见 [DateRange]。
 */
private data class Filters(
    val startDayMs: Long? = null,
    val endDayMs: Long? = null,
    val line: String? = null,
    val model: String? = null,
    val station: String? = null,
    val result: String? = null,
    val kw: String = ""
) {
    val kwOrNull: String? get() = kw.trim().ifEmpty { null }

    /** 开始日期 → 当天 0 点；截止日期 → 当天最后一毫秒（含当天） */
    val fromMs: Long? get() = startDayMs
    val toMs: Long? get() = endDayMs?.let { DateRange.endOfDay(it) }

    val hasDate: Boolean get() = startDayMs != null || endDayMs != null
    val isEmpty: Boolean
        get() = startDayMs == null && endDayMs == null && line == null && model == null &&
            station == null && result == null && kw.isBlank()
}

/** 待删除目标：单条 / 按当前筛选批量 */
private sealed interface DeleteTarget {
    data class One(val rec: MeasurementRecord) : DeleteTarget
    data object ByFilter : DeleteTarget
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    val lines by db.lineDao().all().collectAsState(initial = emptyList())
    val models by db.modelDao().all().collectAsState(initial = emptyList())
    val stations by db.stationDao().all().collectAsState(initial = emptyList())
    val lineOptions = remember(lines) { lines.map { it.name } }
    val modelOptions = remember(models) { models.map { it.name } }
    val stationOptions = remember(stations) { stations.map { it.name } }

    var filters by remember { mutableStateOf(Filters()) }
    var selected by remember { mutableStateOf<MeasurementRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    // 查询条件与删除条件共用同一组参数 —— 「看到的就是要删的」
    val records by remember(filters) {
        db.recordDao().query(
            filters.fromMs, filters.toMs, filters.line, filters.model,
            filters.station, filters.result, filters.kwOrNull
        )
    }.collectAsState(initial = emptyList())
    val total by remember(filters) {
        db.recordDao().count(
            filters.fromMs, filters.toMs, filters.line, filters.model,
            filters.station, filters.result, filters.kwOrNull
        )
    }.collectAsState(initial = 0)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 响应式：≥600dp（平板）筛选一行排开；手机窄屏自动换行
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
            val wide = maxWidth >= 600.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text("历史记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { pendingDelete = DeleteTarget.ByFilter },
                        enabled = records.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.padding(end = 6.dp))
                        Text(if (wide) "删除记录" else "删除")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { exportRecords(ctx, app, records) } },
                        enabled = records.isNotEmpty()
                    ) {
                        Icon(Icons.Default.FileDownload, null, Modifier.padding(end = 6.dp))
                        Text(if (wide) "导出 Excel（当前筛选）" else "导出 Excel")
                    }
                }

                // 筛选卡片
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        // ── 第一行：两个日历（开始 / 截止）──
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateField(
                                label = "开始日期",
                                dayMs = filters.startDayMs,
                                onPick = { filters = filters.copy(startDayMs = it) },
                                modifier = Modifier.width(190.dp)
                            )
                            DateField(
                                label = "截止日期",
                                dayMs = filters.endDayMs,
                                onPick = { filters = filters.copy(endDayMs = it) },
                                modifier = Modifier.width(190.dp)
                            )
                        }

                        // ── 已选日期回显（「选完的时间要显示出来」）+ 清除 + 命中条数 ──
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    filters.hasDate -> "已选：" + dateRangeText(filters)
                                    else -> "未选择日期 = 全部时间"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (filters.hasDate) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { filters = filters.copy(startDayMs = null, endDayMs = null) },
                                enabled = filters.hasDate) { Text("清除日期") }
                            Text("共 $total 条", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        OutlinedTextField(
                            value = filters.kw,
                            onValueChange = { filters = filters.copy(kw = it) },
                            placeholder = { Text("关键词（线别 / 机种 / 站别 / 设备信息）") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )

                        // ── 结果筛选 ──
                        FlowRow(
                            Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResultChip("OK", filters.result == "OK") {
                                filters = filters.copy(result = if (it) "OK" else null)
                            }
                            ResultChip("NG", filters.result == "NG") {
                                filters = filters.copy(result = if (it) "NG" else null)
                            }
                            ResultChip("无效", filters.result == "无效") {
                                filters = filters.copy(result = if (it) "无效" else null)
                            }
                        }

                        // ── 三个平级下拉（线别 / 机种 / 站别）+ 清空 ──
                        FlowRow(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SeatSelect("线别", lineOptions, filters.line,
                                { filters = filters.copy(line = it) }, Modifier.width(160.dp))
                            SeatSelect("机种", modelOptions, filters.model,
                                { filters = filters.copy(model = it) }, Modifier.width(160.dp))
                            SeatSelect("站别", stationOptions, filters.station,
                                { filters = filters.copy(station = it) }, Modifier.width(160.dp))
                        }
                        TextButton(onClick = { filters = Filters() }, enabled = !filters.isEmpty) {
                            Text("清空筛选")
                        }
                    }
                }
            }
        }

        // 列表
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(records, key = { it.id }) { rec ->
                RecordRow(rec) { selected = rec }
            }
            if (records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("没有符合条件的记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (total > records.size) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("仅显示前 ${records.size} 条（共 $total 条），请用日期或筛选缩短范围后再删除",
                            color = NgRed, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    selected?.let { rec ->
        DetailDialog(
            rec = rec,
            onDismiss = { selected = null },
            onDelete = { selected = null; pendingDelete = DeleteTarget.One(rec) }
        )
    }

    pendingDelete?.let { target ->
        DeleteWithPasswordDialog(
            message = if (target is DeleteTarget.One)
                "将永久删除这条记录，不可恢复：\n\n" +
                    "${fullTime(target.rec.timestampMs)}\n${seatText(target.rec)}\n" +
                    "结果 ${target.rec.result}　温度 ${tempText(target.rec.measuredTemp)}"
            else
                "将永久删除当前筛选出的 $total 条记录，不可恢复。\n\n" +
                    "筛选：" + filterSummary(filters, total) +
                    if (total > records.size)
                        "\n⚠ 列表只显示前 ${records.size} 条，删除会作用于全部 $total 条"
                    else "\n（删除口径与上方列表完全一致：看到的就是要删的）",
            db = db,
            onDismiss = { pendingDelete = null },
            perform = {
                if (target is DeleteTarget.One) db.recordDao().deleteById(target.rec.id)
                else db.recordDao().deleteByFilter(
                    filters.fromMs, filters.toMs, filters.line, filters.model,
                    filters.station, filters.result, filters.kwOrNull
                )
            }
        )
    }
}

/** 「2026-09-01 ~ 2026-09-16」；只选一端时给出开放区间提示 */
private fun dateRangeText(f: Filters): String {
    val s = f.startDayMs?.let { dateOnly(it) }
    val e = f.endDayMs?.let { dateOnly(it) }
    return when {
        s != null && e != null -> "$s ~ $e"
        s != null -> "$s 起（至最新）"
        e != null -> "最早 ~ $e"
        else -> "全部时间"
    }
}

private fun filterSummary(f: Filters, matched: Int): String {
    val parts = mutableListOf<String>()
    parts += "时间=${dateRangeText(f)}"
    f.line?.let { parts += "线别=$it" }
    f.model?.let { parts += "机种=$it" }
    f.station?.let { parts += "站别=$it" }
    f.result?.let { parts += "结果=$it" }
    f.kwOrNull?.let { parts += "关键词=$it" }
    parts += "命中 $matched 条"
    return parts.joinToString("　")
}

// ---------- 日历选择 ----------

/**
 * 日期选择：点一下弹日历，选完把日期文字显示在栏位上（用户要求"选完的时间要显示出来"）。
 * Material3 DatePicker 的毫秒值是 **UTC 当天 0 点**，这里与本地日期互转，
 * 避免时区偏移导致日历上少一天/多一天。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    dayMs: Long?,
    onPick: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.clickable { show = true },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = dayMs?.let { dateOnly(it) } ?: "未选择",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (dayMs != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (dayMs != null) {
                IconButton(onClick = { onPick(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "清除$label", Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.DateRange, contentDescription = "选择$label",
                    Modifier.padding(end = 10.dp))
            }
        }
    }
    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dayMs?.let { DateRange.utcDayOf(it) },
            yearRange = 2020..2100
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onPick(DateRange.localDayOf(it)) }
                        show = false
                    },
                    enabled = state.selectedDateMillis != null
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

// 时区换算见 DateRange（有单测覆盖，别在这里就地重写 —— 偏移 8 小时会静默漏记录）

// ---------- 删除（需密码） ----------

/**
 * 删除确认 + 口令校验。
 * 密码只校验不落盘；[DeletePassword] 存的是盐 + PBKDF2 哈希，且可在配置页修改，
 * 出厂默认口令首次修改后即失效。删除条数以数据库实际影响行数为准（不按预估报数）。
 */
@Composable
private fun DeleteWithPasswordDialog(
    message: String,
    db: AppDatabase,
    onDismiss: () -> Unit,
    perform: suspend () -> Int
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pwd by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("删除记录（需密码）") },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; err = null },
                    label = { Text("删除密码") },
                    singleLine = true,
                    enabled = !busy,
                    isError = err != null,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { err?.let { Text(it, color = NgRed) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pwd.isNotEmpty() && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            if (!DeletePassword.verify(db, pwd)) {
                                err = "密码错误"
                                busy = false
                                return@launch
                            }
                            val n = perform()
                            Toast.makeText(ctx, "已删除 $n 条记录", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } catch (e: Exception) {
                            err = "删除失败：${e.message ?: e.javaClass.simpleName}"
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "处理中…" else "确认删除") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}

// ---------- 列表 ----------

@Composable
private fun ResultChip(label: String, selected: Boolean, onClick: (Boolean) -> Unit) {
    val c = when (label) {
        "OK" -> OkGreen
        "无效" -> InvalidGrey
        else -> NgRed
    }
    FilterChip(
        selected = selected, onClick = { onClick(!selected) }, label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = c.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun SeatSelect(
    placeholder: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchableSelect(
        label = placeholder,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
private fun RecordRow(rec: MeasurementRecord, onClick: () -> Unit) {
    val color = resultColorOf(rec)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(fullTime(rec.timestampMs), style = MaterialTheme.typography.bodyMedium)
                Text(dateOnly(rec.timestampMs), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(seatText(rec), style = MaterialTheme.typography.bodyLarge)
                Text("编号 ${rec.deviceInfo?.ifBlank { "—" } ?: "—"}　通道 ${channelText(rec.channel)}　" +
                    "电压 ${voltText(rec.measuredVoltageMv)}　电阻 ${ohmText(rec.measuredResistanceOhm)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                Text(resultLabelOf(rec), color = color,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tempText(rec.measuredTemp), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun DetailDialog(rec: MeasurementRecord, onDismiss: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("记录详情　#${rec.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(4.dp))
                DetailLine("结果", rec.result, color = resultColorOf(rec))
                DetailLine("时间", fullTime(rec.timestampMs))
                DetailLine("线别", rec.lineName.ifBlank { "—" })
                DetailLine("机种", rec.modelName.ifBlank { "—" })
                DetailLine("站别", rec.stationName.ifBlank { "—" })
                DetailLine("设备编号", rec.deviceInfo?.ifBlank { "—" } ?: "—")
                DetailLine("设备 IP", rec.deviceIp)
                // 明细只给换算好的值：寄存器原始地址/数值在现场没人看（用户 2026-09 要求），
                // 需要核对寄存器的话进「诊断」页看帧日志
                DetailLine("测量通道", channelText(rec.channel))
                DetailLine("设定温度", intTempText(rec.targetTemp))
                DetailLine("合格区间", rangeText(rec.tempLow, rec.tempHigh))
                DetailLine("电压上限", voltText(rec.voltageLimitMv))
                DetailLine("电阻上限", ohmText(rec.resistanceLimitOhm))
                DetailLine("测量温度", tempText(rec.measuredTemp))
                DetailLine("测量电压", voltText(rec.measuredVoltageMv))
                DetailLine("测量电阻", ohmText(rec.measuredResistanceOhm))
                Spacer(Modifier.padding(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = NgRed)
                        Text(" 删除此条", color = NgRed)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, Modifier.width(90.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------- 导出 ----------

private fun exportRecords(ctx: Context, app: QuickApp, records: List<MeasurementRecord>) {
    if (records.isEmpty()) return
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
    val file = File(dir, "测量记录_$stamp.xlsx")
    try {
        XlsxExporter.export(records, file)
        shareFile(ctx, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        // 通知 UI 线程
        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, "已导出：${records.size} 条 → ${file.name}", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun shareFile(ctx: Context, file: File, mime: String) {
    val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
}
