package com.quick.app.torque

import android.app.Application
import com.quick.app.collect.CommLine
import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.TQ_DEVICE
import com.quick.app.data.db.TQ_LINE
import com.quick.app.data.db.TQ_MODEL
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TORQUE_UNIT
import com.quick.app.data.db.TorqueRecord
import com.quick.app.data.db.torqueDictLabel
import com.quick.app.data.db.torqueJudge
import com.quick.app.torque.usb.FtdiSerialPort
import com.quick.app.torque.usb.UsbSerialLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

/** USB 链路状态（测量页/诊断页共用一句话说明现在是什么情况） */
sealed interface TorqueLinkState {
    /** 监听开着，但没有 FTDI 设备（没插线 / 驱动不认） */
    data object NoDevice : TorqueLinkState

    /** 找到设备了，等用户点系统弹窗授权 */
    data object NeedPermission : TorqueLinkState

    /** 正在打开并配置 */
    data object Opening : TorqueLinkState

    /** 已打开（desc = 芯片 + 波特率 + 包长，诊断页直接显示） */
    data class Open(val desc: String) : TorqueLinkState

    /** 打开了但出错（打开失败/中途断开），reason 说明原因 */
    data class Failed(val reason: String) : TorqueLinkState

    /** 用户手动关了监听 */
    data object Off : TorqueLinkState
}

/** 扭力计界面状态 */
data class TorqueUiState(
    val listen: Boolean = true,
    val link: TorqueLinkState = TorqueLinkState.NoDevice,
    val baud: Int = TorqueController.DEFAULT_BAUD,
    /** 三次测完后的自动保存延时（毫秒，用户可改，默认 5 秒） */
    val saveDelayMs: Long = TorqueController.DEFAULT_SAVE_DELAY_MS,
    // 四个选择（持久化，语义同烙铁：保留到下次）
    val selLine: String? = null,
    val selModel: String? = null,
    val selRange: String? = null,
    val selDevice: String? = null,
    // 本组暂存（三次分开显示）
    val sessionSeq: Int = 0,
    val sessionTotal: Int = TorqueController.SESSION_SIZE,
    val sessionSample: List<Double> = emptyList(),
    val sessionTexts: List<String> = emptyList(),
    /** 满组后被忽略的笔数（第 4 笔…）—— 界面必须如实显示，不静默 */
    val ignoredCount: Int = 0,
    /** 满组后正在等自动保存的截止时刻（界面显示倒计时） */
    val saveAtMs: Long? = null,
    /** 缺哪几项（满组但信息不全 → 显眼提示；空 = 正常） */
    val missingFields: List<String> = emptyList(),
    /** 本组单位（设备原样，实测 `kgf*cm`） */
    val unit: String = TORQUE_UNIT,
    // 最近一笔 / 最近保存
    val lastReading: TorqueReading? = null,
    val lastSaved: TorqueRecord? = null,
    // 最近收到的原始块（页内显示最新一条，诊断页看全部）
    val frames: List<TorqueFrame> = emptyList(),
    val lastError: String? = null,
    val pendingCount: Int = 0,
    val readingCount: Int = 0,
    val byteCount: Long = 0,
    /** 解析缓冲里没认出来的字节数（诊断页显示 —— 格式又变了的话这里会堆起来） */
    val pendingBytes: Int = 0,
    /** 缓冲里那段没认出来的内容（可读化） */
    val pendingText: String = "",
    /** 被白名单滤掉的噪声字节**累计**多少（诊断页显示：>0 说明链路上有非协议字节，别静默） */
    val noiseTotal: Long = 0,
    /** 因为超过缓冲上限被丢掉的字节（诊断页显示，不装作没发生） */
    val droppedBytes: Long = 0
)

/** 收到数据的事件（App 根节点据此自动切页 / 弹卡） */
sealed interface TorqueEvent {
    /** 单笔已进暂存组（App 据此自动切到扭力计页） */
    data class Reading(val value: Double, val seq: Int, val total: Int) : TorqueEvent

