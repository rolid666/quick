package com.quick.app

import android.app.Application
import com.quick.app.collect.MeasurementController
import com.quick.app.data.db.AppDatabase
import com.quick.app.torque.TorqueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级单例容器：db / controller（烙铁）/ torque（扭力计）全局唯一。
 *
 * 两个控制器**同时活着、各自独立**（2026-09-21 双测量界面）：
 * - [controller] 走 Wi-Fi 的 Modbus TCP，读 192AF+ 测温仪；
 * - [torque] 走 USB 串口，读扭力计。
 * 界面在两者之间怎么切都不影响这里的任何循环 —— 这正是用户那条硬要求
 * 「切换界面时，烙铁的通信不能断」的实现方式：**通信根本不在界面里**。
 */
class QuickApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var controller: MeasurementController
        private set
    lateinit var torque: TorqueController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        controller = MeasurementController(this, db)
        torque = TorqueController(this, db)
        appScope.launch {
            controller.init()  // 读缓存配置、恢复 pending、按需自动开始采集
            torque.init()      // 同上：恢复扭力计 pending、按需自动开始监听 USB
        }
    }
}
