package com.quick.app.ui.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.quick.app.QuickApp
import com.quick.app.config.ConfigBackup
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.DeviceConfig
import com.quick.app.net.LocalIp
import com.quick.app.qr.QrGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConfigScreen(openManage: () -> Unit, openDiag: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val db = app.db
    val controller = app.controller
    val scope = rememberCoroutineScope()

    // 表单本地状态（从 DB 载入一次）
    var cfg by remember { mutableStateOf<DeviceConfig?>(null) }
    LaunchedEffect(Unit) {
        cfg = db.configDao().getOnce()
    }

    // 配网二维码 dialog
    var showWifiQr by remember { mutableStateOf(false) }

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
                    toast(ctx, "导入完成：线别 +${r.importedLines}，机种 +${r.importedModels}，仪器配置已更新")
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ---- 仪器通信配置 ----
        val c = cfg
        if (c != null) {
            var ip by remember(c) { mutableStateOf(c.ip) }
            var port by remember(c) { mutableStateOf(c.port.toString()) }
            var unitId by remember(c) { mutableStateOf(c.unitId.toString()) }
            var poll by remember(c) { mutableStateOf(c.pollIntervalMs.toString()) }
            var timeout by remember(c) { mutableStateOf(c.timeoutMs.toString()) }
            var autoStart by remember(c) { mutableStateOf(c.autoStart) }

            Card(Modifier.fillMaxWidth()) {
                // 响应式：平板 IP/端口/UnitID 一行排开；手机窄屏 IP 独占一行，其余两两一行
                BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                    val wide = maxWidth >= 600.dp
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("仪器通信（MODBUS TCP/IP）", style = MaterialTheme.typography.titleMedium)
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
                    Text("默认端口 502、Unit ID 1、巡检约 1s。保存后对下一次连接生效；若正在采集请重启采集。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val newCfg = DeviceConfig(
                                id = 1, name = c.name,
                                ip = ip.trim(), port = port.trim().toIntOrNull() ?: 502,
                                unitId = unitId.trim().toIntOrNull() ?: 1,
                                pollIntervalMs = poll.trim().toLongOrNull() ?: 1000,
                                timeoutMs = timeout.trim().toLongOrNull() ?: 2000,
                                autoStart = autoStart
                            )
                            scope.launch(Dispatchers.IO) {
                                db.configDao().put(newCfg)
                                controller.reloadConfig()
                            }
                            toast(ctx, "配置已保存")
                        },
                        enabled = ip.isNotBlank()
                    ) { Text("保存通信配置") }
                }
            }
        }
        }

        // ---- 线别 / 机种管理入口 ----
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(14.dp)) {
                Text("线别 / 机种", style = MaterialTheme.typography.titleMedium)
                Text("管理线别与机种列表（新增/修改/删除/搜索）", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openManage) {
                    Icon(Icons.Default.Article, null, Modifier.padding(end = 6.dp)); Text("进入管理")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 4.dp))
                }
            }
        }

        // ---- 配置备份 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("配置备份（不含测量历史）", style = MaterialTheme.typography.titleMedium)
                Text("导出：线别/机种/仪器通信参数 → JSON 文件，可复制到另一台平板；导入：同名线别/机种自动跳过。",
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

        // ---- 配网二维码（191AF+ 首次联网用） ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("191AF+ 配网二维码", style = MaterialTheme.typography.titleMedium)
                Text("填写无线路由与仪器静态 IP，生成二维码给仪器扫（MB-TCP：ServerIP 是仪器自身 IP，非本机）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { showWifiQr = true }) {
                    Icon(Icons.Default.QrCode, null, Modifier.padding(end = 6.dp)); Text("生成配网二维码")
                }
            }
        }

        // ---- 通信诊断 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("通信诊断", style = MaterialTheme.typography.titleMedium)
                Text("帧级收发日志与寄存器读数，用于现场验证（对应测试 A~G）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openDiag) {
                    Icon(Icons.Default.BugReport, null, Modifier.padding(end = 6.dp)); Text("打开诊断页")
                }
            }
        }
    }

    if (showWifiQr) {
        WifiQrDialog(app, onDismiss = { showWifiQr = false })
    }
}

// ---------- 配网二维码 ----------

/** 上次输入的配网参数（记忆用，存 app_setting，仅本机） */
private const val KEY_WIFI_QR_LAST = "wifi.qr.last"

@Composable
private fun WifiQrDialog(app: QuickApp, onDismiss: () -> Unit) {
    val cfg by app.db.configDao().get().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var wifiName by remember { mutableStateOf("") }
    var wifiPwd by remember { mutableStateOf("") }
    var devIp by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("502") }
    var gateway by remember { mutableStateOf("") }

    var payload by remember { mutableStateOf<String?>(null) }
    var ifaces by remember { mutableStateOf(LocalIp.list()) }

    // 打开弹窗：读本机网络地址 + 回填上次输入的参数（不用每次重输）
    LaunchedEffect(Unit) {
        ifaces = LocalIp.list()
        val saved = runCatching { app.db.settingDao().get(KEY_WIFI_QR_LAST) }.getOrNull()
        if (!saved.isNullOrBlank()) {
            runCatching {
                val o = JSONObject(saved)
                wifiName = o.optString("wifiName", "")
                wifiPwd = o.optString("wifiPassword", "")
                devIp = o.optString("instrumentIp", "")
                port = o.optInt("port", 502).toString()
                gateway = o.optString("gateway", "")
            }
        }
    }
    // 记忆中没有仪器 IP 时，用「配置页当前仪器 IP」兜底（cfg 异步加载完成后回填）
    LaunchedEffect(cfg) {
        cfg?.let { if (devIp.isBlank()) devIp = it.ip }
    }

    /** 生成即记忆：下次打开自动回填 */
    fun rememberInputs() {
        val o = JSONObject()
            .put("wifiName", wifiName.trim())
            .put("wifiPassword", wifiPwd.trim())
            .put("instrumentIp", devIp.trim())
            .put("port", port.trim().toIntOrNull() ?: 502)
            .put("gateway", gateway.trim())
        scope.launch(Dispatchers.IO) {
            runCatching { app.db.settingDao().put(AppSetting(KEY_WIFI_QR_LAST, o.toString())) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("生成 191AF+ 配网二维码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                Text("输入内容会记住（仅保存在本机），下次打开自动回填。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        rememberInputs()
                        payload = QrGen.wifiConfigPayload(
                            wifiName.trim(), wifiPwd.trim(), devIp.trim(),
                            port.trim().toIntOrNull() ?: 502,
                            gateway.trim().ifEmpty { null }
                        )
                    },
                    enabled = wifiName.isNotBlank() && devIp.isNotBlank()
                ) { Text("生成二维码") }

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
