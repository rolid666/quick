package com.quick.app.ui.torque

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.data.db.TQ_DEVICE
import com.quick.app.data.db.TQ_LINE
import com.quick.app.data.db.TQ_MODEL
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TorqueRecord
import com.quick.app.data.db.torqueDictLabel
import com.quick.app.data.db.torqueJudge
import com.quick.app.data.db.torqueValueText
import com.quick.app.torque.TorqueLinkState
import com.quick.app.torque.TorqueScan
import com.quick.app.torque.TorqueUiState
import com.quick.app.ui.InvalidGrey
import com.quick.app.ui.NgRed
import com.quick.app.ui.OkGreen
import com.quick.app.ui.SearchableSelect
import com.quick.app.ui.WarnOrange
import com.quick.app.ui.WifiBlue
import com.quick.app.ui.hms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 扭力计测量主页（用户 2026-09-22 workflow 定稿）：
 *
 * ```
 * 在扭力计上测 → 三笔分开暂存显示 → 满三笔起延时（默认 5 秒，可改）
 *   ├─ 期间点「重测」 → 清空本次三笔，重新开始（无需密码）
 *   └─ 时间到 + 四项信息齐全 → 自动保存一行（三笔 + 平均 + OK/NG）
 * ```
 *
 * 用户明确要求的界面行为：
 * 1. **单位只标注一次**（写在结果前面），单元格里只有数字；
 * 2. **四项（线别/机种/扭矩范围/设备信息）没选全时**：三次测完也**不保存**，
 *    顶部弹一条醒目告警，并把**没选的那几个下拉整块点亮成橙色加粗**，选完即恢复；
 * 3. **重测按键不用密码**，点了就清空暂存（已经保存的记录不受影响）；
 * 4. **每格右上角一个小叉**（2026-09-26）：只删这一笔，后面的笔往前补位，
 *    下一笔测量自动补满 —— 同样不用密码；
 * 5. **只记正数**（2026-09-26）：反扭松 / 按清除键时设备吐出的非正数读数被跳过，
 *    跳过多少笔在卡片里如实显示（不静默）。
 *
 * 本页**不碰**烙铁的任何状态：控制器在 QuickApp 里各跑各的，切页面只是换这一屏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorqueMeasureScreen(
    openHistory: () -> Unit,
    openManage: () -> Unit,
    openDiag: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val torque = app.torque
    val scope = rememberCoroutineScope()
    val state by torque.ui.collectAsStateWithLifecycle()
    val dicts by app.db.torqueDictDao().allKinds().collectAsState(initial = emptyList())

    val lines = remember(dicts) { dicts.filter { it.kind == TQ_LINE }.map { it.name } }
    val models = remember(dicts) { dicts.filter { it.kind == TQ_MODEL }.map { it.name } }
    val ranges = remember(dicts) { dicts.filter { it.kind == TQ_RANGE }.map { it.name } }
    val devices = remember(dicts) { dicts.filter { it.kind == TQ_DEVICE }.map { it.name } }

    /** 当前选中的扭矩范围（拿上下限做判定；界面与控制器用同一个 [torqueJudge]，两边不可能不一致） */
    val selRange = remember(dicts, state.selRange) {
        dicts.firstOrNull { it.kind == TQ_RANGE && it.name == state.selRange }
    }

    fun pick(kind: String) = { name: String? -> scope.launch { torque.select(kind, name) }; Unit }

    var scanning by remember { mutableStateOf(false) }

    // 「信息不全」时点亮的字段（按 label 匹配，界面上就是这四个下拉）
    val missing = state.missingFields
    val now = rememberNow(state.saveAtMs != null)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val compact = maxWidth < 480.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 标题 + 链路状态 + 监听开关 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (compact) "扭力计" else "扭力计（USB）",
                    style = if (wide) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LinkDot(state)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { torque.setListening(!state.listen) }) {
                    Text(
                        if (state.listen) (if (compact) "■ 停止" else "■ 停止监听")
                        else (if (compact) "▶ 监听" else "▶ 开始监听")
                    )
                }
            }

            // ── 页面跳转（记录 / 管理 / 诊断）──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton("记录", Icons.Default.History, openHistory)
                NavButton("管理", Icons.AutoMirrored.Filled.Article, openManage)
                NavButton("诊断", Icons.Default.BugReport, openDiag)
            }

            // ── 四个下拉：线别 / 机种 / 扭矩范围 / 设备信息（用户要求 2）──
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val w = if (wide) Modifier.width(230.dp) else Modifier.width(160.dp)
                SeatSelect("线别", lines, state.selLine, pick(TQ_LINE), w,
                    highlight = missing.contains(torqueDictLabel(TQ_LINE)))
                SeatSelect("机种", models, state.selModel, pick(TQ_MODEL), w,
                    highlight = missing.contains(torqueDictLabel(TQ_MODEL)))
                SeatSelect("扭矩范围", ranges, state.selRange, pick(TQ_RANGE), w,
                    highlight = missing.any { it.startsWith(torqueDictLabel(TQ_RANGE)) })
                SeatSelect("设备信息", devices, state.selDevice, pick(TQ_DEVICE), w,
                    highlight = missing.contains(torqueDictLabel(TQ_DEVICE)))
                // 设备信息可以调后置摄像头扫码录入（用户要求 4）
                FilledTonalButton(onClick = { scanning = true }) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.padding(end = 6.dp))
                    Text("扫码录入设备")
                }
            }

            // ── 信息不全告警（三次测完才出现，用户要求：要有一栏显眼的提示信息）──
            if (missing.isNotEmpty()) MissingBanner(missing)

            // ── 主区：本组三笔暂存 ──
            SessionCard(
                state = state,
                remainSec = state.saveAtMs?.let { (it - now) / 1000.0 },
                rangeMin = selRange?.minValue,
                rangeMax = selRange?.maxValue,
                onRemeasure = { torque.remeasure() },
                // 单笔删除：格子右上角的小叉（用户 2026-09-26）。无需密码，删掉后下一笔自动补上。
                onDeleteSample = { i -> torque.removeSample(i) }
            )

            // ── 最近保存的一组 ──
            LastSavedCard(state.lastSaved)

            // ── 底部一行：链路 / 待补记 / 错误（通信明细在诊断页）──
            FooterLine(state)
        }
    }

    if (scanning) {
        ScanDialog(
            title = "扫码录入设备信息",
            hint = "对准电动螺丝机铭牌上的二维码/条码；扫到 SN=xxx 形式的内容时只取编号本身。" +
                "没有摄像头或扫不出来，可在下面手动输入。"
        ) { text ->
            scanning = false
            val name = TorqueScan.extractName(text)
            if (name != null) scope.launch { torque.addDict(TQ_DEVICE, name) }
        }
    }
}

