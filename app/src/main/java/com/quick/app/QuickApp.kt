package com.quick.app

import android.app.Application
import com.quick.app.collect.MeasurementController
import com.quick.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用级单例容器：db / controller 全局唯一（采集状态跨 Activity 存活） */
class QuickApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var controller: MeasurementController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        controller = MeasurementController(this, db)
        appScope.launch {
            controller.init()  // 读缓存配置、恢复 pending、按需自动开始采集
        }
    }
}