    /** 一组三笔已自动保存成一条记录（弹卡 + 振动） */
    data class Saved(val record: TorqueRecord) : TorqueEvent
}

/**
 * 扭力计控制器：**App 级单例**，与 [com.quick.app.collect.MeasurementController] 完全并列且互不干涉。
 *
 * 用户最硬的那条要求（「添加新界面不能影响原本烙铁的功能，切换界面时烙铁的通信不能断」）
 * 靠三点保证：
 * 1. **两个控制器各有各的线程与协程**（各自 SupervisorJob + Dispatchers.IO），
 *    扭力计读 USB 阻塞多久都影响不到 Modbus 轮询；
 * 2. 界面切换只是换一个 Composable，**两个控制器的循环都不在界面里**（都不随界面销毁而停）；
 * 3. 扭力计这块**不碰** MeasurementController 的任何状态、任何表、任何连接。
 *
 * 一轮测量的完整流程（2026-09-22 用户定稿）：
 * ```
 * 读数 → 进暂存组（1/3 2/3 3/3，界面分开显示）
 *      → 第 3 笔后起延时（默认 5 秒，可设定）
 *         ├─ 期间点「重测」 → 清空暂存，重新开始
 *         └─ 延时到 + 四项信息（线别/机种/扭矩范围/设备信息）齐全 → 自动保存一行
 *            四项缺任何一项 → **不保存**，页面上显眼提示缺哪几项（用户 2026-09-22 硬要求）
 * ```
 * 保存后把「设备信息」选择清空（它是电动螺丝机的 ID，下一件换机台；用户 2026-09-22 定）。
 *
 * 数据可靠性沿用烙铁那一套（不静默丢数据）：每组先写 pending（app_setting）→
 * 幂等插入 torque_record → 清 pending；崩在中间由启动时的 [recoverPending] 补记，
 * 组号唯一索引吸收重复。
 */
