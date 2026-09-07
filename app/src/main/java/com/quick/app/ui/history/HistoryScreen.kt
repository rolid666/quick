package com.quick.app.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.quick.app.QuickApp
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.export.XlsxExporter
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.dateOnly
import com.quick.app.ui.fullTime
import com.quick.app.ui.tempText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.os.Looper

private val OkGreen = Color(0xFF2E7D32)
private val NgRed = Color(0xFFC62828)

/** 筛选条件（全部为空 = 不过滤） */
private data class Filters(
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val line: String? = null,
    val model: String? = null,
    val result: String? = null,
    val kw: String = ""
) {
    val kwOrNull: String? get() = kw.trim().ifEmpty { null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    val lines by db.lineDao().all().collectAsState(initial = emptyList())
    val models by db.modelDao().all().collectAsState(initial = emptyList())
    val lineOptions = remember(lines) { listOf<String?>(null) + lines.map { it.name } }
    val modelOptions = remember(models) { listOf<String?>(null) + models.map { it.name } }

    var filters by remember { mutableStateOf(Filters()) }
    var selected by remember { mutableStateOf<MeasurementRecord?>(null) }

    val records by remember(filters) {
        db.recordDao().query(
            filters.fromMs, filters.toMs, filters.line, filters.model, filters.result, filters.kwOrNull
        )
    }.collectAsState(initial = emptyList())
    val total by remember(filters) {
        db.recordDao().count(filters.fromMs, filters.toMs, filters.line, filters.model, filters.result)
    }.collectAsState(initial = 0)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 响应式：≥600dp（平板）筛选一行排开；手机窄屏自动换行
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
            val wide = maxWidth >= 600.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("历史记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
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
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val fs = filters.fromMs
                                DateChip("全部", fs == null) { filters = filters.copy(fromMs = null) }
                                DateChip("今天", fs == todayStart()) { filters = filters.copy(fromMs = todayStart()) }
                                DateChip("昨天", fs == yesterdayStart()) { filters = filters.copy(fromMs = yesterdayStart()) }
                                DateChip("本周", fs == weekStart()) { filters = filters.copy(fromMs = weekStart()) }
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = filters.kw,
                                    onValueChange = { filters = filters.copy(kw = it) },
                                    placeholder = { Text("关键词(线别/机种/SN)") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("共 $total 条", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                ResultChip("OK", filters.result == "OK") {
                                    filters = filters.copy(result = if (it) "OK" else null)
                                }
                                ResultChip("NG", filters.result == "NG") {
                                    filters = filters.copy(result = if (it) "NG" else null)
                                }
                                Row(Modifier.width(14.dp)) {}
                                LabeledSelect("线别", lineOptions, filters.line,
                                    { filters = filters.copy(line = it) }, Modifier.width(170.dp))
                                LabeledSelect("机种", modelOptions, filters.model,
                                    { filters = filters.copy(model = it) }, Modifier.width(190.dp))
                            }
                        } else {
                            run {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    val fs = filters.fromMs
                                    DateChip("全部", fs == null) { filters = filters.copy(fromMs = null) }
                                    DateChip("今天", fs == todayStart()) { filters = filters.copy(fromMs = todayStart()) }
                                    DateChip("昨天", fs == yesterdayStart()) { filters = filters.copy(fromMs = yesterdayStart()) }
                                    DateChip("本周", fs == weekStart()) { filters = filters.copy(fromMs = weekStart()) }
                                    Spacer(Modifier.weight(1f))
                                    Text("共 $total 条", style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = filters.kw,
                                    onValueChange = { filters = filters.copy(kw = it) },
                                    placeholder = { Text("关键词(线别/机种/SN)") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                )
                                // 手机窄屏：放不下自动折行，永不重叠
                                FlowRow(
                                    Modifier.padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ResultChip("OK", filters.result == "OK") {
                                        filters = filters.copy(result = if (it) "OK" else null)
                                    }
                                    ResultChip("NG", filters.result == "NG") {
                                        filters = filters.copy(result = if (it) "NG" else null)
                                    }
                                    LabeledSelect("线别", lineOptions, filters.line,
                                        { filters = filters.copy(line = it) }, Modifier.width(170.dp))
                                    LabeledSelect("机种", modelOptions, filters.model,
                                        { filters = filters.copy(model = it) }, Modifier.width(190.dp))
                                }
                            }
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
        }
    }

    selected?.let { rec -> DetailDialog(rec) { selected = null } }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ResultChip(label: String, selected: Boolean, onClick: (Boolean) -> Unit) {
    FilterChip(
        selected = selected, onClick = { onClick(!selected) }, label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (label == "OK") OkGreen.copy(alpha = 0.2f) else NgRed.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun LabeledSelect(
    placeholder: String,
    options: List<String?>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        SearchableSelect(
            label = placeholder,
            options = options.filterNotNull(),
            selected = selected,
            onSelect = { onSelect(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecordRow(rec: MeasurementRecord, onClick: () -> Unit) {
    val ok = rec.isOk
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(fullTime(rec.timestampMs), style = MaterialTheme.typography.bodyMedium)
                Text(dateOnly(rec.timestampMs), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text("${rec.lineName.ifBlank { "—" }} / ${rec.modelName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyLarge)
                Text("SN: ${rec.deviceSn?.ifBlank { "—" } ?: "—"}　设定 ${rec.setTemp?.let { "$it℃" } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = if (ok) OkGreen.copy(alpha = 0.14f) else NgRed.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp)) {
                Text(if (ok) "✓ OK" else "✕ NG",
                    color = if (ok) OkGreen else NgRed,
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
private fun DetailDialog(rec: MeasurementRecord, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("记录详情　#${rec.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(4.dp))
                DetailLine("结果", rec.result, color = if (rec.isOk) OkGreen else NgRed)
                DetailLine("时间", fullTime(rec.timestampMs))
                DetailLine("线别", rec.lineName.ifBlank { "—" })
                DetailLine("机种", rec.modelName.ifBlank { "—" })
                DetailLine("设备 SN", rec.deviceSn?.ifBlank { "—" } ?: "—")
                DetailLine("设备 IP", rec.deviceIp)
                DetailLine("设定温度", rec.setTemp?.let { "$it ℃" } ?: "—")
                DetailLine("测量温度", tempText(rec.measuredTemp))
                DetailLine("误差范围", rec.tolerance?.let { "±$it ℃" } ?: "—")
                DetailLine("判定来源", "仪器 0x1E（App 不参与判定）")
                Spacer(Modifier.padding(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
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

// ---------- 日期工具 ----------

private fun dayStart(offsetDays: Long): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -offsetDays.toInt())
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun todayStart(): Long = dayStart(0)
private fun yesterdayStart(): Long = dayStart(1)
private fun weekStart(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
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
