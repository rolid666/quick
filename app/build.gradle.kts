plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.quick.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quick.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 二维码：生成（192AF+ 配网/配置码）
    implementation("com.google.zxing:core:3.5.3")

    // 扭力计 USB 串口（FTDI 芯片，19200 8N2）—— 用系统自带 USB Host API，自写最小 FTDI 驱动。
    // 不用 usb-serial-for-android：该库只在 JitPack 上有，本机网络下不通（详见 docs §24）。
    // 驱动实现：com.quick.app.torque.usb

    // 后置摄像头扫码（设备信息录入）—— CameraX + zxing 解码，不依赖 Google Play 服务
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    testImplementation("junit:junit:4.13.2")

    // 单测里用真正的 org.json 实现 —— 单元测试跑的是 JVM，android.jar 里的 org.json 是空壳
    // （调用即 "not mocked" 异常）。配置导出/导入的正确性必须能真测（用户 2026-09-22 要求 7），
    // 所以把 org.json 挂到测试编译/运行路径上；**只影响单测，不进 APK**。
    testImplementation("org.json:json:20231013")
}
