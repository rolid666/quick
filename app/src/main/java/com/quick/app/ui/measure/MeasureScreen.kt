package com.quick.app.ui.measure

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.collect.CommLine
import com.quick.app.collect.ConnState
import com.quick.app.collect.MeasureUiState
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.StatusChip
import com.quick.app.ui.hms
import com.quick.app.ui.intTempText
import com.quick.app.ui.leakText
import com.quick.app.ui.rangeText
import com.quick.app.ui.tempText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF2E7D32)
private val NgRed = Color(0xFFC62828)
private val WifiBlue = Color(0xFF1565C0)

/**
 * 测量主页 —— 响应式：
 * - 宽屏（平板，≥700dp）：左信息面板 + 右结果/日志 双列
 * - 窄屏（手机竖屏）：全部卡片单列纵向排布，整体可滚动（任何高度下都不互相挤压）
 */
@Composable
fun MeasureScreen(openManage: () -> Unit, openDiag: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val controller = app.controller
    val scope = rememberCoroutineScope()
    val state by controller.ui.collectAsStateWithLifecycle()
    val lines by app.db.lineDao().all().collectAsState(initial = emptyList())
    val models by app.db.modelDao().all().collectAsState(initial = emptyList())

    // 线别/机种选择（本地即时 + 持久化到 controller）
    var lineSel by remember { mutableStateOf(controller.selectedLine) }
    var modelSel by remember { mutableStateOf(controller.selectedModel) }
    fun onLine(name: String?) { lineSel = name; scope.launch { controller.selectLine(name) } }
    fun onModel(name: String?) { modelSel = name; scope.launch { controller.selectModel(name) } }

    // 新结果 → 弹卡 + 振动
    var resultCard by remember { mutableStateOf<MeasurementRecord?>(null) }
    LaunchedEffect(controller) {
        controller.resultEvents.collect { rec ->
            vibrate(ctx)
            resultCard = rec
            delay(5000)
            resultCard = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            val logs by controller.logs.collectAsStateWithLifecycle()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "191AF+ 测量系统",
                        style = if (wide) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = openDiag) { Icon(Icons.Default.BugReport, "通信诊断") }
                    Button(onClick = { controller.setRunning(!state.running) }) {
                        Text(if (state.running) "■ 停止采集" else "▶ 开始采集")
                    }
                }

                ConnBanner(state)

                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LiveTempCard(state)
                            SelectRow(lines.map { it.name }, models.map { it.name }, lineSel, modelSel, ::onLine, ::onModel)
                            InstrumentInfo(state, wide = true)
                            TestHint()
                        }
                        Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LastResultCard(state.lastRecord)
                            EventLog(logs, openManage)
                        }
                    }
                } else {
                    LiveTempCard(state)
                    SelectRow(lines.map { it.name }, models.map { it.name }, lineSel, modelSel, ::onLine, ::onModel)
                    InstrumentInfo(state, wide = false)
                    LastResultCard(state.lastRecord)
                    TestHint()
                    EventLog(logs, openManage)
                }
            }
        }

        resultCard?.let { rec ->
            ResultOverlay(rec, onDismiss = { resultCard = null })
        }
    }
}

