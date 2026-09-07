package com.quick.app.collect

import android.app.Application
import android.content.Intent
import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.DeviceConfig
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.net.DeviceSnapshot
import com.quick.app.net.FrameLog
import com.quick.app.net.ModbusTcpClient
import com.quick.app.net.Registers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 界面日志行（采集事件与通信帧，诊断页/首页展示） */
data class CommLine(val timeMs: Long, val text: String)

/** 连接状态 */
sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data class Connected(val ip: String, val port: Int) : ConnState
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ConnState
}

/** 测量页/全局展示状态 */
data class MeasureUiState(
    val running: Boolean = false,
    val conn: ConnState = ConnState.Disconnected,
    val snapshot: DeviceSnapshot? = null,          // 最新全读帧
    val lastRecord: MeasurementRecord? = null,     // 最近入库结果
    val lastError: String? = null,                 // 最近一次采集错误提示
    val pendingCount: Int = 0                      // 待补记(崩溃恢复)数量
)

/**
 * 采集控制器：唯一拥有轮询循环与状态机的组件（App 级单例，QuickApp 持有）。
 *
 * 数据可靠性设计（见 docs/可行性分析与通讯规格-V1.0.md §6/§7）：
 * 1. 每轮只发 1 个请求一次全读 31 寄存器 —— 0x1C 与 0x1D/0x1E 同帧快照；
 * 2. 收到 0x1C=1 立即把结果先写 pending 表（同库）再幂等插入正式表，最后清 pending；
 *    崩溃只可能发生在 [插库成功, 清 pending] 之间，重启后补记被幂等键吸收；
 * 3. 单协程串行执行，无并发读写仪器，天然防重入。
 */
