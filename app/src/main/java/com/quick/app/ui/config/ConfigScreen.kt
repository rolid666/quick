package com.quick.app.ui.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.quick.app.QuickApp
import com.quick.app.config.CommProfile
import com.quick.app.config.CommProfiles
import com.quick.app.config.ConfigBackup
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.DeviceConfig
import com.quick.app.net.LocalIp
import com.quick.app.qr.QrGen
import com.quick.app.security.DeletePassword
import com.quick.app.ui.SearchableSelect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConfigScreen(onBack: () -> Unit, openManage: () -> Unit, openDiag: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val controller = app.controller
    val scope = rememberCoroutineScope()

    // 表单本地状态（从 DB 载入一次；二维码同步 IP 后由 onSynced 重新载入）
    var cfg by remember { mutableStateOf<DeviceConfig?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(reloadTick) {
        cfg = db.configDao().getOnce()
    }

    // 配网二维码 dialog
    var showWifiQr by remember { mutableStateOf(false) }
    var showPwdDialog by remember { mutableStateOf(false) }
    var pwdIsDefault by remember { mutableStateOf(true) }
    LaunchedEffect(reloadTick, showPwdDialog) {
        pwdIsDefault = DeletePassword.isDefault(db)
    }

    // 导入配置：SAF 选文件
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                val result = runCatching { ConfigBackup.import(db, text ?: throw IllegalArgumentException("读取失败")) }
                if (text == null || result.isFailure) {
                    toast(ctx, "导入失败：${result?.exceptionOrNull()?.message ?: "无法读取文件"}")
                } else {
                    val r = result.getOrThrow()
                    controller.reloadConfig()
                    toast(
                        ctx, "导入完成：线别 +${r.importedLines}，机种 +${r.importedModels}，" +
                            "站别 +${r.importedStations}，温度设置 +${r.importedPresets}，" +
                            "上限设置 +${r.importedLimits}，设备编号 +${r.importedDeviceSns}，" +
                            "扭力计字典 +${r.importedTorqueDicts}，仪器配置已更新"
                    )
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // ---- 七个字典的管理入口（配置页的主角） ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("线别 / 机种 / 站别 / 温度设置 / 漏电压上限 / 接地电阻上限 / 设备编号",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("七个列表的新增、修改、删除、搜索（删除不影响已有历史记录）。" +
                    "温度设置 = 设定温度 ± 误差范围，漏电压上限 / 接地电阻上限 = 单个数值，" +
                    "设备编号 = 仪器上的 SN —— 这四项在测量页可合成一张配置二维码。",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = openManage, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.Default.Article, null, Modifier.padding(end = 6.dp))
                    Text("进入管理")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 4.dp))
                }
            }
        }

        // ---- 仪器通信配置 ----
        val c = cfg
        if (c != null) {
            var ip by remember(c) { mutableStateOf(c.ip) }
            var port by remember(c) { mutableStateOf(c.port.toString()) }
            var unitId by remember(c) { mutableStateOf(c.unitId.toString()) }
            var poll by remember(c) { mutableStateOf(c.pollIntervalMs.toString()) }
            var timeout by remember(c) { mutableStateOf(c.timeoutMs.toString()) }
            var autoStart by remember(c) { mutableStateOf(c.autoStart) }
            var profiles by remember { mutableStateOf<List<CommProfile>>(emptyList()) }
            var picked by remember { mutableStateOf<CommProfile?>(null) }
            LaunchedEffect(c) { profiles = CommProfiles.load(db) }

            Card(Modifier.fillMaxWidth()) {
                // 响应式：平板 IP/端口/UnitID 一行排开；手机窄屏 IP 独占一行，其余两两一行
                BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                    val wide = maxWidth >= 600.dp
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("仪器通信（MODBUS TCP/IP）", style = MaterialTheme.typography.titleMedium)

                    // ---- 通信配置历史（整组保存：IP/端口/UnitID/轮询/超时）----
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchableSelect(
                            label = "通信配置历史",
                            options = profiles.map { it.label },
                            selected = picked?.label,
                            onSelect = { label ->
                                val p = profiles.firstOrNull { it.label == label }
                                picked = p
                                if (p != null) {
                                    ip = p.ip
                                    port = p.port.toString()
                                    unitId = p.unitId.toString()
                                    poll = p.pollIntervalMs.toString()
                                    timeout = p.timeoutMs.toString()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val p = picked ?: return@TextButton
                                scope.launch(Dispatchers.IO) {
                                    profiles = CommProfiles.remove(db, p)
                                    picked = null
                                }
                            },
                            enabled = picked != null
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Text(" 删除此条", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (profiles.isEmpty()) {
                        Text("保存过的通信配置会出现在这里，下次直接选，不用重填。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(ip, { ip = it }, label = { Text("仪器 IP（MB-TCP 下为仪器静态 IP）") },
                                singleLine = true, modifier = Modifier.weight(1.6f))
                            OutlinedTextField(port, { port = it }, label = { Text("端口") }, singleLine = true,
                                modifier = Modifier.weight(0.7f))
                            OutlinedTextField(unitId, { unitId = it }, label = { Text("Unit ID") }, singleLine = true,
                                modifier = Modifier.weight(0.7f))
                        }
                    } else {
                        OutlinedTextField(ip, { ip = it }, label = { Text("仪器 IP（仪器静态 IP）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(port, { port = it }, label = { Text("端口") }, singleLine = true,
                                modifier = Modifier.weight(1f))
                            OutlinedTextField(unitId, { unitId = it }, label = { Text("Unit ID") }, singleLine = true,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(poll, { poll = it }, label = { Text("轮询周期(ms)") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("超时(ms)") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(autoStart, { autoStart = it })
                        Text("启动 App 自动采集", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                    Text("默认端口 502、Unit ID 1、轮询 900ms（仪器结果标志只保持约 1s，周期须小于它）。" +
                        "保存后对下一次连接生效；若正在采集请重启采集。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val newCfg = DeviceConfig(
                                    id = 1, name = c.name,
                                    ip = ip.trim(), port = port.trim().toIntOrNull() ?: 502,
                                    unitId = unitId.trim().toIntOrNull() ?: 1,
                                    pollIntervalMs = poll.trim().toLongOrNull() ?: 900,
                                    timeoutMs = timeout.trim().toLongOrNull() ?: 2000,
                                    autoStart = autoStart
                                )
                                scope.launch(Dispatchers.IO) {
                                    db.configDao().put(newCfg)
                                    controller.reloadConfig()
                                    // 保存即记入历史（IP+端口+UnitID 相同则更新并置顶）
                                    profiles = CommProfiles.remember(db, newCfg)
                                    picked = profiles.firstOrNull { it.ip == newCfg.ip }
                                }
                                toast(ctx, "配置已保存（已记入通信配置历史）")
                            },
                            enabled = ip.isNotBlank()
                        ) { Text("保存通信配置") }
                        TextButton(
                            onClick = {
                                ip = ""; port = "502"; unitId = "1"
                                poll = "900"; timeout = "2000"
                                picked = null
                            }
                        ) { Text("清空表单") }
                    }
                }
            }
        }
        }

        // ---- 配网二维码（192AF+ 首次联网用） ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("192AF+ 配网二维码", style = MaterialTheme.typography.titleMedium)
                Text("填写无线路由，生成二维码给仪器扫；二维码里的 ServerIP/端口就是上面「仪器通信」里的仪器 IP 与端口" +
                    "（MB-TCP：ServerIP 是仪器自身 IP，非本机）。生成时会自动保持一致。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { showWifiQr = true }) {
                    Icon(Icons.Default.QrCode, null, Modifier.padding(end = 6.dp)); Text("生成配网二维码")
                }
            }
        }

        // ---- 配置备份 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("配置备份（不含测量历史）", style = MaterialTheme.typography.titleMedium)
                Text("导出：线别/机种/站别/温度设置/上限设置/设备编号/仪器通信参数，以及扭力计的" +
                    "线别/机种/扭矩范围/设备信息 → JSON 文件，可复制到另一台平板；" +
                    "导入：同名项自动跳过。删除密码与通信配置历史不参与备份，" +
                    "两边的测量历史（烙铁、扭力计）都不在备份范围内。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val json = ConfigBackup.export(db)
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
                            val f = File(dir, "配置备份_$stamp.json")
                            f.writeText(json, Charsets.UTF_8)
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                            val it = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(it, "分享配置备份"))
                            toast(ctx, "配置已导出")
                        }
                    }) {
                        Icon(Icons.Default.FileUpload, null, Modifier.padding(end = 6.dp)); Text("导出配置")
                    }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Icon(Icons.Default.FileOpen, null, Modifier.padding(end = 6.dp)); Text("导入配置")
                    }
                }
            }
        }

        // ---- 删除记录用的密码 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("记录删除密码", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (pwdIsDefault) "当前是出厂默认密码，建议改成现场自己的密码。"
                    else "已设置自定义密码（只保存在本机，不随配置备份导出）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pwdIsDefault) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("删除测量记录时需要输入此密码；查询、导出、采集都不需要。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { showPwdDialog = true }) {
                    Icon(Icons.Default.Lock, null, Modifier.padding(end = 6.dp))
                    Text(if (pwdIsDefault) "设置密码" else "修改密码")
                }
            }
        }

        // ---- 通信诊断 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("通信诊断", style = MaterialTheme.typography.titleMedium)
                Text("帧级收发日志与寄存器读数，用于现场验证",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openDiag) {
                    Icon(Icons.Default.BugReport, null, Modifier.padding(end = 6.dp)); Text("打开诊断页")
                }
            }
        }
    }

    if (showWifiQr) {
        WifiQrDialog(
            app = app,
            // 生成二维码时若把 IP/端口同步进了配置，这里重新载入表单，保证两面显示一致
            onSynced = { reloadTick++ },
            onDismiss = { showWifiQr = false }
        )
    }

    if (showPwdDialog) {
        ChangePasswordDialog(
            db = db,
            isDefault = pwdIsDefault,
            onDone = { reloadTick++ },
            onDismiss = { showPwdDialog = false }
        )
    }
}

