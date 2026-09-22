package com.quick.app.ui.measure

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.collect.ConnState
import com.quick.app.collect.MeasureUiState
import com.quick.app.data.db.LIMIT_RMAX
import com.quick.app.data.db.LIMIT_VMAX
import com.quick.app.data.db.LimitPreset
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.data.db.TempPreset
import com.quick.app.net.channelText
import com.quick.app.qr.QrGen
import com.quick.app.ui.InvalidGrey
import com.quick.app.ui.OkGreen
import com.quick.app.ui.QrDialog
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.WarnOrange
import com.quick.app.ui.WifiBlue
import com.quick.app.ui.hms
import com.quick.app.ui.intTempText
import com.quick.app.ui.ohmText
import com.quick.app.ui.rangeText
import com.quick.app.ui.resultColor
import com.quick.app.ui.resultLabel
import com.quick.app.ui.tempText
import com.quick.app.ui.voltText
import kotlinx.coroutines.launch

/**
 * 测量主页 —— 常驻页，其他页面都从这里按按钮进入（用户 2026-09 定：不用底部导航栏）。
 *
 * 布局原则：**大字只留两个**（当前温度、最近结果），其余信息收成小字一行；
 * 采集事件不再铺在页面上，要看日志进「诊断」页。
 * - 宽屏（平板 ≥700dp）：温度与结果左右并排
 * - 窄屏（手机）：上下堆叠
 *
 * 顶部一行的宽度分配是**硬要求**：标题可压缩、连接状态可压缩，**采集开关永远是固定宽度**。
 * （旧写法里状态文字会先把宽度吃光，把「开始/停止采集」挤出屏幕 —— 2026-09 用户反馈的那个 bug）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureScreen(
    openHistory: () -> Unit,
    openConfig: () -> Unit,
    openManage: () -> Unit,
    openDiag: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val controller = app.controller
    val scope = rememberCoroutineScope()
    val state by controller.ui.collectAsStateWithLifecycle()
    val lines by app.db.lineDao().all().collectAsState(initial = emptyList())
    val models by app.db.modelDao().all().collectAsState(initial = emptyList())
    val stations by app.db.stationDao().all().collectAsState(initial = emptyList())
    val presets by app.db.tempPresetDao().all().collectAsState(initial = emptyList())
    val limits by app.db.limitPresetDao().all().collectAsState(initial = emptyList())
    val sns by app.db.deviceSnDao().all().collectAsState(initial = emptyList())

    // 选择项全部来自控制器状态（保存后控制器清空设备编号，界面自动跟随）
    val lineSel = state.selLine
    val modelSel = state.selModel
    val stationSel = state.selStation
    val tempSel = state.selTempPreset
    val vmaxSel = state.selVmax
    val rmaxSel = state.selRmax
    val snSel = state.selDeviceSn
    fun onLine(name: String?) = scope.launch { controller.selectLine(name) }
    fun onModel(name: String?) = scope.launch { controller.selectModel(name) }
    fun onStation(name: String?) = scope.launch { controller.selectStation(name) }
    fun onTemp(name: String?) = scope.launch { controller.selectTempPreset(name) }
    fun onVmax(name: String?) = scope.launch { controller.selectVmax(name) }
    fun onRmax(name: String?) = scope.launch { controller.selectRmax(name) }
    fun onSn(name: String?) = scope.launch { controller.selectDeviceSn(name) }

    // 温度 / 漏电压上限 / 接地电阻上限 / 设备编号 → 组合配置二维码
    // （用户 2026-09 定：前三项**任意一项**有选择就能出码，没选的项不进二维码）
    var qrPayload by remember { mutableStateOf<String?>(null) }
    val pickedPreset = presets.firstOrNull { it.name == tempSel }
    val vmaxOptions = limits.filter { it.kind == LIMIT_VMAX }.map { it.name }
    val rmaxOptions = limits.filter { it.kind == LIMIT_RMAX }.map { it.name }
    val pickedVmax = limits.firstOrNull { it.kind == LIMIT_VMAX && it.name == vmaxSel }
    val pickedRmax = limits.firstOrNull { it.kind == LIMIT_RMAX && it.name == rmaxSel }
    // 有设备编号也算「有得配」——厂家的单条指令示例就是一条 SN=…（此时二维码只配编号，不动其他项）
    val canGenQr = pickedPreset != null || pickedVmax != null || pickedRmax != null ||
        !snSel.isNullOrBlank()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val compact = maxWidth < 480.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 标题 + 连接状态 + 采集开关 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (compact) "192AF+" else "192AF+ 测量系统",
                    style = if (wide) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)          // 标题可压缩，给右边让路
                )
                ConnDot(state)
                Spacer(Modifier.width(8.dp))
                // 非加权 = 先测量 = 任何屏幕宽度下都完整可见（旧 bug：被状态文字挤到屏幕外）
                Button(onClick = { controller.setRunning(!state.running) }) {
                    Text(
                        if (state.running) (if (compact) "■ 停止" else "■ 停止采集")
                        else (if (compact) "▶ 采集" else "▶ 开始采集")
                    )
                }
            }

            // ── 页面跳转按钮（记录 / 管理 / 配置 / 诊断）──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton("记录", Icons.Default.History, openHistory)
                NavButton("管理", Icons.Default.Article, openManage)
                NavButton("配置", Icons.Default.Settings, openConfig)
                NavButton("诊断", Icons.Default.BugReport, openDiag)
            }

            // ── 线别 / 机种 / 站别（三个平级下拉；窄屏自动折行，不挤压成一条缝）──
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val w = if (wide) Modifier.width(230.dp) else Modifier.width(150.dp)
                SeatSelect("线别", lines.map { it.name }, lineSel, ::onLine, w)
                SeatSelect("机种", models.map { it.name }, modelSel, ::onModel, w)
                SeatSelect("站别", stations.map { it.name }, stationSel, ::onStation, w)
            }

            // ── 温度设置 / 漏电压上限 / 接地电阻上限 / 设备编号 + 组合配置二维码 ──
            // 与上面的生产信息分开一行：这几项是「配到仪器里」的，性质不同（用户 2026-09 要求）
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val w = if (wide) Modifier.width(230.dp) else Modifier.width(150.dp)
                SeatSelect("温度设置", presets.map { it.name }, tempSel, ::onTemp, w)
                SeatSelect("漏电压上限", vmaxOptions, vmaxSel, ::onVmax, w)
                SeatSelect("接地电阻上限", rmaxOptions, rmaxSel, ::onRmax, w)
                SeatSelect("设备编号", sns.map { it.name }, snSel, ::onSn, w)
                FilledTonalButton(
                    onClick = {
                        qrPayload = QrGen.configPayload(
                            deviceSn = snSel,
                            setTemp = pickedPreset?.setTemp,
                            tolerance = pickedPreset?.tolerance,
                            vmaxRaw = pickedVmax?.valueRaw,
                            rmaxRaw = pickedRmax?.valueRaw
                        )
                    },
                    enabled = canGenQr
                ) {
                    Icon(Icons.Default.QrCode, null, Modifier.padding(end = 6.dp))
                    Text(if (canGenQr) "配置二维码" else "先选配置项")
                }
            }
            Text(
                describeConfig(snSel, pickedPreset, pickedVmax, pickedRmax),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 主区：当前温度 + 最近结果（大字）──
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { CurrentTemp(state) }
                    Box(Modifier.weight(1f)) { LastResult(state.lastRecord) }
                }
            } else {
                CurrentTemp(state)
                LastResult(state.lastRecord)
            }

            InstrumentLine(state)
        }
    }

    qrPayload?.let { p ->
        QrDialog(
            title = "组合配置二维码",
            payload = p,
            note = "仪器扫码后自动写入上面这些项，**没选的项不会被改动**。" +
                "扫完可在本页最下方「仪器状态」核对（设定 / 温度范围 / 电压上限 / 电阻上限）。" +
                "示例格式：SN=QK-HT-001,TSET=350,TMIN=330,TMAX=370",
            onDismiss = { qrPayload = null }
        )
    }
}

/**
 * 组合配置二维码下方的说明 —— 说清「这一张码会把什么配进仪器」。
 * 一个配置项都没选时说清去哪儿建，而不是干给一个灰按钮。
 */
