package com.quick.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quick.app.QuickApp
import com.quick.app.collect.ConnState
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.data.db.TorqueRecord
import com.quick.app.torque.TorqueEvent
import com.quick.app.torque.TorqueLinkState
import com.quick.app.ui.config.ConfigScreen
import com.quick.app.ui.diag.DiagnosticScreen
import com.quick.app.ui.history.HistoryScreen
import com.quick.app.ui.manage.ManageScreen
import com.quick.app.ui.measure.MeasureScreen
import com.quick.app.ui.torque.TorqueDiagScreen
import com.quick.app.ui.torque.TorqueHistoryScreen
import com.quick.app.ui.torque.TorqueManageScreen
import com.quick.app.ui.torque.TorqueMeasureScreen
import com.quick.app.ui.torque.TorqueOverlay
import kotlinx.coroutines.delay

/**
 * 页面结构：
 * - **两套测量界面并列**（用户 2026-09-21 要求）：烙铁（192AF+，Wi-Fi Modbus）与扭力计（USB 串口）。
 *   两者的测量页各是从测量页按钮进入的整页，返回键回到来处；
 * - **底部常驻切换条**只在两个测量页显示（子页面先返回再切，避免在配置/历史中途被切走）。
 *
 * 三个全局职责也放在这里：
 * 1. **状态栏/导航栏内边距** —— MainActivity 开了 edge-to-edge；
 *    分工是「**谁在最下面谁让开导航栏**」（2026-09-22 修掉切换条被导航栏压住的 bug，详见下面内容 Box 的注释）；
 * 2. **烙铁结果全屏弹卡**—— 挂根节点，任何页面都能弹；弹卡期间停止烙铁判定与记录；
 * 3. **扭力计到数处理**—— 收到数据自动切到扭力计测量页；一组三笔自动保存后弹卡 + 振动。
 *
 * ⚠️ **两套通信绝不相干**（用户最硬的要求：「切换界面时烙铁的通信不能断」）：
 * 两个控制器都是 App 级单例（见 [QuickApp]），各有各的线程与循环，**都不在界面里**；
 * 这里做的任何切页、弹卡都不 stop/hold 对方 —— 烙铁弹卡会 hold 烙铁自己（这是原有行为），
 * 扭力计弹卡**不 hold 任何东西**，烙铁的轮询与判定照常跑。
 */
private enum class Page {
    // 烙铁（192AF+ / Wi-Fi Modbus）
    Measure, History, Config, Manage, Diagnostic,
    // 扭力计（USB 串口）
    TqMeasure, TqHistory, TqManage, TqDiag;

    val isTorque: Boolean get() = name.startsWith("Tq")
    val isMeasure: Boolean get() = this == Measure || this == TqMeasure
    /** 这一页属于哪套测量界面（返回键回不去时的兜底） */
    val home: Page get() = if (isTorque) TqMeasure else Measure
}

/** 烙铁弹卡显示时长（= 停止判定与记录的时长；轮询本身照常，链路保温） */
private const val OVERLAY_MS = 5000L

/** 扭力计弹卡显示时长：只挡视线，**不 hold 任何控制器**，超时自动收起（也可点掉） */
private const val TQ_OVERLAY_MS = 8000L

/**
 * 手动切回烙铁后的抑制窗口：这段时间内扭力计再来数据也不抢页面。
 * 现场常常是「刚切回烙铁看一眼，扭力计那边还在出数」，没有这个窗口就会被反复抢走。
 */
private const val AUTO_SWITCH_GUARD_MS = 5000L

