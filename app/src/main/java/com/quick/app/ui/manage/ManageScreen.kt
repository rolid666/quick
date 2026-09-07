package com.quick.app.ui.manage

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quick.app.QuickApp
import com.quick.app.data.db.Line
import com.quick.app.data.db.Model
import com.quick.app.ui.ConfirmDialog
import com.quick.app.ui.EditNameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 线别/机种管理：搜索、新增、修改、删除。
 * 删除只影响配置列表；历史记录保留名称快照，不受影响。
 */
@Composable
fun ManageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    var isLine by remember { mutableStateOf(true) }
    val lines by db.lineDao().all().collectAsState(initial = emptyList())
    val models by db.modelDao().all().collectAsState(initial = emptyList())
    val allNames = if (isLine) lines.map { it.name } else models.map { it.name }

    var kw by remember { mutableStateOf("") }
    val shown = if (kw.isBlank()) allNames
        else allNames.filter { it.contains(kw.trim(), ignoreCase = true) }

    var addDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf<String?>(null) }   // 待修改的名字
    var deleteName by remember { mutableStateOf<String?>(null) } // 待删除的名字

    /** 新增(oldName=null)或改名：先查重再写库（库层还有唯一索引兜底，双保险） */
    fun save(oldName: String?, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // 预检：同名已存在（且不是自己）→ 明确提示，不让它撞唯一约束
                val dupExists = if (isLine) {
                    val d = db.lineDao().findByName(newName)
                    d != null && d.name != oldName
                } else {
                    val d = db.modelDao().findByName(newName)
                    d != null && d.name != oldName
                }
                if (dupExists) {
                    toast(ctx, "「$newName」已存在，请换一个名称")
                    return@launch
                }
                if (isLine) {
                    if (oldName == null) db.lineDao().insert(Line(name = newName))
                    else db.lineDao().findByName(oldName)?.let {
                        db.lineDao().update(it.copy(name = newName, updatedAt = System.currentTimeMillis()))
                    }
                } else {
                    if (oldName == null) db.modelDao().insert(Model(name = newName))
                    else db.modelDao().findByName(oldName)?.let {
                        db.modelDao().update(it.copy(name = newName, updatedAt = System.currentTimeMillis()))
                    }
                }
            } catch (e: Exception) {
                toast(ctx, "保存失败（可能重名）：${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 第一行：返回 + 标题 + 新增（独立成行，任何屏幕宽度下都不会与搜索重叠）
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text(if (isLine) "线别管理" else "机种管理",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { addDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.padding(end = 6.dp))
                Text("新增")
            }
        }
        // 第二行：类型切换 + 搜索（搜索占剩余宽度，窄屏自适应收缩）
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(isLine, { isLine = true }, label = { Text("线别") })
            FilterChip(!isLine, { isLine = false }, label = { Text("机种") })
            OutlinedTextField(
                value = kw, onValueChange = { kw = it }, singleLine = true,
                placeholder = { Text("搜索…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f)
            )
        }

        Text("共 ${shown.size} 条（删除不影响历史记录）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp))

        LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(shown, key = { it }) { name ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editName = name }) {
                        Icon(Icons.Default.Edit, "修改", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { deleteName = name }) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            if (shown.isEmpty()) {
                item {
                    Text("无记录（点右上角 + 新增）", Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (addDialog) {
        EditNameDialog(title = if (isLine) "新增线别" else "新增机种", initial = "") { result ->
            addDialog = false
            if (!result.isNullOrBlank()) save(null, result)
        }
    }
    editName?.let { old ->
        EditNameDialog(title = if (isLine) "修改线别" else "修改机种", initial = old) { result ->
            editName = null
            if (!result.isNullOrBlank() && result != old) save(old, result)
        }
    }
    deleteName?.let { name ->
        ConfirmDialog(
            title = if (isLine) "删除线别" else "删除机种",
            message = "确定删除「$name」？\n历史记录不受影响（仍保留名称），只是以后无法再选择它。",
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    if (isLine) db.lineDao().findByName(name)?.let { db.lineDao().delete(it) }
                    else db.modelDao().findByName(name)?.let { db.modelDao().delete(it) }
                }
            },
            onDismiss = { deleteName = null }
        )
    }
}

private fun toast(ctx: Context, msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