// ---------- 修改删除密码 ----------

@Composable
private fun ChangePasswordDialog(
    db: com.quick.app.data.db.AppDatabase,
    isDefault: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isDefault) "设置删除密码" else "修改删除密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDefault) {
                    Text("出厂默认密码是 1234。设置新密码后默认密码立即失效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = old, onValueChange = { old = it; err = null },
                    label = { Text(if (isDefault) "当前密码（默认 1234）" else "当前密码") },
                    singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = n1, onValueChange = { n1 = it; err = null },
                    label = { Text("新密码（至少 4 位）") },
                    singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = n2, onValueChange = { n2 = it; err = null },
                    label = { Text("再输一次新密码") },
                    singleLine = true, enabled = !busy,
                    isError = err != null,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { err?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && old.isNotEmpty() && n1.isNotEmpty(),
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            when {
                                !DeletePassword.verify(db, old) -> err = "当前密码不对"
                                n1.length < 4 -> err = "新密码至少 4 位"
                                n1 != n2 -> err = "两次输入的新密码不一致"
                                else -> {
                                    DeletePassword.set(db, n1)
                                    toast(ctx, "密码已修改")
                                    onDone()
                                    onDismiss()
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            err = "修改失败：${e.message ?: e.javaClass.simpleName}"
                        }
                        busy = false
                    }
                }
            ) { Text(if (busy) "处理中…" else "确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}

// ---------- 配网二维码 ----------

/** 上次输入的配网参数（记忆用，存 app_setting，仅本机）。仪器 IP/端口不在这里 —— 它们以「仪器通信」为准 */
private const val KEY_WIFI_QR_LAST = "wifi.qr.last"

@Composable
private fun WifiQrDialog(app: QuickApp, onSynced: () -> Unit, onDismiss: () -> Unit) {
    val cfg by app.db.configDao().get().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var wifiName by remember { mutableStateOf("") }
    var wifiPwd by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("") }
    // 与「仪器通信」同一份值：初始取配置，生成时写回配置
    var devIp by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("502") }

    var payload by remember { mutableStateOf<String?>(null) }
    var ifaces by remember { mutableStateOf(LocalIp.list()) }

    // 打开弹窗：读本机网络地址 + 回填 Wi-Fi 参数（不用每次重输）
    LaunchedEffect(Unit) {
        ifaces = LocalIp.list()
        val saved = runCatching { app.db.settingDao().get(KEY_WIFI_QR_LAST) }.getOrNull()
        if (!saved.isNullOrBlank()) {
            runCatching {
                val o = JSONObject(saved)
                wifiName = o.optString("wifiName", "")
                wifiPwd = o.optString("wifiPassword", "")
                gateway = o.optString("gateway", "")
            }
        }
    }
    // 仪器 IP / 端口始终以配置页为准（配置异步加载完成后回填一次）
    var filledFromCfg by remember { mutableStateOf(false) }
    LaunchedEffect(cfg) {
        cfg?.let {
            if (!filledFromCfg) {
                devIp = it.ip
                port = it.port.toString()
                filledFromCfg = true
            }
        }
    }
    val cfgIp = cfg?.ip.orEmpty()
    val cfgPort = cfg?.port ?: 502
    val sameAsCfg = devIp.trim() == cfgIp && (port.trim().toIntOrNull() ?: 502) == cfgPort

    /** 生成即记忆：Wi-Fi 名/密码/网关下次自动回填 */
    fun rememberInputs() {
        val o = JSONObject()
            .put("wifiName", wifiName.trim())
            .put("wifiPassword", wifiPwd.trim())
            .put("gateway", gateway.trim())
        scope.launch(Dispatchers.IO) {
            runCatching { app.db.settingDao().put(AppSetting(KEY_WIFI_QR_LAST, o.toString())) }
        }
    }

    /** 把二维码里的仪器 IP / 端口写回「仪器通信」配置 —— 两处永远一致（用户要求） */
    fun syncToConfig() {
        val newIp = devIp.trim()
        val newPort = port.trim().toIntOrNull() ?: 502
        if (newIp.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val cur = app.db.configDao().getOnce() ?: return@launch
            if (cur.ip == newIp && cur.port == newPort) return@launch
            app.db.configDao().put(cur.copy(ip = newIp, port = newPort))
            app.controller.reloadConfig()
            onSynced()
            // Application 本身即 Context，这里不需要 Activity
            toast(app, "已同步到仪器通信配置：$newIp:$newPort")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("生成 192AF+ 配网二维码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("生成后仪器在 [无线协议=MB-TCP] 下依次扫描配置。示例：WifiName=Quick-service,WifiPassword=…,ServerIP=192.168.x.x,ServerPort=502,GateWay=…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // ---- 本机网络地址：手机热点无法手动设 IP，仪器必须配同网段地址 ----
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("本机网络地址", style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { ifaces = LocalIp.list() }) { Text("刷新") }
                        }
                        if (ifaces.isEmpty()) {
                            Text("未读取到 IP：请先连接 Wi-Fi 或开启热点，再点「刷新」。",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            ifaces.forEach { f ->
                                Text("${f.label}（${f.name}）　${f.ip}",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            val primary = ifaces.first()
                            Text("仪器 ServerIP 需与本机同网段：${primary.subnet}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { devIp = LocalIp.suggestInstrumentIp(primary) }) {
                                Text("填入建议 IP ${LocalIp.suggestInstrumentIp(primary)}")
                            }
                        }
                    }
                }

                OutlinedTextField(wifiName, { wifiName = it }, label = { Text("Wi-Fi 名 (WifiName)") }, singleLine = true)
                OutlinedTextField(wifiPwd, { wifiPwd = it }, label = { Text("Wi-Fi 密码 (WifiPassword)") }, singleLine = true)
                OutlinedTextField(devIp, { devIp = it }, label = { Text("仪器静态 IP (ServerIP)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(port, { port = it }, label = { Text("端口 (ServerPort)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(gateway, { gateway = it }, label = { Text("网关 (GateWay，可选)") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                Text(
                    if (sameAsCfg) "✔ 与「仪器通信」中的仪器 IP / 端口一致（$cfgIp:$cfgPort）"
                    else "⚠ 与「仪器通信」当前值（$cfgIp:$cfgPort）不一致 —— 点「生成二维码」会自动同步过去",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sameAsCfg) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            rememberInputs()
                            syncToConfig()
                            payload = QrGen.wifiConfigPayload(
                                wifiName.trim(), wifiPwd.trim(), devIp.trim(),
                                port.trim().toIntOrNull() ?: 502,
                                gateway.trim().ifEmpty { null }
                            )
                        },
                        enabled = wifiName.isNotBlank() && devIp.isNotBlank()
                    ) { Text("生成二维码") }
                    TextButton(onClick = { syncToConfig() }, enabled = !sameAsCfg && devIp.isNotBlank()) {
                        Text("只同步到仪器通信")
                    }
                }

                payload?.let { p ->
                    val bmp = remember(p) { QrGen.qrBitmap(p) }
                    // 二维码尺寸不超过弹窗可用宽度（手机窄屏自动缩小，不溢出）
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val qrSize = 320.dp.coerceAtMost(maxWidth)
                        Image(bmp.asImageBitmap(), contentDescription = "配网二维码",
                            modifier = Modifier.size(qrSize))
                    }
                    Text(p, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun toast(ctx: Context, msg: String) {
    android.os.Handler(Looper.getMainLooper()).post {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