/** 线别/机种两个下拉选择（窄屏下各占一半，weight 分摊不溢出） */
@Composable
private fun SelectRow(
    lines: List<String>, models: List<String>,
    lineSel: String?, modelSel: String?,
    onLine: (String?) -> Unit, onModel: (String?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SearchableSelect(
            label = "线别", options = lines, selected = lineSel, onSelect = onLine,
            modifier = Modifier.weight(1f)
        )
        SearchableSelect(
            label = "机种", options = models, selected = modelSel, onSelect = onModel,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 连接状态横幅 */
@Composable
private fun ConnBanner(state: MeasureUiState) {
    val (text, color) = when (val c = state.conn) {
        is ConnState.Connected -> "已连接 ${c.ip}:${c.port}" to OkGreen
        is ConnState.Connecting -> "连接中…" to WifiBlue
        is ConnState.Reconnecting -> "重连中（第 ${c.attempt} 次，${c.delayMs / 1000}s 后重试）" to Color(0xFFEF6C00)
        is ConnState.Disconnected -> (if (state.running) "未连接（等待仪器 IP）" else "采集已停止") to Color(0xFF757575)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(text, color)
        state.lastError?.let { err ->
            Text("　⚠ $err", color = NgRed, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun LiveTempCard(state: MeasureUiState) {
    val snap = state.snapshot
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("当前温度（实时）", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (snap?.liveTempC == null) "--" else String.format("%.1f", snap.liveTempC),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Text(" ℃", fontSize = 30.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 14.dp, start = 4.dp))
            }
        }
    }
}

/**
 * 仪器信息卡（字段语义为实机实测版）：
 * 设备信息 0x0A~0x19、目标温度 0x1B、温度范围 0x1C~0x1D、漏地电压 0x02、结果保存 0x1E。
 */
@Composable
private fun InstrumentInfo(state: MeasureUiState, wide: Boolean) {
    val s = state.snapshot
    val info = s?.deviceInfo ?: "--"
    val target = intTempText(s?.targetTemp)
    val range = rangeText(s?.tempLow, s?.tempHigh)
    val leak = leakText(s?.leakageMv)
    val flag = s?.saveFlagRaw?.let { if (it != 0) "$it（保存中）" else "0（待命）" } ?: "--"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoItem("设备信息", info)
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    InfoItem("目标温度", target); InfoItem("温度范围", range)
                    InfoItem("漏地电压", leak); InfoItem("结果保存", flag)
                }
            } else {
                // 手机窄屏：2×2 排布
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { InfoItem("目标温度", target); InfoItem("温度范围", range) }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { InfoItem("漏地电压", leak); InfoItem("结果保存", flag) }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TestHint() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
        Column(Modifier.padding(12.dp)) {
            Text("操作提示：操作员在仪器上测量并按下「保存」按钮即可，本 App 会自动收取并保存结果，无需再点任何按钮。",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LastResultCard(rec: MeasurementRecord?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近结果", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (rec != null) Text(hms(rec.timestampMs), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rec == null) {
                Text("等待仪器保存…", Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
            } else {
                val ok = rec.isOk
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (ok) OkGreen.copy(alpha = 0.15f) else NgRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ok) "✓ OK" else "✕ NG",
                                color = if (ok) OkGreen else NgRed,
                                fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(20.dp))
                            Text(tempText(rec.measuredTemp), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("${rec.lineName} / ${rec.modelName}".ifBlank { "未选线别/机种" })
                    Text("目标 ${intTempText(rec.targetTemp)}")
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("漏地 ${leakText(rec.leakageMv)}")
                    Text("已自动保存 ✓")
                }
            }
        }
    }
}

@Composable
private fun EventLog(logs: List<CommLine>, openManage: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("采集事件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = openManage) {
                    Icon(Icons.Default.Edit, null, Modifier.padding(end = 6.dp)); Text("线别/机种管理")
                }
            }
            // 只展示最近 6 条并完整摊开 —— 外层已是滚动容器，此处不再嵌套滚动
            val recent = logs.takeLast(6)
            if (recent.isEmpty()) {
                Text("暂无事件。开启采集后这里会显示连接与保存记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recent.forEach { l ->
                    val color = when {
                        l.text.startsWith("★") || l.text.startsWith("✔") -> OkGreen
                        l.text.startsWith("⚠") || l.text.startsWith("!!") -> NgRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text("${hms(l.timeMs)}  ${l.text}", style = MaterialTheme.typography.bodyMedium,
                        color = color, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}

/** 新结果全屏弹卡：自动消失或点击关闭 */
@Composable
private fun ResultOverlay(rec: MeasurementRecord, onDismiss: () -> Unit) {
    val ok = rec.isOk
    val color = if (ok) OkGreen else NgRed
    Surface(
        modifier = Modifier.fillMaxSize().clickable { onDismiss() },
        color = color
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("测试完成", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(if (ok) "✓ OK" else "✕ NG", fontSize = 150.sp, color = Color.White, fontWeight = FontWeight.Black,
                    maxLines = 1)
                Text(tempText(rec.measuredTemp), fontSize = 90.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1)
                Text("${rec.lineName} / ${rec.modelName}　目标 ${intTempText(rec.targetTemp)}",
                    fontSize = 26.sp, color = Color.White)
                Text("已自动保存到记录", fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

private fun vibrate(ctx: Context) {
    runCatching {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(250)
        }
    }
}