@Composable
fun App() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as QuickApp
    val controller = app.controller
    val torque = app.torque

    // 用序号保存，避免枚举在 rememberSaveable 里的序列化差异
    var pageOrd by rememberSaveable { mutableIntStateOf(Page.Measure.ordinal) }
    var backOrd by rememberSaveable { mutableIntStateOf(Page.Measure.ordinal) }
    val page = Page.entries[pageOrd]

    /** 进下一页，记住来处 */
    fun go(target: Page) {
        backOrd = pageOrd
        pageOrd = target.ordinal
    }

    /** 返回来处（默认回本套的测量页） */
    fun back() {
        val cur = Page.entries[pageOrd]
        pageOrd = backOrd
        backOrd = cur.home.ordinal
    }

    // 系统返回键：非测量页一律回上一页；测量页交给系统（退出 App）
    BackHandler(enabled = !page.isMeasure) { back() }

    // ── 烙铁全屏结果弹卡（OK 才有；NG/无效 不入库也不弹，见 MeasurementController.skipResult）──
    var overlay by remember { mutableStateOf<MeasurementRecord?>(null) }
    var overlayNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(controller) {
        controller.resultEvents.collect { rec ->
            vibrate(ctx)
            overlay = rec
            overlayNonce++
            // 弹卡显示期间停止烙铁判定与记录（显示完才记下一组）；轮询照常，链路保温
            controller.hold(OVERLAY_MS)
        }
    }
    // 自动收起（提前点掉弹卡时由 ResultOverlay 直接清空，这里再兜一次底也无害）
    LaunchedEffect(overlayNonce) {
        if (overlay != null) {
            delay(OVERLAY_MS)
            overlay = null
            controller.resumePolling()
        }
    }

    // ── 扭力计：到数就切页；一组三笔自动保存时弹卡 + 振动 ──
    var tqOverlay by remember { mutableStateOf<TorqueRecord?>(null) }
    var tqOverlayNonce by remember { mutableIntStateOf(0) }
    // 上次「手动」切页的时刻（只用于抑制自动切页，不参与任何采集逻辑）
    var lastManualMs by remember { mutableLongStateOf(0L) }

    /** 自动切到扭力计测量页：只在烙铁测量页时生效（子页面/配置中途绝不抢页面） */
    fun autoSwitchToTorque() {
        if (Page.entries[pageOrd] != Page.Measure) return
        if (System.currentTimeMillis() - lastManualMs < AUTO_SWITCH_GUARD_MS) return
        pageOrd = Page.TqMeasure.ordinal
        backOrd = Page.Measure.ordinal
    }

    LaunchedEffect(torque) {
        torque.events.collect { ev ->
            when (ev) {
                // 单笔：只切页（不弹卡、不振动 —— 现场还要接着测第 2、3 笔）
                is TorqueEvent.Reading -> autoSwitchToTorque()
                // 一组三笔自动保存完成：切页 + 弹卡 + 振动
                is TorqueEvent.Saved -> {
                    autoSwitchToTorque()
                    vibrate(ctx)
                    tqOverlay = ev.record
                    tqOverlayNonce++
                    // 注意：这里**不 hold 烙铁控制器** —— 扭力计弹卡不该影响烙铁采集
                }
            }
        }
    }
    LaunchedEffect(tqOverlayNonce) {
        if (tqOverlay != null) {
            delay(TQ_OVERLAY_MS)
            tqOverlay = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 页面内容避让状态栏与输入法。
            //
            // ⚠️ 这里**不用 systemBarsPadding()**（2026-09-22 修）：它把导航栏的内边距也算进内容里，
            // 而底部切换条是这一块的兄弟节点、排在它下面 —— 结果是「内容避开导航栏」+「切换条又被
            // 排在内容之后」，切换条就直接画在系统导航栏底下（现场表现：点不到、被手势条挡住）。
            // 正确分工：**谁在最下面，谁负责导航栏的内边距**。
            // 有切换条时（测量页）切换条自己让开；没切换条时（子页面）内容自己让开。
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .statusBarsPadding()
                    .then(if (page.isMeasure) Modifier else Modifier.navigationBarsPadding())
                    .imePadding()
            ) {
                when (page) {
                    // ---- 烙铁 ----
                    Page.Measure -> MeasureScreen(
                        openHistory = { go(Page.History) },
                        openConfig = { go(Page.Config) },
                        openManage = { go(Page.Manage) },
                        openDiag = { go(Page.Diagnostic) }
                    )
                    Page.History -> HistoryScreen(onBack = { back() })
                    Page.Config -> ConfigScreen(
                        onBack = { back() },
                        openManage = { go(Page.Manage) },
                        openDiag = { go(Page.Diagnostic) }
                    )
                    Page.Manage -> ManageScreen(onBack = { back() })
                    Page.Diagnostic -> DiagnosticScreen(onBack = { back() })

                    // ---- 扭力计 ----
                    Page.TqMeasure -> TorqueMeasureScreen(
                        openHistory = { go(Page.TqHistory) },
                        openManage = { go(Page.TqManage) },
                        openDiag = { go(Page.TqDiag) }
                    )
                    Page.TqHistory -> TorqueHistoryScreen(onBack = { back() })
                    Page.TqManage -> TorqueManageScreen(onBack = { back() })
                    Page.TqDiag -> TorqueDiagScreen(onBack = { back() })
                }
            }
            // 底部常驻切换条：只在两个测量页出现
            if (page.isMeasure) {
                SwitchBar(
                    torquePage = page.isTorque,
                    onIron = { lastManualMs = System.currentTimeMillis(); pageOrd = Page.Measure.ordinal },
                    onTorque = { lastManualMs = System.currentTimeMillis(); pageOrd = Page.TqMeasure.ordinal }
                )
            }
        }

        // 全屏弹卡画在内边距**之外**：状态栏/导航栏区域也铺满结果色，不留缝
        overlay?.let { rec ->
            ResultOverlay(rec) {
                overlay = null
                controller.resumePolling()
            }
        }
        // 扭力计「一组已自动保存」弹卡（同样铺满；点一下关闭）
        tqOverlay?.let { rec ->
            TorqueOverlay(rec = rec, onDismiss = { tqOverlay = null })
        }
    }
}