/** 每 250ms 走一次的「当前时刻」（只在需要倒计时时开着，避免无谓重组） */
@Composable
private fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

@Composable
private fun SeatSelect(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
    highlight: Boolean = false
) {
    SearchableSelect(label = label, options = options, selected = selected,
        onSelect = onSelect, modifier = modifier, highlight = highlight)
}

@Composable
private fun NavButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.padding(end = 6.dp))
        Text(label)
    }
}

/** 链路状态小圆点（宽度封顶，长描述不会把右边的开关挤出屏幕） */
@Composable
private fun LinkDot(state: TorqueUiState) {
    val (text, color) = linkText(state)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 190.dp)) {
        Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.size(10.dp)) {
            Box(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp))
    }
}

private fun linkText(state: TorqueUiState): Pair<String, Color> = when (val l = state.link) {
    is TorqueLinkState.Open -> "已连接" to OkGreen
    is TorqueLinkState.Opening -> "打开中…" to WifiBlue
    is TorqueLinkState.NeedPermission -> "等待授权" to WarnOrange
    is TorqueLinkState.NoDevice -> (if (state.listen) "未检测到 USB" else "已停止") to InvalidGrey
    is TorqueLinkState.Failed -> l.reason to WarnOrange
    is TorqueLinkState.Off -> "已停止" to InvalidGrey
}

