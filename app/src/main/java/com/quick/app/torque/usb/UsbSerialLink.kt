package com.quick.app.torque.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * USB 串口链路：**设备发现 + 权限申请 + 插拔通知**（不含协议、不含解析）。
 *
 * 权限这件事在 Android 上必须走「动态注册接收器 + PendingIntent」那一套：
 * 系统弹的授权框结果是以广播形式回来的，收到广播才知道有没有拿到。
 * 授权结果**只对本次插入有效** —— 拔掉再插要重新授权，所以授权请求写在这里、
 * 由控制器的重连循环按需调用（它每次连之前都会先问一遍 hasPermission）。
 *
 * 插拔广播只用来「叫醒」读循环（否则最多要等一个轮询间隔才反应过来），
 * 真正的状态判断仍然是每轮 `findDevice()` 实查 —— 不依赖广播不丢事件，
 * 这一点很关键：USB 广播在部分定制系统上并不可靠。
 */
class UsbSerialLink(private val ctx: Context) {

    private val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    /** 自定义权限动作（必须是本应用私有动作，不能用系统的，避免别的应用伪造授权结果） */
    private val permAction = "${ctx.packageName}.usb.PERMISSION"

    @Volatile private var waiter: CompletableDeferred<Boolean>? = null

    /** 插拔时回调：让控制器立刻重新检查设备，而不是等下一个轮询周期 */
    @Volatile var onDeviceChanged: (() -> Unit)? = null

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                permAction -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java
                    )
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    waiter?.complete(granted && device != null && mgr.hasPermission(device))
                    waiter = null
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> onDeviceChanged?.invoke()
            }
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(permAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // NOT_EXPORTED：只收系统与本应用发的广播（权限结果就是系统发的，收得到）
        runCatching {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    /** 系统里所有 FTDI 设备（诊断页列出来看 —— 有时问题是「插了但不是 FTDI」） */
    fun devices(): List<UsbDevice> = FtdiUsb.devices(mgr)

    fun findDevice(): UsbDevice? = FtdiUsb.findDevice(mgr)

    fun hasPermission(device: UsbDevice): Boolean = mgr.hasPermission(device)

    /**
     * 申请权限并等结果。**超时按失败处理**（用户没点、系统没弹、接收器没收到都归这类），
     * 由控制器的重连循环过一会儿再试 —— 现场表现就是「等一会儿自己好了」。
     */
    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (mgr.hasPermission(device)) return true
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(permAction).setPackage(ctx.packageName)
        val pi = PendingIntent.getBroadcast(ctx, 0, intent, flags)
        val deferred = CompletableDeferred<Boolean>()
        waiter = deferred
        return try {
            mgr.requestPermission(device, pi)
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() } ?: false
        } catch (_: Exception) {
            false
        } finally {
            if (waiter === deferred) waiter = null
        }
    }

    /** 打开设备连接（需已有权限）；失败返回 null */
    fun openConnection(device: UsbDevice): UsbDeviceConnection? =
        runCatching { mgr.openDevice(device) }.getOrNull()

    companion object {
        /** 授权框的等待上限：现场是人在点，给足 15 秒 */
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
