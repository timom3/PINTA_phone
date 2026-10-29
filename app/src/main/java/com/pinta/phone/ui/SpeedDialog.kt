package com.pinta.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pinta.phone.core.SpeedConfig
import com.pinta.phone.core.f1
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.components.PintaButton
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import java.util.Locale

private val WheelNames = listOf("FL", "FR", "RL", "RR")

/**
 * 车速标定弹窗：勾选参与计算的轮子 + ωPU→RPM + 轮周长。
 * 风格与座舱一致：0 圆角、等宽字体、色阶分层。
 */
@Composable
fun SpeedSettingsDialog(
    config: SpeedConfig,
    omegaPu: List<Float>,
    onDismiss: () -> Unit,
    onApply: (SpeedConfig) -> Unit,
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var rpmText by remember { mutableStateOf(String.format(Locale.US, "%.0f", config.rpmPerPu)) }
    var circText by remember { mutableStateOf("${config.wheelCircCm}") }

    val rpm = rpmText.toFloatOrNull() ?: 0f
    val circ = circText.toIntOrNull() ?: 0
    val draft = SpeedConfig(enabled = enabled, rpmPerPu = rpm, wheelCircCm = circ)
    val preview = draft.speedKph(omegaPu)
    val valid = rpm > 0f && circ > 0 && enabled.any { it }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .border(1.dp, Hairline)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SPEED CALIB",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = Phosphor,
                )
                Spacer(Modifier.width(6.dp))
                Text("车速标定", fontSize = 8.sp, color = TextFaint)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "CLOSE",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = TextDim,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 4.dp),
                )
            }
            HairlineH()

            // ① 生效轮子
            Text("生效轮子", fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelNames.forEachIndexed { index, name ->
                    val on = enabled.getOrElse(index) { false }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(if (on) Phosphor else Color.Transparent)
                            .border(1.dp, if (on) Phosphor else Hairline)
                            .clickable {
                                enabled = enabled.toMutableList().also { it[index] = !on }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Ink else TextDim,
                        )
                    }
                }
            }

            // ② 两个标定值
            NumberRow(
                label = "ωPU → RPM",
                value = rpmText,
                hint = "omega_pu=1.0 时的轮上转速；有减速器按减速比折算",
                onValueChange = { rpmText = it.filter(Char::isDigit).take(6) },
            )
            NumberRow(
                label = "轮周长 cm",
                value = circText,
                hint = "整圈周长，精度 1cm",
                onValueChange = { circText = it.filter(Char::isDigit).take(4) },
            )

            // ③ 说明与预览
            Text(
                text = "车速取勾选轮子中转速最低的一个：ωPU × RPM 得轮速，" +
                    "再乘轮周长换成 km/h。双驱时只勾实际驱动的两个轮，否则没转的轮会把速度压到 0。",
                fontFamily = Mono,
                fontSize = 6.5.sp,
                color = TextFaint,
                lineHeight = 9.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前 ≈", fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (preview != null) "${preview.f1()} km/h" else "--",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (valid) TextPrimary else Amber,
                )
            }

            HairlineH()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PintaButton(
                    label = "取消",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                PintaButton(
                    label = "保存",
                    primary = true,
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = { onApply(draft) },
                )
            }
        }
    }
}

@Composable
private fun NumberRow(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = Mono, fontSize = 8.sp, color = TextDim)
            Text(hint, fontFamily = Mono, fontSize = 6.sp, color = TextFaint, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .width(72.dp)
                .height(26.dp)
                .background(SurfaceLow)
                .border(1.dp, Hairline)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = TextPrimary),
            cursorBrush = SolidColor(Phosphor),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("0", fontFamily = Mono, fontSize = 10.sp, color = TextFaint)
                    }
                    inner()
                }
            },
        )
    }
}