private fun describeConfig(
    snSel: String?,
    preset: TempPreset?,
    vmax: LimitPreset?,
    rmax: LimitPreset?
): String {
    val parts = mutableListOf<String>()
    preset?.let {
        parts += "设定 ${it.setTemp}℃（合格区间 ${it.setTemp - it.tolerance}~${it.setTemp + it.tolerance}℃）"
    }
    vmax?.let { parts += "漏电压上限 ${it.name}（0x1D 应为 ${it.valueRaw}）" }
    rmax?.let { parts += "接地电阻上限 ${it.name}（0x1E 应为 ${it.valueRaw}）" }
    if (!snSel.isNullOrBlank()) parts += "设备编号 $snSel"

    if (parts.isEmpty()) {
        return "扫码可把「设定温度 / 漏电压上限 / 接地电阻上限 / 设备编号」配到仪器上（选哪项配哪项）。" +
            "三项都还没建就去「管理」页新增，选好一项这里就能出码。"
    }
    return "把二维码给仪器扫：${parts.joinToString("；")}。" +
        if (snSel.isNullOrBlank()) "未选设备编号 → 二维码不含 SN，仪器上的编号保持不变。" else ""
}

@Composable
private fun SeatSelect(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier
) {
    SearchableSelect(label = label, options = options, selected = selected, onSelect = onSelect, modifier = modifier)
}

