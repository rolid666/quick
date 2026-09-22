package com.quick.app.ui.torque

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.quick.app.data.db.TQ_DEVICE
import com.quick.app.data.db.TQ_LINE
import com.quick.app.data.db.TQ_MODEL
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TORQUE_NG
import com.quick.app.data.db.TORQUE_OK
import com.quick.app.data.db.TorqueRecord
import com.quick.app.data.db.torqueValueText
import com.quick.app.export.TorqueXlsxExporter
import com.quick.app.security.DeletePassword
import com.quick.app.ui.InvalidGrey
import com.quick.app.ui.NgRed
import com.quick.app.ui.OkGreen
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.dateOnly
import com.quick.app.ui.fullTime
import com.quick.app.ui.history.DateRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 扭力计历史记录（用户要求 3：参考烙铁那页）。
 *
 * 与烙铁记录页的关系：**同一套交互、各自一套代码与数据**。
 * 这里不 import 烙铁记录页的任何东西（连删除对话框都是本地一份），
 * 因为改这一页时绝不能有机会碰到烙铁的查询/删除口径。
 *
 * **一行 = 一次测量组**（三笔 + 平均 + 判定，2026-09-22 改）：
 * 现场看的就是「这一组平均多少、判 OK 还是 NG」，
 * 所以列表主字是平均值，右侧徽标直接是判定色（绿 OK / 红 NG / 灰 未判定）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorqueHistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    val dicts by db.torqueDictDao().allKinds().collectAsState(initial = emptyList())
    val lineOptions = remember(dicts) { dicts.filter { it.kind == TQ_LINE }.map { it.name } }
    val modelOptions = remember(dicts) { dicts.filter { it.kind == TQ_MODEL }.map { it.name } }
    val rangeOptions = remember(dicts) { dicts.filter { it.kind == TQ_RANGE }.map { it.name } }
    val deviceOptions = remember(dicts) { dicts.filter { it.kind == TQ_DEVICE }.map { it.name } }

    var filters by remember { mutableStateOf(TorqueFilters()) }
    var selected by remember { mutableStateOf<TorqueRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    // 查询条件与删除条件共用同一组参数 —— 「看到的就是要删的」
    val records by remember(filters) {
        db.torqueRecordDao().query(
            filters.fromMs, filters.toMs, filters.line, filters.model,
            filters.range, filters.device, filters.judge, filters.kwOrNull
        )
    }.collectAsState(initial = emptyList())
    val total by remember(filters) {
        db.torqueRecordDao().count(
            filters.fromMs, filters.toMs, filters.line, filters.model,
            filters.range, filters.device, filters.judge, filters.kwOrNull
        )
    }.collectAsState(initial = 0)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("扭力计记录", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { pendingDelete = DeleteTarget.ByFilter },
                enabled = records.isNotEmpty()
            ) {
                Icon(Icons.Default.Delete, null, Modifier.padding(end = 6.dp)); Text("删除记录")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { scope.launch(Dispatchers.IO) { exportRecords(ctx, records) } },
                enabled = records.isNotEmpty()
            ) {
                Icon(Icons.Default.FileDownload, null, Modifier.padding(end = 6.dp))
                Text("导出 Excel（当前筛选）")
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TorqueDateField("开始日期", filters.startDayMs,
                        { filters = filters.copy(startDayMs = it) }, Modifier.width(190.dp))
                    TorqueDateField("截止日期", filters.endDayMs,
                        { filters = filters.copy(endDayMs = it) }, Modifier.width(190.dp))
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (filters.hasDate) "已选：" + dateRangeText(filters) else "未选择日期 = 全部时间",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (filters.hasDate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { filters = filters.copy(startDayMs = null, endDayMs = null) },
                        enabled = filters.hasDate
                    ) { Text("清除日期") }
                    Text("共 $total 组", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OutlinedTextField(
                    value = filters.kw,
                    onValueChange = { filters = filters.copy(kw = it) },
                    placeholder = { Text("关键词（线别 / 机种 / 扭矩范围 / 设备信息 / 原始报文）") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )

                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 判定筛选（用户要求 6：范围内 OK、否则 NG）
                    FilterChip(
                        selected = filters.judge == null,
                        onClick = { filters = filters.copy(judge = null) },
                        label = { Text("全部") }
                    )
                    FilterChip(
                        selected = filters.judge == TORQUE_OK,
                        onClick = { filters = filters.copy(judge = TORQUE_OK) },
                        label = { Text("仅 OK") }
                    )
                    FilterChip(
                        selected = filters.judge == TORQUE_NG,
                        onClick = { filters = filters.copy(judge = TORQUE_NG) },
                        label = { Text("仅 NG") }
                    )
                    SearchableSelect("线别", lineOptions, filters.line,
                        { filters = filters.copy(line = it) }, Modifier.width(160.dp))
                    SearchableSelect("机种", modelOptions, filters.model,
                        { filters = filters.copy(model = it) }, Modifier.width(160.dp))
                    SearchableSelect("扭矩范围", rangeOptions, filters.range,
                        { filters = filters.copy(range = it) }, Modifier.width(160.dp))
                    SearchableSelect("设备信息", deviceOptions, filters.device,
                        { filters = filters.copy(device = it) }, Modifier.width(160.dp))
                }
                TextButton(onClick = { filters = TorqueFilters() }, enabled = !filters.isEmpty) {
                    Text("清空筛选")
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(records, key = { it.id }) { rec -> TorqueRecordRow(rec) { selected = rec } }
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
        TorqueDetailDialog(
            rec = rec,
            onDismiss = { selected = null },
            onDelete = { selected = null; pendingDelete = DeleteTarget.One(rec) }
        )
    }

    pendingDelete?.let { target ->
        TorqueDeleteDialog(
            message = if (target is DeleteTarget.One)
                "将永久删除这组记录，不可恢复：\n\n" +
                    "${fullTime(target.rec.timestampMs)}\n${seatText(target.rec)}\n" +
                    "三笔 ${target.rec.v1Text} / ${target.rec.v2Text} / ${target.rec.v3Text}　" +
                    "平均 ${target.rec.averageText} ${target.rec.unitText}　${judgeLabel(target.rec)}"
            else
                "将永久删除当前筛选出的 $total 组记录，不可恢复。\n\n" +
                    "筛选：" + filterSummary(filters, total) +
                    if (total > records.size)
                        "\n⚠ 列表只显示前 ${records.size} 条，删除会作用于全部 $total 条"
                    else "\n（删除口径与上方列表完全一致：看到的就是要删的）",
            db = db,
            onDismiss = { pendingDelete = null },
            perform = {
                if (target is DeleteTarget.One) db.torqueRecordDao().deleteById(target.rec.id)
                else db.torqueRecordDao().deleteByFilter(
                    filters.fromMs, filters.toMs, filters.line, filters.model,
                    filters.range, filters.device, filters.judge, filters.kwOrNull
                )
            }
        )
    }
}

// ---------- 筛选 ----------

private data class TorqueFilters(
    val startDayMs: Long? = null,
    val endDayMs: Long? = null,
    val line: String? = null,
    val model: String? = null,
    val range: String? = null,
    val device: String? = null,
    /** `null` = 全部；`OK` / `NG` */
    val judge: String? = null,
    val kw: String = ""
) {
    val kwOrNull: String? get() = kw.trim().ifEmpty { null }
    val fromMs: Long? get() = startDayMs
    val toMs: Long? get() = endDayMs?.let { DateRange.endOfDay(it) }
    val hasDate: Boolean get() = startDayMs != null || endDayMs != null
    val isEmpty: Boolean
        get() = startDayMs == null && endDayMs == null && line == null && model == null &&
            range == null && device == null && judge == null && kw.isBlank()
}

private sealed interface DeleteTarget {
    data class One(val rec: TorqueRecord) : DeleteTarget
    data object ByFilter : DeleteTarget
}

private fun seatText(rec: TorqueRecord): String =
    "${rec.lineName.ifBlank { "—" }} / ${rec.modelName.ifBlank { "—" }} / ${rec.rangeName.ifBlank { "—" }}"

private fun dateRangeText(f: TorqueFilters): String {
    val s = f.startDayMs?.let { dateOnly(it) }
    val e = f.endDayMs?.let { dateOnly(it) }
    return when {
        s != null && e != null -> "$s ~ $e"
        s != null -> "$s 起（至最新）"
        e != null -> "最早 ~ $e"
        else -> "全部时间"
    }
}

private fun filterSummary(f: TorqueFilters, matched: Int): String {
    val parts = mutableListOf<String>()
    parts += "时间=${dateRangeText(f)}"
    f.line?.let { parts += "线别=$it" }
    f.model?.let { parts += "机种=$it" }
    f.range?.let { parts += "扭矩范围=$it" }
    f.device?.let { parts += "设备信息=$it" }
    f.judge?.let { parts += "判定=$it" }
    f.kwOrNull?.let { parts += "关键词=$it" }
    parts += "命中 $matched 组"
    return parts.joinToString("　")
}

// ---------- 列表 ----------

@Composable
private fun TorqueRecordRow(rec: TorqueRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(fullTime(rec.timestampMs), style = MaterialTheme.typography.bodyMedium)
                Text(dateOnly(rec.timestampMs), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(seatText(rec), style = MaterialTheme.typography.bodyLarge)
                Text("设备 ${rec.deviceName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("三笔 ${rec.v1Text} / ${rec.v2Text} / ${rec.v3Text}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            JudgeChip(rec)
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(rec.averageText, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("平均 ${rec.unitText}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

private fun judgeLabel(rec: TorqueRecord): String = when (rec.judge) {
    TORQUE_OK -> "OK"
    TORQUE_NG -> "NG"
    else -> "未判定"
}

private fun judgeColor(rec: TorqueRecord): Color = when (rec.judge) {
    TORQUE_OK -> OkGreen
    TORQUE_NG -> NgRed
    else -> InvalidGrey
}

/** 判定徽标（绿 OK / 红 NG / 灰 未判定 —— 旧数据没有判据时明写「未判定」，不留空） */
@Composable
private fun JudgeChip(rec: TorqueRecord) {
    val c = judgeColor(rec)
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
        Text(judgeLabel(rec), color = c, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun TorqueDetailDialog(rec: TorqueRecord, onDismiss: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("扭力计记录详情　#${rec.id}", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(4.dp))
                DetailLine("判定", judgeLabel(rec), color = judgeColor(rec))
                DetailLine("时间（第三笔）", fullTime(rec.timestampMs))
                DetailLine("保存时刻", fullTime(rec.savedAtMs))
                DetailLine("线别", rec.lineName.ifBlank { "—" })
                DetailLine("机种", rec.modelName.ifBlank { "—" })
                DetailLine("扭矩范围", rec.rangeName.ifBlank { "—" })
                DetailLine("扭矩下限", torqueValueText(rec.rangeMin) + " " + rec.unitText)
                DetailLine("扭矩上限", torqueValueText(rec.rangeMax) + " " + rec.unitText)
                DetailLine("设备信息", rec.deviceName.ifBlank { "—" })
                DetailLine("第 1 次", rec.v1Text)
                DetailLine("第 2 次", rec.v2Text)
                DetailLine("第 3 次", rec.v3Text)
                DetailLine("平均扭矩", "${rec.averageText} ${rec.unitText}")
                DetailLine("参与平均", "${rec.sampleCount} 笔")
                DetailLine("报文单位", rec.unitText.ifBlank { "—" })
                DetailLine("组号", rec.sessionId)
                DetailLine("原始报文", rec.rawText.ifBlank { "—" })
                DetailLine("原始字节", rec.rawHex)
                Spacer(Modifier.padding(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = NgRed)
                        Text(" 删除此组", color = NgRed)
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
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------- 删除（需密码，与烙铁同一个口令） ----------

/**
 * 删除确认 + 口令校验。**口令与烙铁共用同一份**（用户要求 6）：
 * 都走 [DeletePassword]（盐 + PBKDF2 哈希存在 app_setting），在配置页改一次两边同时生效。
 *
 * 这里不像烙铁记录页那样复用那个私有对话框，而是本地一份 —— 本包不 import 烙铁记录页的任何东西。
 */
@Composable
private fun TorqueDeleteDialog(
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
        title = { Text("删除扭力计记录（需密码）") },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; err = null },
                    label = { Text("删除密码（与烙铁相同）") },
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

// ---------- 日历 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorqueDateField(label: String, dayMs: Long?, onPick: (Long?) -> Unit, modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    Surface(modifier = modifier.clickable { show = true },
        shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    dayMs?.let { dateOnly(it) } ?: "未选择",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (dayMs != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (dayMs != null) {
                IconButton(onClick = { onPick(null) }) {
                    Icon(Icons.Default.Close, "清除$label", Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.DateRange, "选择$label", Modifier.padding(end = 10.dp))
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
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

// ---------- 导出（扭力计专用导出器，与烙铁各一份） ----------

private fun exportRecords(ctx: Context, records: List<TorqueRecord>) {
    if (records.isEmpty()) return
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
    val file = File(dir, "扭力计记录_$stamp.xlsx")
    try {
        TorqueXlsxExporter.export(records, file)
        shareFile(ctx, file)
        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, "已导出：${records.size} 组 → ${file.name}", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun shareFile(ctx: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
}
