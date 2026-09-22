package com.quick.app.ui.torque

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
import com.quick.app.data.db.TorqueRecord
import com.quick.app.data.db.torqueValueText
import com.quick.app.ui.NgRed
import com.quick.app.ui.OkGreen

/**
 * 扭力计「一组三笔已自动保存」全屏弹卡。
 *
 * 与烙铁的结果弹卡一样挂在 **App 根节点**，任何页面都能弹 ——
 * 因为「到数就弹」这件事不该取决于操作员当时在看哪一页。
 * 同样不参与状态栏 inset：整屏铺满，车间里一眼就能看见。
 *
 * 底色 = **判定结果**（用户要求 6：平均值在所选范围内 OK，否则 NG）：
 * OK 绿、NG 红，中间大大的平均值 + 三笔明细 —— 现场一眼定生死，不用去翻记录页。
 * 单位写在数字后面（`kgf*cm`），不再有「单位待确认」这种话。
 */
@Composable
fun TorqueOverlay(rec: TorqueRecord, onDismiss: () -> Unit) {
    val ok = rec.isOk
    Surface(
        modifier = Modifier.fillMaxSize().clickable { onDismiss() },
        color = if (ok == false) NgRed else OkGreen
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("3 次测量完成（已自动保存）", fontSize = 32.sp,
                    color = Color.White, fontWeight = FontWeight.Medium)
                Text(rec.averageText, fontSize = 130.sp,
                    color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                Text("平均值　${rec.unitText}", fontSize = 26.sp, color = Color.White)
                if (ok != null) {
                    Text(if (ok) "OK" else "NG", fontSize = 72.sp,
                        color = Color.White, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "本组：" + listOf(rec.v1Text, rec.v2Text, rec.v3Text).joinToString("　"),
                    fontSize = 30.sp, color = Color.White,
                    modifier = Modifier.padding(top = 12.dp)
                )
                rec.rangeMin?.let { min ->
                    rec.rangeMax?.let { max ->
                        Text(
                            "范围 ${torqueValueText(min)} ~ ${torqueValueText(max)} ${rec.unitText}",
                            fontSize = 24.sp, color = Color.White,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        rec.lineName.ifBlank { null },
                        rec.modelName.ifBlank { null },
                        rec.rangeName.ifBlank { null }
                    ).joinToString(" / ") +
                        (rec.deviceName.ifBlank { null }?.let { "　设备 $it" } ?: ""),
                    fontSize = 24.sp, color = Color.White,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text("点一下关闭", fontSize = 22.sp, color = Color.White,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
