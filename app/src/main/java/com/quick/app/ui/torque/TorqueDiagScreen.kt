package com.quick.app.ui.torque

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.torque.TorqueLinkState
import com.quick.app.torque.usb.FtdiBaud
import com.quick.app.torque.usb.FtdiUsb
import com.quick.app.ui.NgRed
import com.quick.app.ui.OkGreen
import com.quick.app.ui.WarnOrange
import com.quick.app.ui.hms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 扭力计 USB 诊断页 —— **数据格式未定期间最重要的一页**（2026-09-21）。
 *
 * 明天实测时要在这里回答三个问题：
 * 1. **认到的是哪颗 FTDI 芯片**（型号决定波特率分频编码，见 [FtdiBaud]）；
 * 2. **配置步骤哪一步失败**（控制传输逐步打日志，失败的那行就是原因）；
 * 3. **设备到底吐出什么字节**（原始 hex 可切换「含 / 不含 FTDI 状态字节」，
 *    两列并排就能看出每包开头那 2 个 modem 状态字节有没有剥干净）。
 *
 * 只读 + 手动重连，不改任何采集逻辑 —— 诊断页永远不该成为新的故障源。
 */
@Composable
fun TorqueDiagScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val torque = app.torque
    val logs by torque.logs.collectAsStateWithLifecycle()
    val state by torque.ui.collectAsStateWithLifecycle()

    // FTDI 设备列表：进页面查一次，之后靠按钮刷（不自动轮询 —— 诊断页不该一直占着 USB 管理器）
    var deviceNonce by remember { mutableIntStateOf(0) }
    var devices by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(deviceNonce) {
        devices = withContext(Dispatchers.IO) {
            runCatching {
                torque.usbDevices().map { d ->
                    FtdiUsb.describe(d) + if (torque.usbHasPermission(d)) "  已授权" else "  未授权"
                }
            }.getOrDefault(emptyList())
        }
    }

    var rawWithStatus by remember { mutableStateOf(false) }
    var showFrames by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("扭力计诊断（USB）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { torque.retryNow() }) {
                Icon(Icons.Default.Refresh, null, Modifier.padding(end = 4.dp)); Text("手动重连")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { deviceNonce++ }) { Text("刷新设备") }
            IconButton(onClick = { torque.clearLogs(); torque.clearFrames() }) {
                Icon(Icons.Default.DeleteSweep, "清空")
            }
        }

        // ── 链路状态 ──
        Text("链路：${linkText(state.link)}", style = MaterialTheme.typography.bodyMedium,
            color = when (state.link) {
                is TorqueLinkState.Open -> OkGreen
                is TorqueLinkState.Failed -> WarnOrange
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        Text("监听：${if (state.listen) "开" else "关"}　" +
            "累计收到 ${state.byteCount} 字节 / 解析 ${state.readingCount} 笔　" +
            "待补记 ${state.pendingCount} 组",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 解析缓冲：没认出来的尾巴如实显示 —— 格式又变了的话，这里会堆着几十字节
        Text(
            "解析缓冲 ${state.pendingBytes} 字节" +
                (if (state.droppedBytes > 0L) "　⚠ 已丢弃 ${state.droppedBytes} 字节（超过缓冲上限）" else "") +
                (if (state.lastReading != null) "　最近解析：${state.lastReading?.rawLine}" else ""),
            style = MaterialTheme.typography.labelMedium,
            color = if (state.pendingBytes > 16 || state.droppedBytes > 0L) WarnOrange
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.pendingBytes > 0) {
            Text("未识别：${state.pendingText}", style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace, color = WarnOrange,
                modifier = Modifier.padding(top = 2.dp))
        }
        state.lastError?.let {
            Text("⚠ $it", style = MaterialTheme.typography.labelMedium, color = NgRed)
        }

        // ── FTDI 设备 ──
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("USB 设备（只列 FTDI，VID 0403）", style = MaterialTheme.typography.labelLarge)
                if (devices.isEmpty()) {
                    Text("没找到 FTDI 设备：换一根数据线、换个 USB 口，或确认设备是以 USB 从机模式接到平板" +
                        "（部分平板只有一个口能当 USB Host）",
                        style = MaterialTheme.typography.labelSmall, color = WarnOrange,
                        modifier = Modifier.padding(top = 4.dp))
                } else {
                    devices.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text("认到芯片后先核对型号：FT232R 一族是 3MHz 分频（本项目默认假设），" +
                        "FT232H/FT4232H 是 12MHz —— 后者波特率编码要另算（见 FtdiBaud 的告警注释）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // ── 波特率（格式未定期间允许改；改完自动重连）──
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("波特率（手册 6.2 = 19200 8N2，格式实测后可改）",
                    style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(9600, 19200, 38400, 57600, 115200).forEach { b ->
                        FilterChip(
                            selected = state.baud == b,
                            onClick = { if (state.baud != b) torque.setBaud(b) },
                            label = { Text("$b") }
                        )
                    }
                }
                Text(
                    "分频 0x%04X（%s）　%s".format(
                        FtdiBaud.divisorFor(state.baud),
                        if (FtdiBaud.isExact(state.baud)) "精确" else
                            "近似，实际 %.1f bps".format(FtdiBaud.actualBaud(state.baud)),
                        if (FtdiBaud.KNOWN.containsKey(state.baud)) "与已知表一致 ✓"
                        else "⚠ 不在已知表里，属推算值"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ── 自动保存延时（用户要求：三次测完 N 秒内没重测就自动保存，默认 5 秒，可设定）──
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("自动保存延时（三次测完，超过这个时间没点「重测」就保存）",
                    style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3L, 5L, 10L, 15L, 30L).forEach { sec ->
                        val ms = sec * 1000
                        FilterChip(
                            selected = state.saveDelayMs == ms,
                            onClick = { if (state.saveDelayMs != ms) torque.setSaveDelay(ms) },
                            label = { Text("$sec 秒") }
                        )
                    }
                }
                Text(
                    if (state.saveAtMs != null) "本组正在倒计时（改完立刻按新延时重排）"
                    else "当前：${state.saveDelayMs / 1000.0} 秒（下次测满三笔生效）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ── 原始字节 / 日志 切换 ──
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(showFrames, { showFrames = true }, label = { Text("原始字节") })
            FilterChip(!showFrames, { showFrames = false }, label = { Text("操作日志") })
            if (showFrames) {
                FilterChip(rawWithStatus, { rawWithStatus = !rawWithStatus },
                    label = { Text(if (rawWithStatus) "显示：含状态字节" else "显示：已剥状态字节") })
            }
        }

        if (showFrames) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp).heightIn(min = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 不给 key：同一毫秒内两块相同报文（回显）会撞 key 直接崩，索引键更安全
                items(state.frames.asReversed()) { f ->
                    Column {
                        Text("${hms(f.atMs)}  ${f.text}", fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, lineHeight = 16.sp)
                        Text(
                            if (rawWithStatus) "含状态 ${f.rawHex}" else "有效 ${f.dataHex}",
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.frames.isEmpty()) {
                    item {
                        Text("还没有收到任何字节。先在设备上测一笔，再回来刷新这一页。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp).heightIn(min = 120.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(logs.asReversed()) { l ->
                    val color = when {
                        l.text.startsWith("✔") || l.text.startsWith("★") || l.text.startsWith("▶") -> OkGreen
                        l.text.startsWith("⚠") || l.text.startsWith("!!") || l.text.startsWith("!") -> NgRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text("${hms(l.timeMs)} ${l.text}", fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, lineHeight = 16.sp, color = color)
                }
                if (logs.isEmpty()) {
                    item { Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        Text("切换「含 / 已剥状态字节」对照看：FTDI 每个 USB 包开头有 2 个 modem 状态字节，" +
            "剥对了的话有效字节应以数据的第一个字符开头",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}

private fun linkText(l: TorqueLinkState): String = when (l) {
    is TorqueLinkState.Open -> l.desc
    is TorqueLinkState.Opening -> "正在打开…"
    is TorqueLinkState.NeedPermission -> "等待系统授权弹窗"
    is TorqueLinkState.NoDevice -> "未检测到 FTDI 设备"
    is TorqueLinkState.Failed -> "失败：${l.reason}"
    is TorqueLinkState.Off -> "监听已关闭"
}