class TorqueController(private val app: Application, val db: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var saveJob: Job? = null

    private val link = UsbSerialLink(app)
    private val parser = TorqueStreamParser()
    private val session = TorqueSession(SESSION_SIZE)

    private val _logs = MutableStateFlow<List<CommLine>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _ui = MutableStateFlow(TorqueUiState())
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<TorqueEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    // ---- 选择项（内存 + 库双份：库是唯一真源，内存避免每次读库）----
    @Volatile private var _line: String? = null
    @Volatile private var _model: String? = null
    @Volatile private var _range: String? = null
    @Volatile private var _device: String? = null

    /** 当前组号（一组一个，保存后换新号） */
    @Volatile private var sessionId: String = newSessionId()

    /** 上一笔被接受的报文原文与时刻（只用于挡「同一笔被重复输出」这种回显） */
    @Volatile private var lastAcceptedText: String = ""
    @Volatile private var lastAcceptedMs = 0L

    suspend fun init() {
        _line = db.settingDao().get(KEY_LINE)
        _model = db.settingDao().get(KEY_MODEL)
        _range = db.settingDao().get(KEY_RANGE)
        _device = db.settingDao().get(KEY_DEVICE)
        val baud = db.settingDao().get(KEY_BAUD)?.toIntOrNull() ?: DEFAULT_BAUD
        val listen = db.settingDao().get(KEY_LISTEN)?.toBooleanStrictOrNull() ?: true
        val delayMs = db.settingDao().get(KEY_SAVE_DELAY)?.toLongOrNull() ?: DEFAULT_SAVE_DELAY_MS
        _ui.update {
            it.copy(
                baud = baud, listen = listen, saveDelayMs = clampDelay(delayMs),
                selLine = _line, selModel = _model, selRange = _range, selDevice = _device,
                link = if (listen) TorqueLinkState.NoDevice else TorqueLinkState.Off
            )
        }
        link.onDeviceChanged = {
            // 插拔只是「叫醒」信号：读循环自己会实查设备在不在（广播不可靠也不影响正确性）
            postLog("USB 设备插拔事件")
        }
        scope.launch { recoverPending() }
        if (listen) start()
    }

    /** 开关监听（= 开关 USB 读取）。关掉不会影响烙铁采集。 */
    fun setListening(on: Boolean) {
        if (on == _ui.value.listen) return
        _ui.update { it.copy(listen = on) }
        scope.launch { db.settingDao().put(AppSetting(KEY_LISTEN, on.toString())) }
        if (on) start() else stop()
    }

    fun setBaud(baud: Int) {
        _ui.update { it.copy(baud = baud) }
        scope.launch { db.settingDao().put(AppSetting(KEY_BAUD, baud.toString())) }
        postLog("波特率改为 $baud，正在重连…")
        stop()
        start()
    }

    /**
     * 改「三次测完后的自动保存延时」（用户要求：时间可设定，默认 5 秒）。
     * 若此刻正好有一组在等保存，**按新延时重排**（否则改了设置却看不出变化，现场会以为没生效）。
     */
    fun setSaveDelay(ms: Long) {
        val v = clampDelay(ms)
        _ui.update { it.copy(saveDelayMs = v) }
        scope.launch { db.settingDao().put(AppSetting(KEY_SAVE_DELAY, v.toString())) }
        postLog("自动保存延时改为 ${v / 1000.0} 秒")
        if (session.isFull) {
            val at = session.lastAtMs + v
            _ui.update { it.copy(saveAtMs = at) }
            scheduleSaveAt(at)
        }
    }

    private fun start() {
        if (job != null) return
        link.register()
        postLog("▶ 扭力计监听已开启（等待 USB 设备…）")
        job = scope.launch { runLink() }
    }

    private fun stop() {
        job?.cancel()
        job = null
        parser.reset()
        link.unregister()
        _ui.update { it.copy(link = TorqueLinkState.Off) }
    }

    fun destroy() {
        job?.cancel()
        saveJob?.cancel()
        link.unregister()
        scope.cancel()
    }

    // ---------- 只读查询（诊断页用；不改变任何状态） ----------

    /** 系统里所有 FTDI 设备（诊断页列出来看 —— 有时问题是「插了但不是 FTDI」） */
    fun usbDevices(): List<android.hardware.usb.UsbDevice> = link.devices()

    fun usbHasPermission(d: android.hardware.usb.UsbDevice): Boolean = link.hasPermission(d)

    // ---------- 选择项 ----------

    suspend fun select(kind: String, name: String?) {
        val key: String
        when (kind) {
            TQ_LINE -> { _line = name; key = KEY_LINE }
            TQ_MODEL -> { _model = name; key = KEY_MODEL }
            TQ_RANGE -> { _range = name; key = KEY_RANGE }
            else -> { _device = name; key = KEY_DEVICE }
        }
        _ui.update {
            when (kind) {
                TQ_LINE -> it.copy(selLine = name)
                TQ_MODEL -> it.copy(selModel = name)
                TQ_RANGE -> it.copy(selRange = name)
                else -> it.copy(selDevice = name)
            }
        }
        db.settingDao().put(AppSetting(key, name ?: ""))
        // 组已满、延时也过了、只是缺信息 → 补齐这一项就立刻保存（不用再等一轮）
        if (session.isFull) {
            val at = _ui.value.saveAtMs
            if (at == null || System.currentTimeMillis() >= at) trySave()
        }
    }

    /** 扫码/手输新增一个字典项（设备信息常用），已存在就只是选中它 */
    suspend fun addDict(kind: String, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        return runCatching {
            if (db.torqueDictDao().find(kind, n) == null) {
                db.torqueDictDao().insert(com.quick.app.data.db.TorqueDict(kind = kind, name = n))
                postLog("＋ 已新增${torqueDictLabel(kind)}「$n」")
            }
            select(kind, n)
            true
        }.getOrElse {
            postLog("!! 新增字典项失败：${it.message}")
            false
        }
    }

    // ---------- 重测（无需密码，用户要求） ----------

    /**
     * 重测：**清空本组暂存**（三笔全清），停掉待保存的计时，回到 0/3。
     * 已保存的记录不受影响；数据库里本来就没有这一组的任何行（暂存只在内存里）。
     */
    fun remeasure() {
        if (session.count == 0 && session.ignoredCount == 0) {
            postLog("↺ 重测：当前没有暂存数据")
            return
        }
        val n = session.count
        saveJob?.cancel()
        saveJob = null
        session.reset()
        sessionId = newSessionId()
        pushSession(reset = true)
        postLog("↺ 已重测：清空本组暂存（$n 笔）")
    }

    // ---------- 链路 ----------

    private suspend fun runLink() {
        while (scope.isActive && _ui.value.listen) {
            val device = link.findDevice()
            if (device == null) {
                setLink(TorqueLinkState.NoDevice)
                delay(SCAN_INTERVAL_MS)
                continue
            }
            if (!link.hasPermission(device)) {
                setLink(TorqueLinkState.NeedPermission)
                postLog("请求 USB 权限：${com.quick.app.torque.usb.FtdiUsb.describe(device)}")
                val ok = link.requestPermission(device)
                if (!ok) {
                    setLink(TorqueLinkState.Failed("未获得 USB 权限"))
                    postLog("! USB 权限未授予（等待重试）")
                    delay(RETRY_INTERVAL_MS)
                    continue
                }
                postLog("✔ USB 权限已授予")
            }
            setLink(TorqueLinkState.Opening)
            val port = FtdiSerialPort.open(link.openConnection(device), device, _ui.value.baud)
            if (port == null) {
                setLink(TorqueLinkState.Failed("打开设备失败"))
                postLog("! 打开 USB 设备失败（可能被其它程序占用）：${com.quick.app.torque.usb.FtdiUsb.describe(device)}")
                delay(RETRY_INTERVAL_MS)
                continue
            }
            try {
                port.configure().forEach { postLog("USB  $it") }
                setLink(TorqueLinkState.Open(port.describe()))
                postLog("✔ 扭力计串口已打开：${port.describe()}")
                _ui.update { it.copy(lastError = null) }
                // 换设备/重连后重建解析缓冲，免得半截残留串到新数据上
                parser.reset()
                readLoop(port, device.deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postLog("!! USB 读取中断：${e.message ?: e.javaClass.simpleName}")
                _ui.update { it.copy(lastError = e.message) }
            } finally {
                port.close()
            }
            setLink(TorqueLinkState.Failed("连接已断开"))
            postLog("USB 连接已断开，${RETRY_INTERVAL_MS}ms 后重连")
            delay(RETRY_INTERVAL_MS)
        }
    }

    private suspend fun readLoop(port: FtdiSerialPort, deviceId: Int) {
        var emptyStreak = 0
        while (scope.isActive && _ui.value.listen && port.isOpen) {
            val raw = port.readPacket(READ_TIMEOUT_MS)
            if (raw.isEmpty()) {
                emptyStreak++
                // 每约 1 秒实查一次设备还在不在 —— 拔线时 bulkTransfer 不一定会报错，
                // 只靠它会让循环空转（现场表现是「界面还显示已连接」）
                if (emptyStreak % 5 == 0 && link.findDevice()?.deviceId != deviceId) {
                    postLog("USB 设备已拔出")
                    return
                }
                continue
            }
            emptyStreak = 0
            // 用连接自己的包长剥（不是默认 64）——换个非 FT232R 的芯片时端点包长可能不同
            val data = port.stripStatus(raw)
            if (data.isEmpty()) continue
            // 白名单过滤（2026-09-25 用户定）：只把协议允许的字符送进解析器，
            // \x18 \x0E \x0F 这类噪声在这里就丢掉，读数 `+ 5.40 kgf*cm` 原样留下。
            // 丢了多少**如实计数**（帧上 + 累计），绝不静默 —— 真丢多了说明链路还有问题。
            val clean = TorqueStreamParser.keepProtocol(data)
            val noiseBytes = data.size - clean.size
            // 先喂解析器再更新界面：这样诊断页显示的「缓冲残留 / 丢字节数」是同一次读取后的真实值
            val readings = parser.feed(clean)
            val frame = TorqueFrame(
                atMs = System.currentTimeMillis(),
                // 原始 hex 与「剥状态后、滤噪声前」的 hex 都留着 —— 诊断页靠它回看设备到底发了什么
                rawHex = TorqueStreamParser.hex(raw),
                dataHex = TorqueStreamParser.hex(data),
                text = TorqueStreamParser.printable(clean),
                noiseBytes = noiseBytes,
                noiseUtf8 = if (noiseBytes > 0) TorqueStreamParser.droppedTextOrNull(data) else null
            )
            _ui.update {
                it.copy(
                    frames = (it.frames + frame).takeLast(MAX_FRAMES),
                    byteCount = it.byteCount + data.size,
                    noiseTotal = it.noiseTotal + noiseBytes,
                    pendingBytes = parser.pendingBytes,
                    pendingText = parser.pendingText(),
                    droppedBytes = parser.droppedBytes
                )
            }
            for (r in readings) onReading(r)
        }
    }

    private fun setLink(state: TorqueLinkState) {
        _ui.update { it.copy(link = state) }
    }

    // ---------- 一笔读数 ----------

    private suspend fun onReading(reading: TorqueReading) {
        _ui.update { it.copy(lastReading = reading) }
        val now = System.currentTimeMillis()

        // 同一笔被设备重复输出（回显）：原文一模一样且几乎同时 → 只认一笔。
        // 判据故意收得很窄（完全相同 + 极短间隔）——真实的两次测量不可能这么近，
        // 而「宁可多记一笔」在这里是错的（会凑满一组），所以宁可窄。
        if (reading.rawLine == lastAcceptedText && now - lastAcceptedMs < ECHO_GAP_MS) {
            postLog("· 与上一笔完全相同的重复报文，已跳过：${reading.rawLine}")
            return
        }

        if (session.isFull) {
            // 用户 2026-09-22 拍板：满组后的第 4 笔**不并入本组**，只记一笔「已忽略」
            session.add(reading.value, reading.rawLine, reading.unit, now)
            _ui.update { it.copy(ignoredCount = session.ignoredCount) }
            postLog(
                "· 本组已满（${session.count}/${SESSION_SIZE}），第 ${session.count + session.ignoredCount} 笔" +
                    "已忽略：${reading.rawLine} —— 等自动保存后请重测这一笔"
            )
            return
        }

        lastAcceptedText = reading.rawLine
        lastAcceptedMs = now

        val step = session.add(reading.value, reading.rawLine, reading.unit, now)
        _ui.update { it.copy(readingCount = it.readingCount + 1, lastError = null) }
        pushSession()
        postLog("✔ 第 ${step.seq}/${step.total} 笔 ${reading.rawLine}")
        _events.tryEmit(TorqueEvent.Reading(reading.value, step.seq, step.total))

        if (step.done) {
            val at = now + _ui.value.saveDelayMs
            _ui.update { it.copy(saveAtMs = at, missingFields = missingLabels()) }
            scheduleSaveAt(at)
        }
    }

    /** 把暂存组推给界面（三笔分开显示 + 单位 + 忽略笔数） */
    private fun pushSession(reset: Boolean = false) {
        _ui.update {
            it.copy(
                sessionSeq = session.count,
                sessionTotal = SESSION_SIZE,
                sessionSample = session.sample,
                sessionTexts = session.sampleTexts,
                unit = session.unit.ifBlank { TORQUE_UNIT },
                ignoredCount = session.ignoredCount,   // reset() 已清零，这里照抄即可
                saveAtMs = if (reset) null else it.saveAtMs,
                missingFields = if (reset) emptyList() else it.missingFields
            )
        }
    }

    /** 满组后：等 [at] 毫秒时刻保存（重测会取消它） */
    private fun scheduleSaveAt(at: Long) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay((at - System.currentTimeMillis()).coerceAtLeast(0))
            trySave()
        }
    }

    /**
     * 尝试保存当前组。三条闸门缺一不可：
     * 1. 组已满（三笔都在）；
     * 2. 延时已过（`saveAtMs == null` 表示延时已过但不齐，见 [select]）；
     * 3. **四项信息齐全** —— 缺任何一项都**不保存**，页面显眼提示（用户 2026-09-22 硬要求）。
     */
    private suspend fun trySave() {
        if (!session.isFull) return
        val now = System.currentTimeMillis()
        val at = _ui.value.saveAtMs
        if (at != null && now < at) return

        val missing = missingLabels()
        if (missing.isNotEmpty()) {
            _ui.update { it.copy(saveAtMs = null, missingFields = missing) }
            postLog("⚠ 信息不全，暂不保存（缺 ${missing.joinToString("、")}）—— 补齐后会自动保存这一组")
            return
        }

        val range = _range?.let { db.torqueDictDao().find(TQ_RANGE, it) }
        val avg = session.average()
        val judge = torqueJudge(avg, range?.minValue, range?.maxValue)
        if (judge == null) {
            // 范围字典是 v8 时代建的（没有上下限）或数值缺失：不编造判定，也不保存
            val msg = "扭矩范围「${_range ?: "-"}」缺少上下限，请到管理页补填"
            _ui.update { it.copy(saveAtMs = null, missingFields = listOf("扭矩范围（$msg）")) }
            postLog("⚠ $msg —— 这一组暂不保存")
            return
        }

        val rec = TorqueRecord(
            sessionId = sessionId,
            timestampMs = session.lastAtMs.ifZero(now),
            savedAtMs = now,
            lineName = _line ?: "",
            modelName = _model ?: "",
            rangeName = _range ?: "",
            rangeMin = range?.minValue,
            rangeMax = range?.maxValue,
            deviceName = _device ?: "",
            v1 = session.sample.getOrNull(0),
            v2 = session.sample.getOrNull(1),
            v3 = session.sample.getOrNull(2),
            average = avg,
            judge = judge,
            sampleCount = session.count,
            unitText = session.unit.ifBlank { TORQUE_UNIT },
            rawText = session.sampleTexts.joinToString(" | "),
            rawHex = session.rawHexJoined { line -> hexOfText(line) }
        )
        store(rec)

        saveJob = null
        session.reset()
        sessionId = newSessionId()
        pushSession(reset = true)
        _ui.update { it.copy(lastSaved = rec) }
        // 设备信息 = 电动螺丝机 ID：保存完平均值后清空（下一件换机台；用户 2026-09-22 定）
        select(TQ_DEVICE, null)
        postLog("★ 已保存一组（${judge}）：平均 ${rec.averageText} $TORQUE_UNIT　" +
            "三笔 ${rec.v1Text} / ${rec.v2Text} / ${rec.v3Text}　" +
            "范围 ${rec.rangeName}（${rec.rangeMin} ~ ${rec.rangeMax}）")
        _events.tryEmit(TorqueEvent.Saved(rec))
    }

    /** 落库：pending 先写盘 → 幂等插入 → 清 pending。失败留 pending，重启补记，不丢。 */
    private suspend fun store(rec: TorqueRecord) {
        val pendingKey = PENDING_PREFIX + rec.sessionId
        val json = rec.toJson().toString()
        try {
            db.settingDao().put(AppSetting(pendingKey, json))
            // 先写盘再入库 —— 这一组此刻开始就「有据可查」（pendingCount 如实反映还有几组悬着）
            _ui.update { it.copy(pendingCount = it.pendingCount + 1) }
            val id = db.torqueRecordDao().insert(rec)
            db.settingDao().delete(pendingKey)
            _ui.update { it.copy(pendingCount = (it.pendingCount - 1).coerceAtLeast(0)) }
            if (id == -1L) postLog("○ 重复组号已跳过：${rec.sessionId}")
        } catch (e: Exception) {
            postLog("!! 入库失败(${e.message ?: e.javaClass.simpleName})，读数暂存待恢复，不静默丢弃")
            _ui.update { it.copy(lastError = "入库失败，读数已暂存待恢复") }
            runCatching {
                val retry = db.torqueRecordDao().insert(rec)
                db.settingDao().delete(pendingKey)
                _ui.update { it.copy(pendingCount = (it.pendingCount - 1).coerceAtLeast(0)) }
                if (retry != -1L) postLog("✔ 重试入库成功")
            }
        }
    }

    /** 崩溃/被杀恢复：把遗留 pending 读数补记回正式表（组号唯一索引吸收重复） */
    private suspend fun recoverPending() {
        val pendings = db.settingDao().all().filter { it.key.startsWith(PENDING_PREFIX) }
        if (pendings.isEmpty()) return
        var n = 0
        for (p in pendings) {
            try {
                val rec = TorqueRecord.fromJson(JSONObject(p.value)) ?: continue
                val id = db.torqueRecordDao().insert(rec)
                db.settingDao().delete(p.key)
                if (id != -1L) n++
            } catch (_: Exception) {
            }
        }
        if (n > 0) postLog("恢复补记 $n 组先前未保存的扭力数据")
        // 补记失败的仍然留在 pending（界面上「待补记」要如实显示还剩几组）
        _ui.update { it.copy(pendingCount = pendings.size - n) }
    }

    // ---------- 未选信息 ----------

    /** 四项里还没选的（按固定顺序），界面照这个列表逐项高亮 */
    private fun missingLabels(): List<String> = buildList {
        if (_line.isNullOrBlank()) add(torqueDictLabel(TQ_LINE))
        if (_model.isNullOrBlank()) add(torqueDictLabel(TQ_MODEL))
        if (_range.isNullOrBlank()) add(torqueDictLabel(TQ_RANGE))
        if (_device.isNullOrBlank()) add(torqueDictLabel(TQ_DEVICE))
    }

    // ---------- 日志 ----------

    fun postLog(text: String) {
        _logs.update { list ->
            val nl = list + CommLine(System.currentTimeMillis(), text)
            if (nl.size > MAX_LOG) nl.takeLast(MAX_LOG) else nl
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /** 清空原始块列表（诊断页「清屏」） */
    fun clearFrames() {
        parser.reset()
        _ui.update {
            it.copy(
                frames = emptyList(), byteCount = 0, readingCount = 0, noiseTotal = 0,
                droppedBytes = 0, pendingBytes = 0, pendingText = ""
            )
        }
    }

    /** 手动重试：设备在但不认/权限卡住时，用户点一下立刻重连（不用等 2 秒轮询） */
    fun retryNow() {
        if (!_ui.value.listen) {
            postLog("监听已关闭，先打开监听再重试")
            return
        }
        postLog("手动重连…")
        stop()
        start()
    }

    companion object {
        const val SESSION_SIZE = 3
        const val DEFAULT_BAUD = 19200          // 实测确认：19200bps、8 数据位、2 停止位、无校验
        const val DEFAULT_SAVE_DELAY_MS = 5000L // 三次测完 5 秒内没重测就自动保存（用户要求）
        const val MIN_SAVE_DELAY_MS = 1000L
        const val MAX_SAVE_DELAY_MS = 60000L
        const val KEY_LINE = "torque.sel.line"
        const val KEY_MODEL = "torque.sel.model"
        const val KEY_RANGE = "torque.sel.range"
        const val KEY_DEVICE = "torque.sel.device"
        const val KEY_LISTEN = "torque.listen"
        const val KEY_BAUD = "torque.baud"
        const val KEY_SAVE_DELAY = "torque.saveDelayMs"
        const val PENDING_PREFIX = "pending.torque:"

        /** 读超时：一次 bulkTransfer 最多阻塞这么久（没数据就正常返回空） */
        const val READ_TIMEOUT_MS = 200

        /** 没找到设备时的扫描间隔 */
        const val SCAN_INTERVAL_MS = 1500L

        /** 出错后的重试间隔 */
        const val RETRY_INTERVAL_MS = 2000L

        /** 判定为「同一笔被重复输出」的最大间隔（判据还要求原文完全相同） */
        const val ECHO_GAP_MS = 150L

        const val MAX_FRAMES = 300
        const val MAX_LOG = 2000

        fun newSessionId(): String = "s" + System.currentTimeMillis()

        fun clampDelay(ms: Long): Long = ms.coerceIn(MIN_SAVE_DELAY_MS, MAX_SAVE_DELAY_MS)

        /** 三笔原文 → 每笔的字节 hex（ISO-8859-1 与解析器同一口径，1 字符 = 1 字节） */
        fun hexOfText(t: String): String = TorqueStreamParser.hex(t.toByteArray(Charsets.ISO_8859_1))

        private fun Long.ifZero(fallback: Long): Long = if (this <= 0L) fallback else this
    }
}

// ---- JSON 序列化（零依赖，org.json 系统自带）----
// 与 MeasurementController 的 pending 机制同一套做法：只在「每组先落盘」这条路上用。

fun TorqueRecord.toJson(): JSONObject = JSONObject().apply {
    put("sessionId", sessionId)
    put("timestampMs", timestampMs)
    put("savedAtMs", savedAtMs)
    put("lineName", lineName)
    put("modelName", modelName)
    put("rangeName", rangeName)
    put("rangeMin", rangeMin ?: JSONObject.NULL)
    put("rangeMax", rangeMax ?: JSONObject.NULL)
    put("deviceName", deviceName)
    put("v1", v1 ?: JSONObject.NULL)
    put("v2", v2 ?: JSONObject.NULL)
    put("v3", v3 ?: JSONObject.NULL)
    put("average", average ?: JSONObject.NULL)
    put("judge", judge)
    put("sampleCount", sampleCount)
    put("unitText", unitText)
    put("rawText", rawText)
    put("rawHex", rawHex)
}

fun TorqueRecord.Companion.fromJson(o: JSONObject): TorqueRecord? = try {
    fun d(key: String): Double? =
        if (o.has(key) && !o.isNull(key)) o.optDouble(key) else null
    TorqueRecord(
        sessionId = o.getString("sessionId"),
        timestampMs = o.getLong("timestampMs"),
        savedAtMs = o.optLong("savedAtMs", o.optLong("timestampMs")),
        lineName = o.optString("lineName"),
        modelName = o.optString("modelName"),
        rangeName = o.optString("rangeName"),
        rangeMin = d("rangeMin"),
        rangeMax = d("rangeMax"),
        deviceName = o.optString("deviceName"),
        v1 = d("v1"),
        v2 = d("v2"),
        v3 = d("v3"),
        average = d("average"),
        judge = o.optString("judge"),
        sampleCount = o.optInt("sampleCount", 3),
        unitText = o.optString("unitText", TORQUE_UNIT),
        rawText = o.optString("rawText"),
        rawHex = o.optString("rawHex")
    )
} catch (_: Exception) {
    null
}
