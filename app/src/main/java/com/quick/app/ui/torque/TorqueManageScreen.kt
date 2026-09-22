package com.quick.app.ui.torque

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quick.app.QuickApp
import com.quick.app.data.db.TQ_DEVICE
import com.quick.app.data.db.TQ_LINE
import com.quick.app.data.db.TQ_MODEL
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TORQUE_UNIT
import com.quick.app.data.db.TorqueDict
import com.quick.app.data.db.torqueDictLabel
import com.quick.app.data.db.torqueNumText
import com.quick.app.data.db.torqueRangeName
import com.quick.app.data.db.torqueValueText
import com.quick.app.torque.TorqueScan
import com.quick.app.ui.ConfirmDialog
import com.quick.app.ui.EditNameDialog
import com.quick.app.ui.NgRed
import com.quick.app.ui.WarnOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 扭力计字典维护：线别 / 机种 / 扭矩范围 / 设备信息（用户要求 2，参考烙铁的管理页）。
 *
 * 与烙铁管理页的关系：交互一样，**表不一样、DAO 不一样、代码各一份**。
 * 这里维护的全部是 `torque_dict`，任何一个操作都碰不到烙铁的七张字典表。
 *
 * 「设备信息」页签多一个**扫码新增**（用户要求 4）：调后置摄像头扫铭牌/标签，
 * 扫到的内容经 [TorqueScan.extractName] 归一化（`SN=QK-HT-001,TSET=350` → `QK-HT-001`）后入库。
 *
 * **「扭矩范围」是唯一带数值的类别**（用户要求 5）：
 * 新增/修改时填**扭矩下限 + 扭矩上限**两个数（单位 kgf*cm），
 * 名称由两个数**自动生成**（与「温度设置」「上限设置」同一套做法）——
 * 操作员不打字、也不会写错单位。判定 OK/NG 用的就是这两个数（用户要求 6）。
 */
private enum class TorqueDictKind(val kind: String) {
    Line(TQ_LINE), Model(TQ_MODEL), Range(TQ_RANGE), Device(TQ_DEVICE);

    val label: String get() = torqueDictLabel(kind)
}