class MeasurementController(private val app: Application, val db: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val client = ModbusTcpClient { onFrame(it) }

    private val _logs = MutableStateFlow<List<CommLine>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _ui = MutableStateFlow(MeasureUiState())
    val ui = _ui.asStateFlow()

    /** 新结果入库事件（弹卡/提示音用），带缓冲防丢 */
    private val _resultEvents = MutableSharedFlow<MeasurementRecord>(extraBufferCapacity = 32)
    val resultEvents = _resultEvents.asSharedFlow()

    val isRunning: Boolean get() = _ui.value.running

    /** 当前线别/机种（测量页选中值，持久化） */
    val selectedLine: String?
        get() = _line
    val selectedModel: String?
        get() = _model

    @Volatile private var _line: String? = null
    @Volatile private var _model: String? = null
    @Volatile private var cfgCache: DeviceConfig? = null

    suspend fun init() {
        _line = db.settingDao().get(KEY_LINE)
        _model = db.settingDao().get(KEY_MODEL)
        cfgCache = db.configDao().getOnce()
        if (cfgCache == null) {
            cfgCache = DeviceConfig()
            db.configDao().put(cfgCache!!)
        }
        scope.launch { recoverPending() }
        if (cfgCache?.autoStart == true) setRunning(true)
    }

    /** 用户开关：开 → 前台服务保活 + 轮询；关 → 停 */
    fun setRunning(on: Boolean) {
        if (on == isRunning) return
        if (on) {
            postLog("▶ 采集已开启")
            runCatching {
                app.startForegroundService(Intent(app, CollectionService::class.java))
            }
            _ui.update { it.copy(running = true) }
            if (job == null) job = scope.launch { runLoop() }
        } else {
            postLog("■ 采集已停止")
            job?.cancel()
            job = null
            runCatching { app.stopService(Intent(app, CollectionService::class.java)) }
            client.disconnect()
            _ui.update {
                it.copy(running = false, conn = ConnState.Disconnected, snapshot = null)
            }
        }
    }

    suspend fun selectLine(name: String?) {
        _line = name
        db.settingDao().put(AppSetting(KEY_LINE, name ?: ""))
    }

    suspend fun selectModel(name: String?) {
        _model = name
        db.settingDao().put(AppSetting(KEY_MODEL, name ?: ""))
    }

    /** 配置页保存后刷新缓存 */
    suspend fun reloadConfig() {
        cfgCache = db.configDao().getOnce()
    }

    /** 诊断页手动探测：采集停止时执行一次全读并解码（结果直接进事件日志） */
    suspend fun manualProbe() {
        if (isRunning) {
            postLog("! 采集运行中，请先停止采集再手动测试")
            return
        }
        val cfg = cfgCache ?: db.configDao().getOnce() ?: DeviceConfig()
        if (cfg.ip.isBlank()) {
            postLog("! 尚未配置仪器 IP")
            return
        }
        postLog("PROBE 手动全读 ${cfg.ip}:${cfg.port} (Unit ${cfg.unitId})…")
        try {
            client.connect(cfg.ip, cfg.port, cfg.timeoutMs.toInt(), cfg.timeoutMs.toInt())
            val regs = client.readHoldingRegisters(0, Registers.TOTAL, cfg.unitId)
            client.disconnect()
            val snap = DeviceSnapshot.parse(regs)
            postLog("PROBE 解码: 实时温度=${snap.liveTempC?.let { "%.1f".format(it) } ?: "--"}℃ " +
                "单位=${if (snap.unit == 1) "℉" else "℃"} 自动保存=${snap.saveMode} 关机=${snap.autoOffMin}min " +
                "SN=${snap.deviceSn} 设定=${snap.setTemp} 误差=${snap.tolerance}")
            postLog("PROBE 0x1C=${if (snap.hasResult) 1 else 0} 0x1D=${snap.savedTemp} 0x1E=${if (snap.judgedOk == true) 1 else if (snap.judgedOk == false) 0 else -1}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            postLog("PROBE 失败: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun runLoop() {
        var attempt = 0
        var backoff = 1000L
        while (scope.isActive && isRunning) {
            val cfg = cfgCache ?: db.configDao().getOnce() ?: DeviceConfig()
            if (cfg.ip.isBlank()) {
                postLog("尚未配置仪器 IP，等待中…（请到「配置」页填写）")
                _ui.update { it.copy(conn = ConnState.Disconnected) }
                delay(3000)
                continue
            }
            try {
                _ui.update { it.copy(conn = ConnState.Connecting) }
                client.connect(cfg.ip, cfg.port, cfg.timeoutMs.toInt(), cfg.timeoutMs.toInt())
                attempt = 0
                _ui.update { it.copy(conn = ConnState.Connected(cfg.ip, cfg.port)) }
                postLog("✔ 已连接 ${cfg.ip}:${cfg.port}，开始轮询（周期 ${cfg.pollIntervalMs}ms）")
                // ---- 轮询内循环 ----
                while (scope.isActive && isRunning && client.isConnected) {
                    val regs = client.readHoldingRegisters(0, Registers.TOTAL, cfg.unitId)
                    val snap = DeviceSnapshot.parse(regs)
                    _ui.update {
                        it.copy(snapshot = snap, conn = ConnState.Connected(cfg.ip, cfg.port), lastError = null)
                    }
                    if (snap.hasResult) {
                        storeResult(snap, cfg)
                    }
                    delay(cfg.pollIntervalMs)
                }
                if (scope.isActive && isRunning) {
                    postLog("连接中断，准备重连")
                    client.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                backoff = minOf(30_000L, 1000L shl minOf(attempt - 1, 5))
                client.disconnect()
                _ui.update {
                    it.copy(
                        conn = ConnState.Reconnecting(attempt, backoff),
                        lastError = e.message
                    )
                }
                postLog("⚠ 连接失败(${attempt}次): ${e.message ?: e.javaClass.simpleName}，${backoff}ms 后重试")
                if (scope.isActive && isRunning) delay(backoff)
            }
        }
    }

    /**
     * 处理一笔结果：pending 先落盘 → 幂等入库 → 清 pending → 广播 UI。
     * 失败时 pending 保留在盘上，下次启动 recoverPending() 补记（幂等键防重）。
     */
    private suspend fun storeResult(snap: DeviceSnapshot, cfg: DeviceConfig) {
        val now = System.currentTimeMillis()
        val ok = snap.judgedOk == true
        val rec = MeasurementRecord(
            snapshotKey = "$now-${snap.savedTemp}-${ok}-${_line.orEmpty()}-${_model.orEmpty()}",
            timestampMs = now,
            lineName = _line ?: "",
            modelName = _model ?: "",
            deviceSn = snap.deviceSn,
            deviceIp = cfg.ip,
            setTemp = snap.setTemp,
            measuredTemp = snap.savedTemp,
            tolerance = snap.tolerance,
            result = if (ok) "OK" else "NG"
        )
        val pendingKey = PENDING_PREFIX + rec.snapshotKey
        val json = rec.toJson().toString()
        try {
            db.settingDao().put(AppSetting(pendingKey, json))
            val id = db.recordDao().insert(rec)
            db.settingDao().delete(pendingKey)
            if (id == -1L) {
                postLog("结果重复，已跳过：${rec.measuredTemp}℃ ${rec.result}（${timeText(now)}）")
            } else {
                postLog("★ 结果已保存：${rec.measuredTemp}℃ ${rec.result}（${_line} / ${_model}）")
                _ui.update { it.copy(lastRecord = rec) }
                _resultEvents.tryEmit(rec)
            }
        } catch (e: Exception) {
            // DB 异常：pending 保留在盘上，重启后自动补记；当场重试一次
            postLog("!! 入库失败(${e.message ?: e.javaClass.simpleName})，结果暂存待恢复，不静默丢弃")
            _ui.update { it.copy(lastError = "入库失败，结果已暂存待恢复") }
            try {
                val retry = db.recordDao().insert(rec)
                db.settingDao().delete(pendingKey)
                if (retry != -1L) {
                    _ui.update { it.copy(lastRecord = rec, lastError = null) }
                    _resultEvents.tryEmit(rec)
                    postLog("✔ 重试入库成功")
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 崩溃/被杀恢复：把遗留 pending 结果补记回正式表（幂等键吸收重复） */
    private suspend fun recoverPending() {
        val pendings = db.settingDao().all().filter { it.key.startsWith(PENDING_PREFIX) }
        if (pendings.isEmpty()) return
        var n = 0
        for (p in pendings) {
            try {
                val rec = MeasurementRecord.fromJson(JSONObject(p.value)) ?: continue
                val id = db.recordDao().insert(rec)
                db.settingDao().delete(p.key)
                if (id != -1L) n++
            } catch (_: Exception) {
            }
        }
        if (n > 0) {
            postLog("恢复补记 $n 笔先前未保存的结果")
            _ui.update { it.copy(pendingCount = 0) }
        }
    }

    // ---------- 日志 ----------

    private fun onFrame(f: FrameLog) {
        postLog("${f.dir}${if (f.hex.isNotEmpty()) "  ${f.hex}" else ""}${if (f.note.isNotEmpty()) "  ${f.note}" else ""}")
    }

    fun postLog(text: String) {
        _logs.update { list ->
            val nl = list + CommLine(System.currentTimeMillis(), text)
            if (nl.size > MAX_LOG) nl.takeLast(MAX_LOG) else nl
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    companion object {
        const val KEY_LINE = "sel.line"
        const val KEY_MODEL = "sel.model"
        const val PENDING_PREFIX = "pending:"
        const val MAX_LOG = 500

        fun timeText(ms: Long): String =
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ms))
    }

    fun destroy() {
        job?.cancel()
        scope.cancel()
    }
}

// ---- JSON 序列化（零依赖，org.json 系统自带）----

fun MeasurementRecord.toJson(): JSONObject = JSONObject().apply {
    put("snapshotKey", snapshotKey)
    put("timestampMs", timestampMs)
    put("lineName", lineName)
    put("modelName", modelName)
    put("deviceSn", deviceSn ?: JSONObject.NULL)
    put("deviceIp", deviceIp)
    put("setTemp", setTemp ?: JSONObject.NULL)
    put("measuredTemp", measuredTemp ?: JSONObject.NULL)
    put("tolerance", tolerance ?: JSONObject.NULL)
    put("result", result)
}

fun MeasurementRecord.Companion.fromJson(o: JSONObject): MeasurementRecord? = try {
    MeasurementRecord(
        snapshotKey = o.getString("snapshotKey"),
        timestampMs = o.getLong("timestampMs"),
        lineName = o.optString("lineName"),
        modelName = o.optString("modelName"),
        deviceSn = if (o.isNull("deviceSn")) null else o.optString("deviceSn"),
        deviceIp = o.optString("deviceIp"),
        setTemp = if (o.isNull("setTemp")) null else o.optInt("setTemp"),
        measuredTemp = if (o.isNull("measuredTemp")) null else o.optInt("measuredTemp"),
        tolerance = if (o.isNull("tolerance")) null else o.optInt("tolerance"),
        result = o.optString("result", "NG")
    )
} catch (_: Exception) {
    null
}
