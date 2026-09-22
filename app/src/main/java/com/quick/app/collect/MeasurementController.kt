package com.quick.app.collect

import android.app.Application
import android.content.Intent
import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.DeviceConfig
import com.quick.app.data.db.DeviceSn
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.data.db.SN_LEARNED
import com.quick.app.data.db.SN_MANUAL
import com.quick.app.net.DeviceSnapshot
import com.quick.app.net.FrameLog
import com.quick.app.net.ModbusException
import com.quick.app.net.ModbusTcpClient
import com.quick.app.net.ModbusTimeoutException
import com.quick.app.net.Registers
import com.quick.app.net.channelText
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
import java.io.EOFException
import java.net.SocketException

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
    val pendingCount: Int = 0,                     // 待补记(崩溃恢复)数量
    // 断线原因计数（自本次启动累计，诊断页展示）：回答「代码问题还是设备/网络问题」的原始证据
    val dropStats: Map<String, Int> = emptyMap(),
    // 当前选择（控制器是唯一真源 → 保存后自动清空设备编号，界面跟着变，不会各存一份）
    val selLine: String? = null,
    val selModel: String? = null,
    val selStation: String? = null,
    val selTempPreset: String? = null,
    val selVmax: String? = null,                   // 漏电压上限（"2 mV"），与温度设置一样保留到下次
    val selRmax: String? = null,                   // 接地电阻上限（"2 Ω"）
    val selDeviceSn: String? = null
)

