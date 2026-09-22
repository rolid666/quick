package com.quick.app.ui.manage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quick.app.QuickApp
import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.DeviceSn
import com.quick.app.data.db.LIMIT_RMAX
import com.quick.app.data.db.LIMIT_VMAX
import com.quick.app.data.db.Line
import com.quick.app.data.db.LimitPreset
import com.quick.app.data.db.Model
import com.quick.app.data.db.SN_LEARNED
import com.quick.app.data.db.Station
import com.quick.app.data.db.TempPreset
import com.quick.app.data.db.limitPresetName
import com.quick.app.data.db.limitRegAddr
import com.quick.app.data.db.limitUnit
import com.quick.app.data.db.limitValueText
import com.quick.app.data.db.tempPresetName
import com.quick.app.ui.ConfirmDialog
import com.quick.app.ui.EditNameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 七个平级的字典（用户 2026-09 定）：
 * 前三个（线别/机种/站别）是生产信息，中间三个（温度设置/漏电压上限/接地电阻上限）+
 * 设备编号是**配到仪器里**的东西 —— 后四者在测量页可以合成一张组合配置二维码。
 */
private enum class DictKind(val label: String, val limitKind: String? = null) {
    Line("线别"), Model("机种"), Station("站别"),
    Temp("温度设置"),
    LimitV("漏电压上限", LIMIT_VMAX),
    LimitR("接地电阻上限", LIMIT_RMAX),
    Sn("设备编号");

    /** 是不是「单个数值」型字典（温度设置是两个数字，上限设置是一个带小数的数） */
    val isLimit: Boolean get() = limitKind != null
}

/**
 * 字典维护：搜索、新增、修改、删除。
 * 删除只影响配置列表；历史记录保留名称快照，不受影响。
 */