@Composable
fun TorqueManageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(TorqueDictKind.Line) }
    val all by db.torqueDictDao().allKinds().collectAsState(initial = emptyList())

    val rows: List<TorqueDict> = remember(all, kind) { all.filter { it.kind == kind.kind } }

    var kw by remember { mutableStateOf("") }
    val shown = remember(rows, kw) {
        if (kw.isBlank()) rows else rows.filter { it.name.contains(kw.trim(), ignoreCase = true) }
    }

    var addDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<TorqueDict?>(null) }
    var deleteName by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    /** 新增（无数值的类别）：先查重再写库（库层 (kind,name) 唯一索引兜底，双保险） */
    fun add(name: String) {
        scope.launch(Dispatchers.IO) {
            try {
                if (db.torqueDictDao().find(kind.kind, name) != null) {
                    toast(ctx, "「$name」已存在，请换一个名称")
                    return@launch
                }
                db.torqueDictDao().insert(TorqueDict(kind = kind.kind, name = name))
            } catch (e: Exception) {
                toast(ctx, "保存失败（可能重名）：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 扭矩范围：名称自动生成，两个数一起落库（判定就靠它们） */
    fun saveRange(old: TorqueDict?, min: Double, max: Double) {
        val name = torqueRangeName(min, max)
        scope.launch(Dispatchers.IO) {
            try {
                val exist = db.torqueDictDao().find(TQ_RANGE, name)
                if (exist != null && exist.id != old?.id) {
                    toast(ctx, "「$name」已存在，请改数值或直接用它")
                    return@launch
                }
                if (old == null) {
                    db.torqueDictDao().insert(
                        TorqueDict(kind = TQ_RANGE, name = name, minValue = min, maxValue = max)
                    )
                } else {
                    db.torqueDictDao().update(
                        old.copy(
                            name = name, minValue = min, maxValue = max,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                toast(ctx, "保存失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("${kind.label}管理", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            // 扫码只给「设备信息」：铭牌上的编号是扫出来的，线别/机种是选的
            if (kind == TorqueDictKind.Device) {
                FilledTonalButton(onClick = { scanning = true }) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.padding(end = 6.dp))
                    Text("扫码新增")
                }
                Spacer(Modifier.padding(end = 8.dp))
            }
            FilledTonalButton(onClick = { addDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.padding(end = 6.dp)); Text("新增")
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorqueDictKind.entries.forEach { k ->
                    FilterChip(kind == k, { kind = k }, label = { Text(k.label) })
                }
            }
        }

        OutlinedTextField(
            value = kw, onValueChange = { kw = it }, singleLine = true,
            placeholder = { Text("搜索…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Text(
            when (kind) {
                TorqueDictKind.Device ->
                    "共 ${shown.size} 条（「扫码新增」调后置摄像头；扫到 SN=xxx,… 时只取编号）"
                TorqueDictKind.Range ->
                    "共 ${shown.size} 条（每条 = 下限 + 上限，名称自动生成；判定 OK/NG 就用这两个数）"
                else ->
                    "共 ${shown.size} 条（只是扭力计这边可选的名称，删除不影响已保存的记录）"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(shown, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        if (item.kind == TQ_RANGE) {
                            val missing = item.minValue == null || item.maxValue == null
                            Text(
                                if (missing)
                                    "⚠ 缺上下限（旧数据），点铅笔补填后才能用于判定"
                                else "下限 ${torqueValueText(item.minValue)}　上限 " +
                                    "${torqueValueText(item.maxValue)}　单位 $TORQUE_UNIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (missing) WarnOrange
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (item.kind == TQ_RANGE && (item.minValue == null || item.maxValue == null)) {
                        Icon(Icons.Default.Warning, null, tint = WarnOrange,
                            modifier = Modifier.padding(end = 4.dp))
                    }
                    IconButton(onClick = { editItem = item }) {
                        Icon(Icons.Default.Edit, "修改", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { deleteName = item.name }) {
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
        // 先取出类别：对话框开着时切页签，也不会把这一笔存到别的类别去
        val k = kind
        if (k == TorqueDictKind.Range) {
            TorqueRangeDialog(title = "新增扭矩范围", initial = null,
                onDismiss = { addDialog = false }) { min, max ->
                addDialog = false
                saveRange(null, min, max)
            }
        } else {
            EditNameDialog(title = "新增${k.label}", initial = "") { result ->
                addDialog = false
                if (!result.isNullOrBlank()) add(result)
            }
        }
    }

    editItem?.let { item ->
        if (item.kind == TQ_RANGE) {
            TorqueRangeDialog(title = "修改扭矩范围", initial = item,
                onDismiss = { editItem = null }) { min, max ->
                editItem = null
                saveRange(item, min, max)
            }
        } else {
            EditNameDialog(title = "修改${torqueDictLabel(item.kind)}", initial = item.name) { result ->
                editItem = null
                if (!result.isNullOrBlank() && result != item.name) {
                    scope.launch(Dispatchers.IO) {
                        if (db.torqueDictDao().find(item.kind, result) != null) {
                            toast(ctx, "「$result」已存在，请换一个名称")
                        } else {
                            db.torqueDictDao().update(
                                item.copy(name = result, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                }
            }
        }
    }

    deleteName?.let { name ->
        ConfirmDialog(
            title = "删除${kind.label}",
            message = "确定删除「$name」？\n已保存的扭力计记录不受影响（仍保留名称），只是以后无法再选择它。",
            onConfirm = { scope.launch(Dispatchers.IO) { db.torqueDictDao().delete(kind.kind, name) } },
            onDismiss = { deleteName = null }
        )
    }

    if (scanning) {
        ScanDialog(
            title = "扫码新增设备信息",
            hint = "对准设备铭牌上的二维码/条码；扫到 SN=xxx,… 形式的内容时只取编号本身。" +
                "扫不出来可在下面手动输入。"
        ) { text ->
            scanning = false
            val name = TorqueScan.extractName(text)
            if (name != null) add(name)
        }
    }
}

/**
 * 扭矩范围新增/修改对话框：**两个数**（下限 + 上限，单位 kgf*cm）。
 *
 * 名称**自动生成**并实时预览（用户要求 5）—— 操作员只填两个数，
 * 也就避免了「名称写 5kg 但上限填 6」这种判据与名字对不上的情况。
 * 校验：两个数都必须是数字、上限必须 ≥ 下限（先卡住，不让它变成一条永远 NG 的范围）。
 */
@Composable
private fun TorqueRangeDialog(
    title: String,
    initial: TorqueDict?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var minText by remember {
        mutableStateOf(initial?.minValue?.let { torqueNumText(it) } ?: "")
    }
    var maxText by remember {
        mutableStateOf(initial?.maxValue?.let { torqueNumText(it) } ?: "")
    }
    val min = minText.trim().toDoubleOrNull()
    val max = maxText.trim().toDoubleOrNull()
    val err = when {
        minText.isNotBlank() && min == null -> "下限请填数字（如 0.5）"
        maxText.isNotBlank() && max == null -> "上限请填数字（如 6）"
        min != null && max != null && max < min -> "上限不能小于下限"
        else -> null
    }
    val ok = min != null && max != null && err == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("扭矩下限") },
                        singleLine = true,
                        isError = err != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("扭矩上限") },
                        singleLine = true,
                        isError = err != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("单位：$TORQUE_UNIT（设备实测单位，不用另填）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                Text(
                    if (ok) "名称自动生成：${torqueRangeName(min!!, max!!)}"
                    else "填完两个数后自动生成名称",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("判定：平均值在这两个数之间（含边界）→ OK，否则 NG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
                err?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = NgRed,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = { onConfirm(min!!, max!!) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun toast(ctx: Context, msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