@Composable
private fun NavButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.padding(end = 6.dp))
        Text(label)
    }
}

/**
 * 连接状态小圆点 + 一行小字。
 * 宽度**封顶**：长 IP 或长错误信息不会把右边的采集开关挤出屏幕（2026-09 用户反馈的 bug）。
 */
@Composable
private fun ConnDot(state: MeasureUiState) {
    val (text, color) = when (val c = state.conn) {
        is ConnState.Connected -> "${c.ip}:${c.port}" to OkGreen
        is ConnState.Connecting -> "连接中…" to WifiBlue
        is ConnState.Reconnecting -> "重连中（第 ${c.attempt} 次）" to WarnOrange
        is ConnState.Disconnected -> (if (state.running) "未连接" else "已停止") to InvalidGrey
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = 170.dp)
    ) {
        Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.size(10.dp)) {
            Box(Modifier.size(10.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** 当前温度（实时 0x00）：页面第一主角 */
@Composable
private fun CurrentTemp(state: MeasureUiState) {
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
            // 实时电压/电阻 + 仪器当前测量通道（0x02/0x03/0x04）
            Text(
                "通道 ${channelText(snap?.channel)}　电压 ${voltText(snap?.liveVoltageMv)}　" +
                    "电阻 ${ohmText(snap?.liveResistanceOhm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 最近结果：页面第二主角（温度用仪器 0x20 定格值，不是实时值） */
@Composable
private fun LastResult(rec: MeasurementRecord?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近结果", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f))
                if (rec != null) Text(hms(rec.timestampMs), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rec == null) {
                Text("等待仪器保存…", Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 28.sp)
                Text("（只记录判定为 OK 的测量）", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val color = resultColor(rec)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(resultLabel(rec), color = color, fontSize = 72.sp,
                        fontWeight = FontWeight.Black, maxLines = 1)
                    Text(tempText(rec.measuredTemp), fontSize = 44.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 18.dp, bottom = 8.dp), maxLines = 1)
                }
                Text(
                    listOfNotNull(
                        rec.deviceInfo?.ifBlank { null }?.let { "设备 $it" },
                        rec.lineName.ifBlank { null },
                        rec.modelName.ifBlank { null },
                        rec.stationName.ifBlank { null }
                    ).joinToString(" / ").ifBlank { "未选线别 / 机种 / 站别" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "设定 ${intTempText(rec.targetTemp)}　判定范围 ${rangeText(rec.tempLow, rec.tempHigh)}　" +
                        "电压 ${voltText(rec.measuredVoltageMv)}　电阻 ${ohmText(rec.measuredResistanceOhm)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("已自动保存 ✓", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * 仪器状态一行小字：设备编号(0x0A~0x19) 与判据参数(0x1A~0x1E)。
 * 采集错误信息也落在这里（原来是挤在标题行右侧，把采集开关挤没了）。
 */
@Composable
private fun InstrumentLine(state: MeasureUiState) {
    val s = state.snapshot
    val parts = mutableListOf<String>()
    parts += "设备 ${s?.deviceInfo?.ifBlank { "--" } ?: "--"}"
    parts += "设定 ${intTempText(s?.targetTemp)}"
    parts += "温度范围 ${rangeText(s?.tempLow, s?.tempHigh)}"
    parts += "电压上限 ${voltText(s?.voltageLimitMv)}"
    parts += "电阻上限 ${ohmText(s?.resistanceLimitOhm)}"
    if ((s?.uploadFlag ?: 0) != 0) parts += "仪器保存中…"
    if (state.pendingCount > 0) parts += "待补记 ${state.pendingCount} 笔"

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            parts.joinToString("　"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.lastError?.let {
            Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = WarnOrange)
        }
    }
}
