package com.quick.app.ui.torque

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.quick.app.ui.WarnOrange

/**
 * 扫码对话框（后置摄像头）—— 用户要求 4：设备信息的添加要能调用后置摄像头扫码。
 *
 * 实现取向：**CameraX + zxing 解码，不用 Google Play 服务（ML Kit）**。
 * 车间平板常常没有 GMS 也没有 Play 商店，依赖 ML Kit 的话「装上了但扫不了」；
 * zxing 已有（配置二维码就是它生成的），解码放在本地一个线程里，完全离线。
 *
 * 识别到内容就立刻回传并关闭（不是让操作员再点一次确定）；
 * 另外**始终提供手动输入**：没摄像头的平板、镜头脏了、码磨花了，现场都得有退路。
 *
 * @param hint 相机取景框下面那句提示（告诉操作员该扫哪儿）
 * @param onResult 识别到的原文；null = 用户取消
 */
@Composable
fun ScanDialog(
    title: String,
    hint: String,
    onResult: (String?) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    // 进对话框就申请相机权限（首次会弹系统框；已拒绝过则不弹，直接走下面的提示与手输）
    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var manual by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val handled = remember { AtomicBoolean(false) }

    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (granted) {
                PreviewBox(
                    cameraError = cameraError,
                    onError = { cameraError = it },
                    onDecoded = { text ->
                        if (handled.compareAndSet(false, true)) onResult(text)
                    },
                    lifecycleOwner = lifecycleOwner
                )
            } else {
                Text(
                    "没有相机权限，无法扫码。可以点「允许」重试，或直接手动输入。",
                    style = MaterialTheme.typography.bodyMedium, color = WarnOrange
                )
                TextButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("允许使用摄像头")
                }
            }
            cameraError?.let {
                Text("相机打不开：$it（可用下方手动输入）",
                    style = MaterialTheme.typography.bodySmall, color = WarnOrange)
            }

            // 手动输入兜底：永远留着，现场比什么都重要
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("手动输入（扫码不出来时用）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onResult(null) }) { Text("取消") }
                Button(
                    onClick = { onResult(manual.trim()) },
                    enabled = manual.isNotBlank()
                ) { Text("用这个") }
            }
        }
    }
}

@Composable
private fun PreviewBox(
    cameraError: String?,
    onError: (String) -> Unit,
    onDecoded: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val ctx = LocalContext.current
    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val providerFuture = remember { ProcessCameraProvider.getInstance(ctx) }
    val reader = remember { newReader() }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        val bind = Runnable {
            try {
                val p = providerFuture.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    // 只保留最新一帧：解码跟不上时不排队（排队只会让画面越来越延迟）
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val found: String? = try {
                        decodeFrame(proxy, reader)
                    } catch (_: Exception) {
                        null
                    } finally {
                        proxy.close()
                    }
                    if (found != null && found.isNotBlank()) {
                        ContextCompat.getMainExecutor(ctx).execute { onDecoded(found) }
                    }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                ContextCompat.getMainExecutor(ctx).execute {
                    onError(e.message ?: e.javaClass.simpleName)
                }
            }
        }
        providerFuture.addListener(bind, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { provider?.unbindAll() }
            runCatching { executor.shutdown() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(300.dp)
    )
}

private fun newReader(): MultiFormatReader = MultiFormatReader().apply {
    setHints(
        mapOf(
            // 二维码是主用（本应用的配置码就是 QR）；常见一维码一并认，
            // 设备铭牌上贴哪种都有可能
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13, BarcodeFormat.ITF, BarcodeFormat.CODABAR
            ),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
}

/**
 * 一帧 → 文本。取 Y 平面（灰度）做 zxing 的亮度源；
 * 再按相机给出旋转角把它摆正 —— 竖屏时相机出的是横图，
 * **二维码**靠定位图形还能认出来，**一维码**不摆正就永远认不出。
 */
private fun decodeFrame(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val plane = proxy.planes.firstOrNull() ?: return null
    val buf = plane.buffer
    val rowStride = plane.rowStride
    val height = proxy.height
    if (rowStride <= 0 || height <= 0) return null
    val data = ByteArray(rowStride * height)
    buf.rewind()
    val n = minOf(data.size, buf.remaining())
    if (n <= 0) return null
    buf.get(data, 0, n)

    val src = rotate(data, rowStride, height, proxy.imageInfo.rotationDegrees)
    val source = PlanarYUVLuminanceSource(
        src.data, src.rowStride, src.height, 0, 0, src.width, src.height, false
    )
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        reader.reset()          // 找不到码：清掉内部状态，避免下一帧受上一帧影响
        null
    } catch (_: Exception) {
        reader.reset()
        null
    }
}

private class Rotated(val data: ByteArray, val rowStride: Int, val width: Int, val height: Int)

/** 把 rowStride × height 的灰度图按角度摆正（90/180/270；0 直接返回） */
private fun rotate(src: ByteArray, rowStride: Int, height: Int, degrees: Int): Rotated {
    val deg = ((degrees % 360) + 360) % 360
    if (deg == 0) return Rotated(src, rowStride, rowStride, height)
    return when (deg) {
        90 -> {                       // 顺时针
            val dw = height
            val out = ByteArray(dw * rowStride)
            for (y in 0 until height) {
                for (x in 0 until rowStride) {
                    out[x * dw + (height - 1 - y)] = src[y * rowStride + x]
                }
            }
            Rotated(out, dw, dw, rowStride)
        }
        180 -> {
            val out = ByteArray(src.size)
            for (y in 0 until height) {
                for (x in 0 until rowStride) {
                    out[(height - 1 - y) * rowStride + (rowStride - 1 - x)] = src[y * rowStride + x]
                }
            }
            Rotated(out, rowStride, rowStride, height)
        }
        else -> {                     // 270
            val dw = height
            val out = ByteArray(dw * rowStride)
            for (y in 0 until height) {
                for (x in 0 until rowStride) {
                    out[(rowStride - 1 - x) * dw + y] = src[y * rowStride + x]
                }
            }
            Rotated(out, dw, dw, rowStride)
        }
    }
}