/**
 * 「信息不全 → 不能保存」的显眼告警（用户 2026-09-22 硬要求）。
 *
 * 用**错误色边框 + 图标 + 加粗大字**，并写明「补齐后会自动保存」——
 * 现场最怕的是以为白测了，所以这里必须说清楚这一组还留着。
 */
@Composable
private fun MissingBanner(missing: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = WarnOrange.copy(alpha = 0.16f),
        border = BorderStroke(2.dp, WarnOrange)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = WarnOrange, modifier = Modifier.size(30.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "三次已测完，但还不能保存！",
                    color = WarnOrange, fontWeight = FontWeight.Bold, fontSize = 20.sp
                )
                Text(
                    "请选择：${missing.joinToString("、")}",
                    color = WarnOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    "这三笔结果仍在暂存里（不会丢），选完立刻自动保存。",
                    style = MaterialTheme.typography.bodySmall, color = WarnOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 本组三笔暂存卡（页面主角）。
 *
 * 三次结果**分开显示**（用户要求），单位只在标题处标注一次（用户要求：单位单独说明）。
 * 满三笔后这张卡自己会变成「等保存」状态：倒计时 / 信息不全 / 已忽略笔数。
 */
@Composable
private fun SessionCard(
    state: TorqueUiState,
    remainSec: Double?,
    rangeMin: Double?,
    rangeMax: Double?,
    onRemeasure: () -> Unit,
    onDeleteSample: (Int) -> Unit
) {
    val full = state.sessionSeq >= state.sessionTotal
    val avg = if (full) state.sessionSample.takeIf { it.size == state.sessionTotal }?.average() else null
    val judge = if (avg != null) torqueJudge(round2(avg), rangeMin, rangeMax) else null

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本次一组（${state.sessionTotal} 笔取平均）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                // 单位只在这里标注一次：结果前标注即可，单元格里只有数字
                Text(
                    "单位 ${state.unit}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 三个槽位分开显示（未测到的那一格留空，不做假数据）
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0 until state.sessionTotal) {
                    val filled = i < state.sessionSeq
                    SlotBox(
                        label = "第 ${i + 1} 次",
                        text = state.sessionSample.getOrNull(i)?.let { torqueValueText(it) } ?: "--",
                        filled = filled,
                        // 小叉只在有数据的那几格出现（空格子没什么可删的）
                        onDelete = if (filled) ({ onDeleteSample(i) }) else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "右侧小叉 = 只删掉这一笔；删掉后下一笔测量会自动补到这一格（无需密码）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp)
            )

            // 平均值 + 判定（满三笔才有；判定与保存时用的是同一个函数）
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text("平均", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
                Text(
                    if (avg == null) "—" else torqueValueText(round2(avg)),
                    fontSize = 48.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                if (judge != null) {
                    Spacer(Modifier.width(12.dp))
                    JudgeChip(judge, Modifier.padding(bottom = 10.dp))
                }
            }

            // 状态一行：等待第 N 笔 / 倒计时 / 信息不全 / 已忽略
            val status = when {
                !full -> "在扭力计上测一笔，数据自动进来（还差 ${state.sessionTotal - state.sessionSeq} 笔）"
                state.missingFields.isNotEmpty() -> "信息未选全，暂不保存（见上方橙色提示）"
                remainSec != null && remainSec > 0 ->
                    String.format(
                        Locale.US, "信息齐全，%.1f 秒后自动保存（要重测请现在点）", remainSec
                    )
                else -> "正在保存…"
            }
            Text(
                status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (state.ignoredCount > 0) {
                // 删过一笔之后本组可能已经不满，所以措辞不能写死「本组已满」（见 §29：ignoredCount 是事实，删笔不抹掉）
                Text(
                    "本组已有 ${state.ignoredCount} 笔因「已满」被忽略（未计入本组）" +
                        if (full) " —— 等保存后重测这几笔" else " —— 这几笔没有被记进来",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = WarnOrange, modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 跳过的非正数读数要看得见（用户 2026-09-26：只记正数）——
            // 反扭松 / 按清除键时设备会吐负数，这里如实交代跳了多少笔，不静默
            if (state.skippedNonPositive > 0) {
                Text(
                    "已跳过 ${state.skippedNonPositive} 笔非正数读数（反扭松或按清除键时设备的输出，未计入本组）",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnOrange, modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 重测：无需密码，点了清空本组三笔（用户要求）
            FilledTonalButton(
                onClick = onRemeasure,
                enabled = state.sessionSeq > 0 || state.ignoredCount > 0,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = WarnOrange.copy(alpha = 0.18f),
                    contentColor = WarnOrange
                ),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.padding(end = 6.dp))
                Text("重测（清空本次三笔）", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 一个暂存格：`第 1 次` + 数值（未测到显示 --）。
 *
 * [onDelete] 非空时，格子**右上角**出现一个小叉（用户 2026-09-26 要求：单笔可删）。
 * 用普通 Box + clickable 而不是 IconButton：IconButton 会按 Material 的最小触控尺寸
 * 自动放大（48dp），在这个 96dp 高的小格子里会把数值挤歪。
 */
@Composable
private fun SlotBox(
    label: String,
    text: String,
    filled: Boolean,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val onC = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(10.dp),
        color = onC.copy(alpha = if (filled) 0.10f else 0.04f),
        border = BorderStroke(1.dp, onC.copy(alpha = if (filled) 0.45f else 0.18f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = onC.copy(alpha = 0.8f))
                Text(
                    text,
                    fontSize = if (text.length > 6) 22.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filled) onC else onC.copy(alpha = 0.45f),
                    maxLines = 1
                )
            }
            if (onDelete != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)                       // 手指点得到，又不盖住数值
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close, "删除 $label",
                        tint = WarnOrange, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 判定徽标（OK 绿 / NG 红） */
@Composable
private fun JudgeChip(judge: String, modifier: Modifier = Modifier) {
    val color = if (judge == "OK") OkGreen else NgRed
    Surface(color = color, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            judge, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

/** 最近自动保存的一组（三笔 + 平均 + 判定，一眼能核对刚测的那组存得对不对） */
@Composable
private fun LastSavedCard(rec: TorqueRecord?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近保存的一组", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f))
                rec?.let {
                    Text(hms(it.timestampMs), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (rec == null) {
                Text("还没有保存过记录", Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
                return@Column
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(rec.averageText, fontSize = 60.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(" ${rec.unitText}", fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp))
                rec.isOk?.let { ok ->
                    Spacer(Modifier.width(12.dp))
                    JudgeChip(if (ok) "OK" else "NG", Modifier.padding(bottom = 10.dp))
                }
            }
            Text(
                "${rec.v1Text}　${rec.v2Text}　${rec.v3Text}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                listOfNotNull(
                    rec.lineName.ifBlank { null },
                    rec.modelName.ifBlank { null },
                    rec.rangeName.ifBlank { null },
                    rec.deviceName.ifBlank { null }
                ).joinToString("　·　"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text("已自动保存 ✓", style = MaterialTheme.typography.labelMedium,
                color = OkGreen, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 底部一行：链路说明 + 待补记 + 错误（完整通信日志在诊断页） */
@Composable
private fun FooterLine(state: TorqueUiState) {
    val parts = mutableListOf<String>()
    parts += when (val l = state.link) {
        is TorqueLinkState.Open -> l.desc
        is TorqueLinkState.Failed -> "错误：${l.reason}"
        is TorqueLinkState.NeedPermission -> "等待系统授权弹窗"
        is TorqueLinkState.NoDevice -> "未检测到 FTDI 设备（USB 线插在平板上、且是数据线）"
        is TorqueLinkState.Opening -> "正在打开串口…"
        is TorqueLinkState.Off -> "监听已关闭"
    }
    parts += "波特率 ${state.baud} 8N2"
    if (state.pendingCount > 0) parts += "待补记 ${state.pendingCount} 组"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(parts.joinToString("　"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.lastError?.let {
            Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = WarnOrange)
        }
    }
}

/** 与 TorqueSession.average() 同一个圆整口径（先圆整再判定，界面与记录才会一致） */
private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