/**
 * 采集控制器：唯一拥有轮询循环与状态机的组件（App 级单例，QuickApp 持有）。
 *
 * 数据可靠性设计（见 docs/可行性分析与通讯规格-V1.0.md §6/§7）：
 * 1. 每轮只发 1 个请求一次全读 0x00~0x23（36 个寄存器）—— 触发标志(0x1F)、
 *    保存温度(0x20)、保存电压(0x21)、保存电阻(0x22)、判定(0x23) 以及判据上限(0x1D/0x1E)
 *    全部取自同一响应帧，保证「一笔结果」内部自洽；
 * 2. 0x1F=1（手册：读取后自动清零）视为一次新保存，立即把结果先写 pending 表（同库）
 *    再幂等插入正式表，最后清 pending；崩溃只可能发生在 [插库成功, 清 pending] 之间，
 *    重启后补记被幂等键吸收；
 * 3. 仪器若无 0x1F~0x23（老固件），全读会回异常码 02 → 自动降级读 0x00~0x1E 并告警；
 * 4. 单协程串行执行，无并发读写仪器，天然防重入。
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

    /** 当前线别/机种/站别/温度设置/上限设置/设备编号（测量页选中值，持久化） */
    val selectedLine: String?
        get() = _line
    val selectedModel: String?
        get() = _model
    val selectedStation: String?
        get() = _station
    val selectedTempPreset: String?
        get() = _tempPreset
    val selectedDeviceSn: String?
        get() = _deviceSn

    @Volatile private var _line: String? = null
    @Volatile private var _model: String? = null
    @Volatile private var _station: String? = null

    /** 选中的温度设置名（"350±20"）—— 与机种一样**保留**到下次（用户 2026-09 定） */
    @Volatile private var _tempPreset: String? = null

    /** 选中的上限设置名（"2 mV" / "2 Ω"）—— 同温度设置，保留到下次 */
    @Volatile private var _vmax: String? = null
    @Volatile private var _rmax: String? = null

    /** 手选的设备编号 —— **每保存一笔后清空**（用户 2026-09 定，避免下一笔挂错编号） */
    @Volatile private var _deviceSn: String? = null
    @Volatile private var cfgCache: DeviceConfig? = null

    /**
     * 弹卡截止时刻（0 = 未暂停）。弹卡显示期间**照常读仪器，但一帧都不判定、不记录**
     * （用户 2026-09-21 定：卡显示完才能记下一组数据）。
     *
     * 为什么不是"完全不发请求"：那会留出 5 秒静默期。实测反馈「本次更新后断连更频繁」，
     * 而本次唯一新增的静默时段就是它 —— 若仪器/AP 的空闲回收短于 5 秒，就会每保存一笔被踢一次。
     * 改成"继续读但不记录"后，数据行为与完全暂停完全一致（卡收起后第一帧重建基线，
     * 暂停期间的变化一律不算），而链路始终是热的。
     *
     * 上限 [HOLD_MAX_MS]：万一弹卡没能正常收起（界面被系统杀掉等），也不会永久不判定。
     */
    @Volatile private var holdUntilMs = 0L

    /**
     * 触发状态机（标志 0x1F + 结果签名）—— 判定「哪一帧算一笔新结果」全部交给它，
     * 逻辑与判据见 [SaveTrigger]；只在轮询协程里用，无并发。
     *
     * 实测（2026-09-14）：标志置 1 后约 **1 s** 自动清零，因此轮询周期取 0.9 s 抢在窗口内。
     */
    private val trigger = SaveTrigger(RECOVER_MISSED_SAVE)

    /** 「仪器没有结果块、一笔都不会入库」只告警一次，不刷屏 */
    @Volatile private var warnedDegraded = false

    suspend fun init() {
        _line = db.settingDao().get(KEY_LINE)
        _model = db.settingDao().get(KEY_MODEL)
        _station = db.settingDao().get(KEY_STATION)
        _tempPreset = db.settingDao().get(KEY_TEMP_PRESET)
        _vmax = db.settingDao().get(KEY_VMAX)
        _rmax = db.settingDao().get(KEY_RMAX)
        _deviceSn = db.settingDao().get(KEY_DEVICE_SN)
        _ui.update {
            it.copy(
                selLine = _line, selModel = _model, selStation = _station,
                selTempPreset = _tempPreset, selVmax = _vmax, selRmax = _rmax,
                selDeviceSn = _deviceSn
            )
        }
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
        resumePolling()
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
        _ui.update { it.copy(selLine = name) }
        db.settingDao().put(AppSetting(KEY_LINE, name ?: ""))
    }

    suspend fun selectModel(name: String?) {
        _model = name
        _ui.update { it.copy(selModel = name) }
        db.settingDao().put(AppSetting(KEY_MODEL, name ?: ""))
    }

    suspend fun selectStation(name: String?) {
        _station = name
        _ui.update { it.copy(selStation = name) }
        db.settingDao().put(AppSetting(KEY_STATION, name ?: ""))
    }

    /** 选温度设置（"350±20"）—— 二维码的 TSET/TMIN/TMAX 来源，与机种一样保留到下次 */
    suspend fun selectTempPreset(name: String?) {
        _tempPreset = name
        _ui.update { it.copy(selTempPreset = name) }
        db.settingDao().put(AppSetting(KEY_TEMP_PRESET, name ?: ""))
    }

    /** 选漏电压上限（"2 mV"）—— 二维码的 VMAX 来源，保留到下次 */
    suspend fun selectVmax(name: String?) {
        _vmax = name
        _ui.update { it.copy(selVmax = name) }
        db.settingDao().put(AppSetting(KEY_VMAX, name ?: ""))
    }

    /** 选接地电阻上限（"2 Ω"）—— 二维码的 RMAX 来源，保留到下次 */
    suspend fun selectRmax(name: String?) {
        _rmax = name
        _ui.update { it.copy(selRmax = name) }
        db.settingDao().put(AppSetting(KEY_RMAX, name ?: ""))
    }

    /** 选设备编号 —— 二维码的 SN 来源；保存一笔后由 [clearDeviceSn] 清空 */
    suspend fun selectDeviceSn(name: String?) {
        _deviceSn = name
        _ui.update { it.copy(selDeviceSn = name) }
        db.settingDao().put(AppSetting(KEY_DEVICE_SN, name ?: ""))
    }

    /** 保存完一笔后清空设备编号选择（用户 2026-09 定：避免下一笔挂在同一个编号上） */
    suspend fun clearDeviceSn() {
        if (_deviceSn == null) return
        selectDeviceSn(null)
    }

    /**
     * 弹卡期间**停止判定与记录**（用户要求：全屏显示结束才记下一组）。
     * 轮询照常进行（链路保温），只是这几帧不判定；卡收起后第一帧重建基线。
     * 由界面在弹卡出现时调用；提前关掉弹卡请调 [resumePolling]。
     */
    fun hold(ms: Long = HOLD_MS) {
        holdUntilMs = System.currentTimeMillis() + ms.coerceIn(0L, HOLD_MAX_MS)
    }

    /** 弹卡提前结束 / 停止采集：立即恢复判定 */
    fun resumePolling() {
        holdUntilMs = 0L
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
            var len = Registers.TOTAL
            val regs = try {
                client.readHoldingRegisters(0, len, cfg.unitId)
            } catch (e: ModbusException) {
                if (e.code == 2 && len > Registers.LEGACY_TOTAL) {
                    len = Registers.LEGACY_TOTAL
                    postLog("! 仪器拒绝 0x1F~0x23（异常码 02）→ 降级读 0x00~0x1E（结果块不可用）")
                    client.readHoldingRegisters(0, len, cfg.unitId)
                } else throw e
            }
            client.disconnect()
            val snap = DeviceSnapshot.parse(regs)
            postLog("PROBE 读到 ${regs.size} 个寄存器  实时温度=${f1(snap.liveTempC)}℃  单位=${if (snap.unit == 1) "℉" else "℃"}")
            postLog("PROBE 实时电压(0x02)=${f1(snap.liveVoltageMv)}mV  实时电阻(0x03)=${f1(snap.liveResistanceOhm)}Ω  " +
                "测量通道(0x04)=${snap.channel}（${channelText(snap.channel)}）")
            postLog("PROBE 设备信息(0x0A~0x19)=${snap.deviceInfo ?: "--"}")
            postLog("PROBE 设定温度(0x1A)=${snap.targetTemp}  温度判断(0x1B~0x1C)=${snap.tempLow}~${snap.tempHigh}  " +
                "电压上限(0x1D)=${f1(snap.voltageLimitMv)}mV  电阻上限(0x1E)=${f1(snap.resistanceLimitOhm)}Ω")
            if (snap.hasResultBlock) {
                postLog("PROBE ★结果块 上传标志(0x1F)=${snap.uploadFlag}  保存温度(0x20)=${snap.savedTempC}  " +
                    "保存电压(0x21)=${f1(snap.savedVoltageMv)}mV  保存电阻(0x22)=${f1(snap.savedResistanceOhm)}Ω  " +
                    "判定(0x23)=${snap.judge}（${snap.resultText}）")
                postLog("PROBE 提示：在仪器上按一次「保存」后马上再点「手动测试」，即可看到 0x1F=1 与 0x20~0x23 的整帧结果")
            } else {
                postLog("PROBE ⚠ 未读到结果块 0x1F~0x23 —— 请核对仪器固件与手册地址分配表（已降级为实时值兜底）")
            }
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
            // 本次连接的现场证据：连了多久、成功读了多少帧 —— 判断「代码问题还是设备/网络问题」靠它
            var connectedAtMs = 0L
            var okReads = 0L
            try {
                _ui.update { it.copy(conn = ConnState.Connecting) }
                client.connect(cfg.ip, cfg.port, cfg.timeoutMs.toInt(), cfg.timeoutMs.toInt())
                attempt = 0
                connectedAtMs = System.currentTimeMillis()
                holdUntilMs = 0L                     // 新连接不受上一次弹卡暂停影响
                _ui.update { it.copy(conn = ConnState.Connected(cfg.ip, cfg.port)) }
                postLog("✔ 已连接 ${cfg.ip}:${cfg.port}，开始轮询（周期 ${cfg.pollIntervalMs}ms）")
                // 读长度：优先完整表 0x00~0x23；仪器若无结果块会回异常码 02 → 降级到 0x00~0x1E 并告警
                var readLen = Registers.TOTAL
                var protoErrors = 0                  // 连续异常应答计数（协议级，链路本身没问题）
                var needRebaseline = false           // 弹卡结束后，第一帧只重建基线
                // ---- 轮询内循环 ----
                while (scope.isActive && isRunning && client.isConnected) {
                    // 弹卡期间：**照常读，但不判定、不记录**（用户 2026-09-21 定）。
                    // 原来是"完全不发请求"，但那会留出 5 秒静默期 —— 若仪器/AP 的空闲回收短于 5 秒，
                    // 就会变成「每保存一笔就被踢一次连接」，现场看到的就是"断连变频繁"。
                    // 数据行为与完全暂停**一模一样**：暂停期间的变化不判定，卡收起后第一帧重建基线。
                    val paused = holdUntilMs > System.currentTimeMillis()
                    if (paused) needRebaseline = true
                    val cycleStart = System.currentTimeMillis()
                    val regs = try {
                        client.readHoldingRegisters(0, readLen, cfg.unitId)
                    } catch (e: ModbusException) {
                        if (e.code == 2 && readLen > Registers.LEGACY_TOTAL) {
                            readLen = Registers.LEGACY_TOTAL
                            trigger.reset()              // 换长度后重建基线
                            postLog("⚠ 仪器拒绝 0x1F~0x23（异常码 02）→ 降级读 0x00~0x1E：" +
                                "读不到判定(0x23) → 无法确认是否合格，故一律不落库 —— 请核对仪器固件")
                            client.readHoldingRegisters(0, readLen, cfg.unitId)
                        } else {
                            // 协议级异常应答 = 仪器听懂了但拒绝这一条：**TCP 链路是好的，不断线**。
                            // （原实现任何异常都断线重连，一次偶发异常就让现场看到「断连」）
                            protoErrors++
                            if (protoErrors >= MAX_PROTOCOL_ERRORS) throw e
                            postLog("⚠ 仪器异常应答 code=${e.code}（连续 $protoErrors 次）：TCP 连接保持，继续读取")
                            // 按正常周期重试（不是 500ms 抢跑）：异常应答说明仪器活着但拒绝了这一条，
                            // 抢跑只会让它更忙；周期保持一致也便于现场看日志节奏
                            delay(nextDelay(cycleStart, cfg.pollIntervalMs))
                            continue
                        }
                    }
                    protoErrors = 0
                    okReads++
                    val snap = DeviceSnapshot.parse(regs)
                    _ui.update {
                        it.copy(snapshot = snap, conn = ConnState.Connected(cfg.ip, cfg.port), lastError = null)
                    }
                    if (paused) {
                        // 弹卡还在显示：这一帧只用来保温链路，绝不判定（用户要求"显示完再记下一组"）
                        delay(nextDelay(cycleStart, cfg.pollIntervalMs))
                        continue
                    }
                    if (needRebaseline) {
                        // 弹卡结束后的第一帧：只重建基线，不判定也不补收 ——
                        // 卡显示期间仪器上的任何变化都不该被当成本笔结果（正是「多保存一笔」的来源）
                        needRebaseline = false
                        trigger.rebaseline(snap.triggerRaw, snap.contentSignature)
                        delay(nextDelay(cycleStart, cfg.pollIntervalMs))
                        continue
                    }
                    // 触发判定（结果块各字段与判据上限取同一帧，协议层原子）：交给 SaveTrigger
                    val decision = trigger.next(
                        flag = snap.triggerRaw,
                        sig = snap.contentSignature,
                        hasResultBlock = snap.hasResultBlock,
                        judgeValid = (snap.judge ?: -1) in 0..2
                    )
                    when (decision) {
                        // 只保留 OK（用户 2026-09 要求）：NG / 无效不入库也不弹卡，只在日志留痕。
                        // 签名已由 SaveTrigger 推进 —— 否则同一笔会在下一帧再被判成新结果。
                        SaveTrigger.Decision.NEW_FLAG,
                        SaveTrigger.Decision.RECOVERED -> {
                            if (snap.judge == 1) {
                                storeResult(snap, cfg, recovered = decision == SaveTrigger.Decision.RECOVERED)
                            } else {
                                skipResult(snap)
                            }
                        }
                        SaveTrigger.Decision.BASELINE, SaveTrigger.Decision.NONE -> Unit
                    }
                    delay(nextDelay(cycleStart, cfg.pollIntervalMs))
                }
                if (scope.isActive && isRunning) {
                    postLog("⚠ 连接中断（${connSpan(connectedAtMs)}，成功读取 $okReads 帧）→ 准备重连")
                    client.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                backoff = minOf(30_000L, 1000L shl minOf(attempt - 1, 5))
                client.disconnect()
                countDrop(e)
                _ui.update {
                    it.copy(
                        conn = ConnState.Reconnecting(attempt, backoff),
                        lastError = e.message
                    )
                }
                postLog("⚠ ${errKind(e)}（${connSpan(connectedAtMs)}，成功读取 $okReads 帧）：" +
                    "${e.message ?: e.javaClass.simpleName} → ${backoff}ms 后重连（第 $attempt 次）")
                if (scope.isActive && isRunning) delay(backoff)
            }
        }
    }

    /**
     * 固定周期：本轮读+入库耗掉多少就补多少，使**周期恒等于** pollIntervalMs。
     * 原写法是「读 → delay(周期)」，实际周期 = 读取耗时 + 周期，常常越过 1 s 标志窗口
     * （这正是补收通道频繁启用、进而多记一笔的根源）。
     *
     * ⚠️ **下限 [MIN_CYCLE_GAP_MS] 是必须的**（2026-09-21 补）：若某一轮耗时已经超过周期
     * （Wi-Fi 抖动 / 仪器慢 / 入库慢），这里会算出 0 甚至负数 —— 于是**背靠背连发请求，
     * 一点喘息都不给**。旧写法无论多慢都至少留一个完整周期（900ms）的静默，
     * 所以固定周期一旦没有下限，就可能在链路已经吃力时把它彻底压垮（表现为「断连更频繁」）。
     */
    private fun nextDelay(cycleStartMs: Long, intervalMs: Long): Long =
        (intervalMs - (System.currentTimeMillis() - cycleStartMs)).coerceAtLeast(MIN_CYCLE_GAP_MS)

    /** 断线原因分类 —— 现场日志要能一眼看出是「仪器没应答」「被断开」还是「网络断了」 */
    private fun errKind(e: Exception): String = when (e) {
        is ModbusTimeoutException -> "仪器无响应（读超时）"
        is ModbusException -> "仪器异常应答 code=${e.code}"
        is EOFException -> "对端断开连接（仪器/AP 主动关闭）"
        is SocketException -> "网络中断（Socket 异常）"
        else -> "通信失败"
    }

    /**
     * 断线原因归类的**稳定标签**（计数用，不含 code 等变量部分）。
     *
     * 目的：回答用户 2026-09-21 的问题「是代码问题还是设备/网络问题」——
     * 帧日志只有 500 行（约 4 分钟）且会被收发帧刷掉，一眼看不出频次；
     * 计数是**跨整个运行期累加**的，跑一天就能看出来：
     * 「读超时」为主 = 链路/仪器侧；「对端关闭」为主且每段连接都很短 = 被主动踢；
     * 「仪器异常应答」为主 = 协议/寄存器层面的问题（那才可能是代码）。
     */
    private fun dropKey(e: Exception): String = when (e) {
        is ModbusTimeoutException -> "读超时（仪器未应答）"
        is ModbusException -> "仪器异常应答"
        is EOFException -> "对端关闭连接"
        is SocketException -> "网络中断（Socket 异常）"
        else -> "其它通信失败"
    }

    private fun countDrop(e: Exception) {
        val key = dropKey(e)
        _ui.update { it.copy(dropStats = it.dropStats + (key to (it.dropStats[key] ?: 0) + 1)) }
    }

    /** 本次连接维持了多久 —— 偶发断连排查时，「连了 5 秒就断」和「连了 2 小时才断」是两回事 */
    private fun connSpan(fromMs: Long): String {
        if (fromMs == 0L) return "未建立连接"
        val s = (System.currentTimeMillis() - fromMs) / 1000
        return when {
            s < 60 -> "已连接 $s 秒"
            s < 3600 -> "已连接 ${s / 60} 分 ${s % 60} 秒"
            else -> "已连接 ${s / 3600} 小时 ${(s % 3600) / 60} 分"
        }
    }

    /**
     * NG / 无效的结果：**不入库、不弹卡**（用户 2026-09 要求「只保留 OK」），
     * 但仍在诊断日志留一行 —— 现场能查到仪器确实出过这一笔，不静默。
     */
    private fun skipResult(snap: DeviceSnapshot) {
        if (!snap.hasResultBlock) {
            // 降级模式读不到判定(0x23)，无法确认是否合格 —— 宁可不下结论，也不编造一条 OK
            if (!warnedDegraded) {
                warnedDegraded = true
                postLog("⚠ 仪器未提供结果块(0x1F~0x23)：读不到判定 → 一笔都不会入库。" +
                    "请核对仪器固件版本 / 通讯协议；实时值仍在界面上正常显示")
            }
            return
        }
        val tempText = snap.measuredTempC
            ?.let { String.format(java.util.Locale.US, "%.1f℃", it) } ?: "--"
        postLog("○ ${snap.resultText} 不入库：$tempText（${seatText()}）")
    }

    /**
     * 处理一笔结果：pending 先落盘 → 幂等入库 → 清 pending → 广播 UI。
     * 失败时 pending 保留在盘上，下次启动 recoverPending() 补记（幂等键防重）。
     *
     * **只在仪器判定 0x23 = 1（OK）时被调用**；NG / 无效走 [skipResult]。
     *
     * @param recovered true = 标志(0x1F)已回 0、靠结果块签名变化补收（本轮读晚了，见 RECOVER_MISSED_SAVE）；
     *                  记录本身仍全部取自仪器，只是日志会明确标注，便于现场辨别
     */
    private suspend fun storeResult(snap: DeviceSnapshot, cfg: DeviceConfig, recovered: Boolean = false) {
        val now = System.currentTimeMillis()
        // 判定：完整表用 0x23（1=OK 0=NG 2=无效）。
        // 调用点已保证 judge==1 且结果块存在，App 不参与判定、也不猜（降级模式的推测判定已移除）。
        val result = snap.resultText
        // 测量温度：仪器定格的 0x20
        val temp = snap.measuredTempC
        // 设备编号：**以仪器扫码写入的 0x0A~0x19 为准**，仪器里是空白的才用手选的那个
        // （用户 2026-09 定：编号常常是在仪器上扫进去的，App 侧的选择只作兜底）
        val snFromDevice = snap.deviceInfo?.trim()?.ifBlank { null }
        val sn = snFromDevice ?: _deviceSn?.trim()?.ifBlank { null }
        val rec = MeasurementRecord(
            snapshotKey = "$now-${temp}-${result}-${_line.orEmpty()}-${_model.orEmpty()}-${_station.orEmpty()}",
            timestampMs = now,
            lineName = _line ?: "",
            modelName = _model ?: "",
            stationName = _station ?: "",
            deviceInfo = sn,
            deviceIp = cfg.ip,
            channel = snap.channel,
            targetTemp = snap.targetTemp,
            tempLow = snap.tempLow,
            tempHigh = snap.tempHigh,
            voltageLimitMv = snap.voltageLimitMv,
            resistanceLimitOhm = snap.resistanceLimitOhm,
            measuredTemp = temp,
            measuredVoltageMv = snap.savedVoltageMv,
            measuredResistanceOhm = snap.savedResistanceOhm,
            result = result
        )
        val tempText = temp?.let { String.format(java.util.Locale.US, "%.1f℃", it) } ?: "--"
        val pendingKey = PENDING_PREFIX + rec.snapshotKey
        val json = rec.toJson().toString()
        try {
            db.settingDao().put(AppSetting(pendingKey, json))
            val id = db.recordDao().insert(rec)
            db.settingDao().delete(pendingKey)
            if (id == -1L) {
                postLog("结果重复，已跳过：$tempText ${rec.result}（${timeText(now)}）")
            } else if (recovered) {
                postLog("★ 结果已保存（补收）：$tempText ${rec.result}（${seatText()}）" +
                    " —— 本轮读到时标志已自清（实测保持约 1 s），已按结果块补记；" +
                    "若频繁出现请把轮询周期调小")
                _ui.update { it.copy(lastRecord = rec) }
            } else {
                postLog("★ 结果已保存：$tempText ${rec.result}（${seatText()}）")
                _ui.update { it.copy(lastRecord = rec) }
                _resultEvents.tryEmit(rec)
            }
            if (id != -1L) {
                learnDeviceSn(sn, fromInstrument = snFromDevice != null)
                clearDeviceSn()   // 用户 2026-09 定：保存完清空设备编号选择
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

    /**
     * 设备编号自动学习（用户 2026-09 要求）：保存数据时把本次用到的编号记进「设备编号」列表，
     * 于是下次可以直接在测量页选中它、跟温度一起生成配置二维码 —— 不用再单独给仪器配一次编号。
     *
     * 编号常常是**在仪器上扫码输入的**，所以这里以仪器读到的 0x0A~0x19 为主；手选的编号同样入库。
     * 失败不影响已保存的记录（本方法只做列表补充）。
     */
    private suspend fun learnDeviceSn(sn: String?, fromInstrument: Boolean) {
        val name = sn?.trim().orEmpty()
        if (name.isEmpty()) return
        runCatching {
            if (db.deviceSnDao().findByName(name) == null) {
                db.deviceSnDao().insert(
                    DeviceSn(name = name, source = if (fromInstrument) SN_LEARNED else SN_MANUAL)
                )
                postLog("＋ 设备编号「$name」已加入列表（${if (fromInstrument) "仪器扫码写入" else "测量页选择"}）")
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
        const val KEY_STATION = "sel.station"
        const val KEY_TEMP_PRESET = "sel.tempPreset"
        const val KEY_VMAX = "sel.vmax"
        const val KEY_RMAX = "sel.rmax"
        const val KEY_DEVICE_SN = "sel.deviceSn"
        const val PENDING_PREFIX = "pending:"

        /**
         * 帧日志保留行数。收发帧每周期各 1 行 → 500 行只有约 4 分钟历史，断线原因会被刷掉；
         * 2000 行 ≈ 15 分钟，内存约几百 KB（断线频次另由 [MeasureUiState.dropStats] 跨期统计）。
         */
        const val MAX_LOG = 2000

        /**
         * 固定周期调度的**下限间隔**：本轮耗时超过周期时，至少也要给链路这么久静默。
         * 没有它就会出现「0ms 间隔连发请求」——在链路已经吃力时进一步压垮它。
         */
        const val MIN_CYCLE_GAP_MS = 200L

        /**
         * 弹卡显示时长。这段时间内**照常读仪器但不判定、不记录**（用户 2026-09-21 定：
         * 卡没消失前绝不记下一笔；同时不让链路出现静默期）。
         */
        const val HOLD_MS = 5000L

        /**
         * 暂停上限。仪器协议文档第 3 页：「上位机发送数据间隔不要超过 60 秒，否则测温仪认为通讯超时」，
         * 所以暂停必须远小于 60 s；这里再留一道保险，防界面异常导致轮询永久停摆。
         */
        const val HOLD_MAX_MS = 15_000L

        /** 连续多少次协议级异常应答才认定链路也坏了（否则保持 TCP 连接，不断线） */
        const val MAX_PROTOCOL_ERRORS = 5

        /**
         * 补收通道：标志（0x1F）已回 0，但结果块内容（0x20~0x23）变了 —— 说明这一笔是
         * 「按了保存，但本轮读晚了」（实测标志只保持约 1 s），若不补收就会**静默丢一笔**。
         *
         * 判据（三重，尽量不误记）：结果块存在 + 判定值合法（0/1/2）+ 签名与上一笔不同。
         * 签名只含「按保存才会变」的字段（判定 + 保存温度/电压/电阻），**不含**实时量、
         * 测量通道(0x04)与设定温度(0x1A) —— 后两者操作员随手就会改，纳入会让补收通道误记。
         *
         * 2026-09 起还有两道约束把它压得更窄：① 轮询改成**固定周期**（不再累积读取耗时），
         * 正常情况下不会读晚；② 弹卡期间**暂停读取**，恢复后第一帧只重建基线。
         * 于是「一按保存就弹卡」这条路根本不会走到补收 —— 补收只在没弹卡（界面不在测量页）时兜底。
         *
         * ⚠️ 若真机上发现结果块在**非保存**时也被固件改写（出现与操作不符的重复记录），
         * 把本常量改为 false 即退回「纯标志触发」（此时漏收的那一笔会丢，但日志有告警）。
         */
        const val RECOVER_MISSED_SAVE = true

        fun timeText(ms: Long): String =
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ms))
    }

    /** 日志里的「线别 / 机种 / 站别」，未选的用 -- 占位 */
    private fun seatText(): String =
        "${_line ?: "--"} / ${_model ?: "--"} / ${_station ?: "--"}"

    fun destroy() {
        job?.cancel()
        scope.cancel()
    }
}

// ---- JSON 序列化（零依赖，org.json 系统自带）----

/** 诊断日志用：小数保留 1 位，空值 "--" */
private fun f1(v: Double?): String =
    if (v == null) "--" else String.format(java.util.Locale.US, "%.1f", v)

fun MeasurementRecord.toJson(): JSONObject = JSONObject().apply {
    put("snapshotKey", snapshotKey)
    put("timestampMs", timestampMs)
    put("lineName", lineName)
    put("modelName", modelName)
    put("stationName", stationName)
    put("deviceInfo", deviceInfo ?: JSONObject.NULL)
    put("deviceIp", deviceIp)
    put("channel", channel ?: JSONObject.NULL)
    put("targetTemp", targetTemp ?: JSONObject.NULL)
    put("tempLow", tempLow ?: JSONObject.NULL)
    put("tempHigh", tempHigh ?: JSONObject.NULL)
    put("voltageLimitMv", voltageLimitMv ?: JSONObject.NULL)
    put("resistanceLimitOhm", resistanceLimitOhm ?: JSONObject.NULL)
    put("measuredTemp", measuredTemp ?: JSONObject.NULL)
    put("measuredVoltageMv", measuredVoltageMv ?: JSONObject.NULL)
    put("measuredResistanceOhm", measuredResistanceOhm ?: JSONObject.NULL)
    put("result", result)
}

private fun JSONObject.strOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

private fun JSONObject.intOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.dblOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key)

/** 兼容读取：早期 pending JSON 用过 deviceSn / setTemp / leakageMv 等键名 */
fun MeasurementRecord.Companion.fromJson(o: JSONObject): MeasurementRecord? = try {
    MeasurementRecord(
        snapshotKey = o.getString("snapshotKey"),
        timestampMs = o.getLong("timestampMs"),
        lineName = o.optString("lineName"),
        modelName = o.optString("modelName"),
        stationName = o.optString("stationName"),
        deviceInfo = o.strOrNull("deviceInfo") ?: o.strOrNull("deviceSn"),
        deviceIp = o.optString("deviceIp"),
        channel = o.intOrNull("channel"),
        targetTemp = o.intOrNull("targetTemp") ?: o.intOrNull("setTemp"),
        tempLow = o.intOrNull("tempLow"),
        tempHigh = o.intOrNull("tempHigh"),
        voltageLimitMv = o.dblOrNull("voltageLimitMv"),
        resistanceLimitOhm = o.dblOrNull("resistanceLimitOhm"),
        measuredTemp = o.dblOrNull("measuredTemp"),
        measuredVoltageMv = o.dblOrNull("measuredVoltageMv") ?: o.dblOrNull("leakageMv"),
        measuredResistanceOhm = o.dblOrNull("measuredResistanceOhm"),
        result = o.optString("result", "NG")
    )
} catch (_: Exception) {
    null
}
