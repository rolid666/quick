package com.quick.app.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.collect.ConnState
import com.quick.app.ui.hms
import kotlinx.coroutines.launch

/**
 * 通信诊断页（对应验证测试 A~G 的人工观察窗口）：
 * - 帧级收发日志（hex，可与厂家报文示例核对）
 * - 手动全读一次并解码
 * 记录/核对方法见 docs/可行性分析与通讯规格-V1.0.md §15。
 */
@Composable
fun DiagnosticScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as QuickApp
    val controller = app.controller
    val scope = rememberCoroutineScope()
    val logs by controller.logs.collectAsStateWithLifecycle()
    val state by controller.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("通信诊断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (state.running) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("采集运行中：帧日志实时显示；手动探测需先停止采集",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            OutlinedButton(
                onClick = { scope.launch { controller.manualProbe() } },
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.padding(end = 4.dp)); Text("手动全读一次")
            }
            IconButton(onClick = { controller.clearLogs() }) { Icon(Icons.Default.DeleteSweep, "清空日志") }
        }

        Text("连接状态：${connText(state.conn)}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 断线计数：帧日志只有几百行会被刷掉，这里跨整个运行期累计 ——
        // 「读超时」多 = 链路/仪器侧；「对端关闭」多且每段连接都短 = 被主动踢；「仪器异常应答」多 = 协议层面
        if (state.dropStats.isNotEmpty()) {
            Text(
                "断线统计（自本次启动）：" +
                    state.dropStats.entries.joinToString("　") { "${it.key} ${it.value} 次" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC62828)
            )
        }
        // 迟到帧：仪器晚好几秒才回上一笔，应答落在当前连接上 —— 丢掉即可，不再断线（2026-09-26）
        if (state.staleFrames > 0L) {
            Text(
                "迟到帧（已丢弃，连接保持）：${state.staleFrames} 帧" +
                    "　—— 仪器应答偏慢的旁证；它涨而断线不涨，说明是仪器/网络慢，不是代码把连接判死",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF6C00)
            )
        }
        Text("日志含完整收发帧（每帧 hex 对应厂家报文格式：事务ID 协议ID 长度 单元ID 功能码 …）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(logs.asReversed(), key = { "${it.timeMs}-${it.text.hashCode()}" }) { l ->
                val color = when {
                    l.text.startsWith("★") || l.text.startsWith("✔") || l.text.startsWith("PROBE 解码") -> Color(0xFF2E7D32)
                    l.text.startsWith("⚠") || l.text.startsWith("!!") || l.text.startsWith("PROBE 失败") -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    "${hms(l.timeMs)} ${l.text}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (logs.isEmpty()) {
                item { Text("暂无日志。开启采集或点「手动全读一次」。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun connText(c: ConnState): String = when (c) {
    is ConnState.Connected -> "已连接 ${c.ip}:${c.port}"
    is ConnState.Connecting -> "连接中…"
    is ConnState.Reconnecting -> "重连中（第 ${c.attempt} 次）"
    is ConnState.Disconnected -> "未连接"
}
