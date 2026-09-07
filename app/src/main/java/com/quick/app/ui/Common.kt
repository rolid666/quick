package com.quick.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 工具条标题（页面标题行） */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

/** 可搜索下拉选择器：点击打开对话框，输入过滤列表 */
@Composable
fun SearchableSelect(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.clickable { open = true },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected ?: "（未选择）", style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择$label")
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