@Composable
fun ManageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(DictKind.Line) }
    // 五个列表都订阅：切换页签即时生效，无需重新查询
    val lines by db.lineDao().all().collectAsState(initial = emptyList())
    val models by db.modelDao().all().collectAsState(initial = emptyList())
    val stations by db.stationDao().all().collectAsState(initial = emptyList())
    val presets by db.tempPresetDao().all().collectAsState(initial = emptyList())
    val limits by db.limitPresetDao().all().collectAsState(initial = emptyList())
    val sns by db.deviceSnDao().all().collectAsState(initial = emptyList())

    // 列表行 = 名称 +（可选）右侧标记
    val rows: List<Pair<String, String?>> = when (kind) {
        DictKind.Line -> lines.map { it.name to null }
        DictKind.Model -> models.map { it.name to null }
        DictKind.Station -> stations.map { it.name to null }
        DictKind.Temp -> presets.map { it.name to "${it.setTemp}℃ ±${it.tolerance}" }
        // 上限设置的标记直接写「寄存器应为多少」：扫码后拿仪器状态一对就知道有没有配进去
        DictKind.LimitV, DictKind.LimitR -> limits.filter { it.kind == kind.limitKind }
            .map { it.name to "${limitRegAddr(it.kind)}=${it.valueRaw}" }
        DictKind.Sn -> sns.map { it.name to if (it.source == SN_LEARNED) "仪器" else "手输" }
    }

    var kw by remember { mutableStateOf("") }
    val shown = if (kw.isBlank()) rows
        else rows.filter { it.first.contains(kw.trim(), ignoreCase = true) }

    var addDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf<String?>(null) }   // 待修改的名字
    var deleteName by remember { mutableStateOf<String?>(null) } // 待删除的名字

    /** 新增(oldName=null)或改名：先查重再写库（库层还有唯一索引兜底，双保险） */
    fun save(oldName: String?, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // 预检：同名已存在（且不是自己）→ 明确提示，不让它撞唯一约束
                if (newName != oldName && db.hasName(kind, newName)) {
                    toast(ctx, "「$newName」已存在，请换一个名称")
                    return@launch
                }
                if (oldName == null) db.insertName(kind, newName) else db.rename(kind, oldName, newName)
            } catch (e: Exception) {
                toast(ctx, "保存失败（可能重名）：${e.message}")
            }
        }
    }

    /** 温度设置：两个数字（设定温度 + 误差范围），名称自动生成为 "350±20" */
    fun savePreset(oldName: String?, setTemp: Int, tolerance: Int) {
        val name = tempPresetName(setTemp, tolerance)
        scope.launch(Dispatchers.IO) {
            try {
                if (name != oldName && db.tempPresetDao().findByName(name) != null) {
                    toast(ctx, "「$name」已存在")
                    return@launch
                }
                val now = System.currentTimeMillis()
                val old = oldName?.let { db.tempPresetDao().findByName(it) }
                if (old == null) {
                    db.tempPresetDao().insert(
                        TempPreset(name = name, setTemp = setTemp, tolerance = tolerance)
                    )
                } else {
                    db.tempPresetDao().update(
                        old.copy(name = name, setTemp = setTemp, tolerance = tolerance, updatedAt = now)
                    )
                }
            } catch (e: Exception) {
                toast(ctx, "保存失败：${e.message}")
            }
        }
    }

    /** 上限设置：只输一个数（mV / Ω，最多一位小数），名称自动生成为 "2 mV" / "2.5 Ω" */
    fun saveLimit(oldName: String?, kind: String, valueRaw: Int) {
        val name = limitPresetName(kind, valueRaw)
        scope.launch(Dispatchers.IO) {
            try {
                if (name != oldName && db.limitPresetDao().find(kind, name) != null) {
                    toast(ctx, "「$name」已存在")
                    return@launch
                }
                val now = System.currentTimeMillis()
                val old = oldName?.let { db.limitPresetDao().find(kind, it) }
                if (old == null) {
                    db.limitPresetDao().insert(
                        LimitPreset(kind = kind, name = name, valueRaw = valueRaw)
                    )
                } else {
                    db.limitPresetDao().update(
                        old.copy(name = name, valueRaw = valueRaw, updatedAt = now)
                    )
                }
            } catch (e: Exception) {
                toast(ctx, "保存失败：${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 第一行：返回 + 标题 + 新增（独立成行，任何屏幕宽度下都不会与搜索重叠）
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("${kind.label}管理",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { addDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.padding(end = 6.dp))
                Text("新增")
            }
        }
        // 第二行：类型切换（可横向滚动，五个页签在手机上不挤）+ 搜索
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DictKind.entries.forEach { k ->
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
                DictKind.Temp -> "共 ${shown.size} 条（新增只需输「设定温度」和「误差范围」，名称自动生成）"
                DictKind.LimitV -> "共 ${shown.size} 条（单位 mV，最多一位小数；扫码后仪器 0x1D 应等于右侧数字）"
                DictKind.LimitR -> "共 ${shown.size} 条（单位 Ω，最多一位小数；扫码后仪器 0x1E 应等于右侧数字）"
                DictKind.Sn -> "共 ${shown.size} 条（「仪器」= 保存数据时从仪器读到的编号，自动收录）"
                else -> "共 ${shown.size} 条（删除不影响历史记录）"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(shown, key = { it.first }) { (name, tag) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    tag?.let {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.padding(end = 4.dp))
                    }
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
        when {
            kind == DictKind.Temp -> TempPresetDialog(title = "新增温度设置", preset = null) { set, tol ->
                addDialog = false
                if (set != null && tol != null) savePreset(null, set, tol)
            }
            kind.isLimit -> {
                // 先取出类别常量：对话框开着时切页签也不会把这一笔存到别的类别去
                val lk = kind.limitKind!!
                LimitPresetDialog(title = "新增${kind.label}", kind = lk, preset = null) { raw ->
                    addDialog = false
                    if (raw != null) saveLimit(null, lk, raw)
                }
            }
            else -> EditNameDialog(title = "新增${kind.label}", initial = "") { result ->
                addDialog = false
                if (!result.isNullOrBlank()) save(null, result)
            }
        }
    }
    editName?.let { old ->
        when {
            kind == DictKind.Temp -> TempPresetDialog(
                title = "修改温度设置",
                preset = presets.firstOrNull { it.name == old }
            ) { set, tol ->
                editName = null
                if (set != null && tol != null) savePreset(old, set, tol)
            }
            kind.isLimit -> {
                val lk = kind.limitKind!!
                LimitPresetDialog(
                    title = "修改${kind.label}",
                    kind = lk,
                    preset = limits.firstOrNull { it.kind == lk && it.name == old }
                ) { raw ->
                    editName = null
                    if (raw != null) saveLimit(old, lk, raw)
                }
            }
            else -> EditNameDialog(title = "修改${kind.label}", initial = old) { result ->
                editName = null
                if (!result.isNullOrBlank() && result != old) save(old, result)
            }
        }
    }
    deleteName?.let { name ->
        ConfirmDialog(
            title = "删除${kind.label}",
            message = if (kind == DictKind.Sn)
                "确定删除「$name」？\n只是从可选列表里去掉；已保存的历史记录不受影响。"
            else
                "确定删除「$name」？\n历史记录不受影响（仍保留名称），只是以后无法再选择它。",
            onConfirm = {
                scope.launch(Dispatchers.IO) { db.deleteName(kind, name) }
            },
            onDismiss = { deleteName = null }
        )
    }
}

/**
 * 温度设置编辑框：**只输两个数字** —— 设定温度 + 误差范围（用户 2026-09 要求），
 * 名称自动生成为 `350±20`，与二维码里的 TSET/TERR 一一对应。
 */
@Composable
private fun TempPresetDialog(
    title: String,
    preset: TempPreset?,
    onResult: (setTemp: Int?, tolerance: Int?) -> Unit
) {
    var setTemp by remember { mutableStateOf(preset?.setTemp?.toString() ?: "") }
    var tol by remember { mutableStateOf(preset?.tolerance?.toString() ?: "") }
    val setV = setTemp.trim().toIntOrNull()
    val tolV = tol.trim().toIntOrNull()
    val valid = setV != null && setV > 0 && tolV != null && tolV >= 0

    AlertDialog(
        onDismissRequest = { onResult(null, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("只输两个数字，名称自动生成（例：350 与 20 → 350±20）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = setTemp, onValueChange = { setTemp = it },
                    label = { Text("设定温度 (TSET, ℃)") }, singleLine = true,
                    isError = setTemp.isNotBlank() && (setV == null || setV <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tol, onValueChange = { tol = it },
                    label = { Text("误差范围 (TERR, ±℃)") }, singleLine = true,
                    isError = tol.isNotBlank() && (tolV == null || tolV < 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (valid) {
                    Text("将生成：${tempPresetName(setV, tolV)}　（合格区间 ${setV - tolV} ~ ${setV + tolV}℃）",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onResult(setV, tolV) }) { Text("确定") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onResult(null, null) }) { Text("取消") }
            }
        }
    )
}

/**
 * 上限设置编辑框（漏电压上限 / 接地电阻上限）：**只输一个数**，单位 mV / Ω，最多一位小数。
 * 名称自动生成（`2 mV` / `2.5 Ω`），与二维码里的 `VMAX` / `RMAX` 一一对应。
 *
 * 输入的是**整单位值**（厂家示例 `VMAX=2` 就是 2 mV），存库时 ×10 存寄存器原值
 * —— 界面下方直接把「扫码后仪器寄存器应该是多少」写出来，扫完就地核对，不用换算。
 */
@Composable
private fun LimitPresetDialog(
    title: String,
    kind: String,
    preset: LimitPreset?,
    onResult: (valueRaw: Int?) -> Unit
) {
    var text by remember { mutableStateOf(preset?.let { limitValueText(it.valueRaw) } ?: "") }
    val unit = limitUnit(kind)
    val cmd = if (kind == LIMIT_VMAX) "VMAX" else "RMAX"
    val reg = limitRegAddr(kind)
    val v = text.trim().toDoubleOrNull()
    val decimalsOk = text.trim().substringAfter('.', "").length <= 1
    // raw 是唯一的有效性判据：能换算出寄存器原值就算合法，valid 由它派生（避免两处条件各写一份）
    val raw = if (decimalsOk && v != null && v > 0 && v <= 999.9) Math.round(v * 10).toInt() else null
    val valid = raw != null

    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("只输一个数字，名称自动生成（例：2 → 2 $unit）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("$cmd 上限 ($unit)") }, singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank() && !valid) {
                    Text("请输入正数，最多一位小数", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (raw != null) {
                    Text("二维码内容：$cmd=${limitValueText(raw)}", style = MaterialTheme.typography.bodyMedium)
                    Text("扫码后仪器 $reg 应变为 $raw（寄存器按 ×0.1 计，$raw = ${limitValueText(raw)} $unit）—— " +
                        "扫完在本页最下方「仪器状态」核对",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onResult(raw) }) { Text("确定") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onResult(null) }) { Text("取消") }
            }
        }
    )
}

// ---------- 七张字典表走同一套操作 ----------

private suspend fun AppDatabase.hasName(kind: DictKind, name: String): Boolean = when (kind) {
    DictKind.Line -> lineDao().findByName(name) != null
    DictKind.Model -> modelDao().findByName(name) != null
    DictKind.Station -> stationDao().findByName(name) != null
    DictKind.Temp -> tempPresetDao().findByName(name) != null
    DictKind.LimitV, DictKind.LimitR -> limitPresetDao().find(kind.limitKind!!, name) != null
    DictKind.Sn -> deviceSnDao().findByName(name) != null
}

/** 新增一个只有名称的字典项（温度设置/上限设置有各自入口，不在这里） */
private suspend fun AppDatabase.insertName(kind: DictKind, name: String) {
    when (kind) {
        DictKind.Line -> lineDao().insert(Line(name = name))
        DictKind.Model -> modelDao().insert(Model(name = name))
        DictKind.Station -> stationDao().insert(Station(name = name))
        DictKind.Temp -> {}                                  // 由 TempPresetDialog 走 savePreset
        DictKind.LimitV, DictKind.LimitR -> {}               // 由 LimitPresetDialog 走 saveLimit
        DictKind.Sn -> deviceSnDao().insert(DeviceSn(name = name))
    }
}

private suspend fun AppDatabase.rename(kind: DictKind, oldName: String, newName: String) {
    val now = System.currentTimeMillis()
    when (kind) {
        DictKind.Line -> lineDao().findByName(oldName)?.let {
            lineDao().update(it.copy(name = newName, updatedAt = now))
        }
        DictKind.Model -> modelDao().findByName(oldName)?.let {
            modelDao().update(it.copy(name = newName, updatedAt = now))
        }
        DictKind.Station -> stationDao().findByName(oldName)?.let {
            stationDao().update(it.copy(name = newName, updatedAt = now))
        }
        DictKind.Temp -> tempPresetDao().findByName(oldName)?.let {
            tempPresetDao().update(it.copy(name = newName, updatedAt = now))
        }
        // 上限设置没有独立改名入口（名字由数值生成，改名等于改数值，走 saveLimit）
        DictKind.LimitV, DictKind.LimitR -> {}
        DictKind.Sn -> deviceSnDao().findByName(oldName)?.let {
            deviceSnDao().update(it.copy(name = newName, updatedAt = now))
        }
    }
}

private suspend fun AppDatabase.deleteName(kind: DictKind, name: String) {
    when (kind) {
        DictKind.Line -> lineDao().findByName(name)?.let { lineDao().delete(it) }
        DictKind.Model -> modelDao().findByName(name)?.let { modelDao().delete(it) }
        DictKind.Station -> stationDao().findByName(name)?.let { stationDao().delete(it) }
        DictKind.Temp -> tempPresetDao().findByName(name)?.let { tempPresetDao().delete(it) }
        DictKind.LimitV, DictKind.LimitR -> limitPresetDao().find(kind.limitKind!!, name)?.let {
            limitPresetDao().delete(it)
        }
        DictKind.Sn -> deviceSnDao().findByName(name)?.let { deviceSnDao().delete(it) }
    }
}

private fun toast(ctx: Context, msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
