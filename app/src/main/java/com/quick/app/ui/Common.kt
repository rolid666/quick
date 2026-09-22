package com.quick.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quick.app.qr.QrGen

/** 工具条标题（页面标题行） */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

/**
 * 可搜索下拉选择器：点击打开对话框，输入过滤列表。
 *
 * [highlight] = true 时整块变成醒目告警样式（橙框 + 橙底 + 加粗橙字 + ⚠前缀）。
 * 默认 false —— 烙铁那边原样不动，只有扭力计「三次测完但信息没选全」时才点亮
 * （用户 2026-09-22 要求：未选的信息字体要变得明显，选择后恢复正常）。
 */
@Composable
fun SearchableSelect(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val warn = WarnOrange
    Surface(
        modifier = modifier.clickable { open = true },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (highlight) 0.dp else 2.dp,
        color = if (highlight) warn.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = if (highlight) BorderStroke(2.dp, warn) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (highlight) "⚠ $label" else label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (highlight) warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (highlight) FontWeight.Bold else null
                )
                Text(
                    selected ?: "（未选择）",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (highlight) warn else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (highlight) FontWeight.Bold else null
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择$label",
                tint = if (highlight) warn else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        SearchDialog(
            title = label,
            options = options,
            selected = selected,
            onConfirm = { onSelect(it); open = false },
            onDismiss = { open = false }
        )
    }
}

@Composable
fun SearchDialog(
    title: String,
    options: List<String>,
    selected: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
    allowClear: Boolean = true,
    allowAdd: Boolean = false
) {
    var kw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = kw,
                    onValueChange = { kw = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("输入以过滤…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val filtered = remember(options, kw) {
                    if (kw.isBlank()) options else options.filter { it.contains(kw.trim(), ignoreCase = true) }
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(filtered, key = { it }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(item) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (item == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item { Text("无匹配项", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = {
            if (allowAdd && kw.isNotBlank()) {
                TextButton(onClick = { onConfirm(kw.trim()) }) {
                    Icon(Icons.Default.Add, null)
                    Text(" 新增并选择")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (allowClear) {
                    TextButton(onClick = { onConfirm(null) }) { Text("清除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 文本编辑对话框：返回 null = 取消；空 = 待删；否则编辑值 */
@Composable
fun EditNameDialog(
    title: String,
    initial: String,
    onResult: (String?) -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true)
        },
        confirmButton = {
            Button(onClick = { onResult(v.trim()) }, enabled = v.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("取消") } }
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 二维码弹窗：只负责把已生成的 payload 画成二维码 + 原样显示文本。
 * （配网二维码有输入表单，见 ConfigScreen.WifiQrDialog；这里是「温度 + 设备编号」的组合配置码）
 */
@Composable
fun QrDialog(
    title: String,
    payload: String,
    note: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val size = 320.dp.coerceAtMost(maxWidth)
                    val bmp = remember(payload) { QrGen.qrBitmap(payload) }
                    Image(
                        bmp.asImageBitmap(), contentDescription = title,
                        modifier = Modifier.size(size)
                    )
                }
                Text(payload, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

/** 提示行（彩色圆点 + 文本） */
@Composable
fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 8.dp)) {
                Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.padding(0.dp)) {
                    Box(Modifier.padding(6.dp))
                }
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}
