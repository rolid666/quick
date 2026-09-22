package com.quick.app.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quick.app.data.db.MeasurementRecord
import com.quick.app.net.channelText

val OkGreen = Color(0xFF2E7D32)
val NgRed = Color(0xFFC62828)
val InvalidGrey = Color(0xFF616161)
val WifiBlue = Color(0xFF1565C0)
val WarnOrange = Color(0xFFEF6C00)

/** 结果配色：OK 绿 / NG 红 / 无效 灰（仪器 0x23 明确给出 2=无效，不能按 NG 也不按 OK 展示） */
fun resultColor(rec: MeasurementRecord): Color = when {
    rec.isOk -> OkGreen
    rec.isInvalid -> InvalidGrey
    else -> NgRed
}

fun resultLabel(rec: MeasurementRecord): String = when {
    rec.isOk -> "OK"
    rec.isInvalid -> "无效"
    else -> "NG"
}

/**
 * 全屏结果弹卡 —— 挂在 **App 根节点**，任何页面（测量/记录/配置…）都能弹，
 * 且**不参与状态栏 inset**：状态栏与导航栏区域也铺满结果色，全屏无缝隙。
 *
 * 2026-09 起由 App() 持有并负责：弹卡出现即 [MeasurementController.hold] 暂停轮询，
 * 弹卡消失才继续读仪器。所以本组件只负责画，不含计时逻辑。
 */
@Composable
fun ResultOverlay(rec: MeasurementRecord, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().clickable { onDismiss() },
        color = resultColor(rec)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("测试完成", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(resultLabel(rec), fontSize = 150.sp,
                    color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                Text(tempText(rec.measuredTemp), fontSize = 90.sp, color = Color.White,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                rec.deviceInfo?.ifBlank { null }?.let {
                    Text("设备编号 $it", fontSize = 26.sp, color = Color.White, maxLines = 1)
                }
                Text(
                    listOfNotNull(
                        rec.lineName.ifBlank { null },
                        rec.modelName.ifBlank { null },
                        rec.stationName.ifBlank { null }
                    ).joinToString(" / ") + "　通道 ${channelText(rec.channel)}",
                    fontSize = 26.sp, color = Color.White
                )
                Text("设定 ${intTempText(rec.targetTemp)}　电压 ${voltText(rec.measuredVoltageMv)}　" +
                    "电阻 ${ohmText(rec.measuredResistanceOhm)}", fontSize = 26.sp, color = Color.White)
                Text("已自动保存到记录（点一下关闭）", fontSize = 26.sp, color = Color.White,
                    modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

/** 弹出结果卡时振动一下（车间噪音大，看屏幕不一定来得及） */
fun vibrate(ctx: Context) {
    runCatching {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(250)
        }
    }
}
