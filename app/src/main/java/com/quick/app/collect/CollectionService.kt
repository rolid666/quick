package com.quick.app.collect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.quick.app.QuickApp
import com.quick.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 采集前台服务：只负责「保活 + 状态通知」。
 * 真正的轮询/入库逻辑全在 [MeasurementController]（App 级单例），
 * 因此 Activity 销毁、本服务被系统重启都不会影响采集连续性。
 */
class CollectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notifJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as QuickApp).controller
        startForegroundCompat()
        if (controller.isRunning && notifJob == null) {
            // 服务被系统 START_STICKY 重启时，确保通知继续跟随状态
            notifJob = scope.launch {
                controller.ui.collectLatest { render(it) }
            }
        }
        return START_STICKY
    }

    private fun render(s: MeasureUiState) {
        val nm = getSystemService(NotificationManager::class.java)
        val text = when (val c = s.conn) {
            is ConnState.Connected -> {
                val t = s.snapshot?.liveTempC?.let { "%.1f℃".format(it) } ?: "--"
                val line = if (s.snapshot?.hasResult == true) " ★有结果待收" else ""
                "已连接 ${c.ip}:${c.port}｜当前 ${t}$line"
            }
            is ConnState.Connecting -> "连接中…"
            is ConnState.Reconnecting -> "重连中(第${c.attempt}次)…"
            is ConnState.Disconnected -> "未连接"
        }
        nm.notify(NOTIF_ID, buildNotification("测量采集 ${if (s.running) "运行中" else "已停止"}", text))
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_collect)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun startForegroundCompat() {
        val notif = buildNotification("测量采集", "正在初始化…")
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ServiceCompat.startForeground(this, NOTIF_ID, notif, 0)
        }
    }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "测量采集", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onDestroy() {
        notifJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "collection"
        private const val NOTIF_ID = 1001
    }
}