/**
 * 底部常驻切换条：左「烙铁」右「扭力计」，各带一个状态圆点。
 *
 * 为什么不做成底部导航栏（NavigationBar）那套：车间戴手套点，目标要大；
 * 而且这里只需要两个互斥的大按钮，加图标/波纹反而更难瞄。
 * 状态圆点让操作员**不切页面也知道另一套通不通**（这正是「烙铁通信不能断」要的可观测性）。
 *
 * ⚠️ 两个尺寸/位置问题（2026-09-22 用户反馈「太矮了、跟平板导航按钮重叠」）：
 * 1. **导航栏内边距加在这里**（[navigationBarsPadding] 放在 Surface **内部**）——
 *    背景色铺到屏幕最下沿（不留一条白缝），但**可点的内容整体抬到系统导航栏之上**；
 * 2. 高度 64dp → **88dp**、字号跟着加大：戴手套、隔着防护罩也好点。
 */
@Composable
private fun SwitchBar(
    torquePage: Boolean,
    onIron: () -> Unit,
    onTorque: () -> Unit
) {
    val app = LocalContext.current.applicationContext as QuickApp
    val iron by app.controller.ui.collectAsStateWithLifecycle()
    val tq by app.torque.ui.collectAsStateWithLifecycle()

    val (ironText, ironColor) = ironStatus(iron.conn, iron.running)
    val (tqText, tqColor) = torqueStatus(tq.link, tq.listen)

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        // 先让开导航栏、再定高度：总高 = 88dp + 导航栏高度，可点区域永远在导航栏上方
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(SWITCH_BAR_HEIGHT)) {
            SwitchCell("烙铁", ironText, ironColor, !torquePage, Modifier.weight(1f).fillMaxSize(), onIron)
            Box(Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colorScheme.outlineVariant))
            SwitchCell("扭力计", tqText, tqColor, torquePage, Modifier.weight(1f).fillMaxSize(), onTorque)
        }
    }
}

/** 切换条可点内容的高度（不含导航栏内边距）—— 戴手套点得准 */
private val SWITCH_BAR_HEIGHT = 88.dp

@Composable
private fun SwitchCell(
    title: String,
    status: String,
    statusColor: Color,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = statusColor, shape = RoundedCornerShape(50), modifier = Modifier.size(14.dp)) {
            Box(Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 烙铁状态：连接状态 + 采集开关（两句话合起来才是现场要的信息） */
private fun ironStatus(conn: ConnState, running: Boolean): Pair<String, Color> = when (conn) {
    is ConnState.Connected -> (if (running) "已连接 · 采集中" else "已连接 · 未采集") to OkGreen
    is ConnState.Connecting -> "连接中…" to WifiBlue
    is ConnState.Reconnecting -> "重连中（第 ${conn.attempt} 次）" to WarnOrange
    is ConnState.Disconnected -> "未连接" to InvalidGrey
}

/** 扭力计状态：USB 链路 + 监听开关 */
private fun torqueStatus(link: TorqueLinkState, listen: Boolean): Pair<String, Color> = when (link) {
    is TorqueLinkState.Open -> "已连接 · 监听中" to OkGreen
    is TorqueLinkState.Opening -> "打开中…" to WifiBlue
    is TorqueLinkState.NeedPermission -> "等待授权" to WarnOrange
    is TorqueLinkState.NoDevice -> (if (listen) "未检测到 USB" else "已停止") to InvalidGrey
    is TorqueLinkState.Failed -> "连接失败" to WarnOrange
    is TorqueLinkState.Off -> "已停止" to InvalidGrey
}
