package com.pinta.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pinta.phone.core.MotorHistory
import com.pinta.phone.core.MotorSample
import com.pinta.phone.core.autoAxisRange
import com.pinta.phone.core.f3
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.PhosphorHot
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val clockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
private val WheelLabels = listOf("FL", "FR", "RL", "RR")
private val PanelIndex = listOf("03", "04", "05", "06")

/** 采样率按 20Hz 折算秒数（dyn 轮询固定 40ms 一拍）。 */
private const val SAMPLE_HZ = 20f

/** 弹窗内的电机选择：只存在内存里（app 重启就回到"点哪个显示哪个"）。 */
private object HistorySelection {
    var wheels: Set<Int> = emptySet()
}

/**
 * 电机卡片的长按手势：按住 [holdMs] 才触发（默认 2s），避免误触。
 * 提前松手 / 手指移出控件都会取消；计时跑在组合作用域的协程里（指针手势内部是受限作用域，
 * 不能直接用 withTimeout）。
 */
fun Modifier.longPressAction(holdMs: Long = 2_000L, onLongPress: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(holdMs) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val job = scope.launch {
                kotlinx.coroutines.delay(holdMs)
                onLongPress()
            }
            waitForUpOrCancellation()
            job.cancel()
        }
    }
}

/**
 * 03–06 电机卡的详情弹窗：180 秒历史 + 居中竖光标 + 拖动/缩放。
 * 可以同时勾选多个电机，按 FL→FR→RL→RR 从上到下纵向排列。
 * 打开期间 dyn 停发（数据定格），所以这里按样本下标映射，不用时间戳。
 */
@Composable
fun MotorHistoryDialog(
    initialWheel: Int,
    onDismiss: () -> Unit,
) {
    // 同一进程里记住上次的选择；点了一个"不在记忆里"的卡片就只显示它
    var selected by remember(initialWheel) {
        val remembered = HistorySelection.wheels
        val start = if (initialWheel in remembered) remembered else setOf(initialWheel)
        mutableStateOf(start)
    }
    val wheels = remember(selected) { selected.sorted() }

    val samples = remember { MotorHistory.snapshot() }
    val count = samples.size

    // Y 轴量程：弹窗自己独立算（不再写全局 MotorAxis），按整段历史的最大值分档，
    // 打开时定格 —— 拖动/缩放时量程不跳变，四张图始终同一尺度
    val yRange = remember(samples) {
        var maxAbs = 0f
        for (s in samples) {
            for (w in 0 until 4) {
                maxAbs = maxOf(
                    maxAbs,
                    kotlin.math.abs(s.iqPu.getOrElse(w) { 0f }),
                    kotlin.math.abs(s.ibusPu.getOrElse(w) { 0f }),
                    kotlin.math.abs(s.omegaPu.getOrElse(w) { 0f }),
                )
            }
        }
        autoAxisRange(maxAbs)
    }

    var visible by remember(count) {
        mutableStateOf(if (count <= 0) 40f else minOf(count, 400).toFloat())
    }
    var endIndex by remember(count) { mutableStateOf((count - 1).coerceAtLeast(0).toFloat()) }

    fun maxVisible() = minOf(count, MotorHistory.CAPACITY).toFloat().coerceAtLeast(2f)

    fun clampEnd(end: Float, vis: Float): Float {
        if (count <= 1) return 0f
        val maxEnd = (count - 1).toFloat()
        val minEnd = (vis - 1f).coerceAtMost(maxEnd)
        return end.coerceIn(minEnd, maxEnd)
    }

    val cursorIndex =
        if (count == 0) 0 else (endIndex - visible / 2f).roundToInt().coerceIn(0, count - 1)
    val cursor = samples.getOrNull(cursorIndex)
    val newestT = if (count > 0) samples[count - 1].t else 0L
    val dtSec = if (cursor != null) (cursor.t - newestT) / 1000f else 0f
    val spanSec = visible / SAMPLE_HZ
    val totalSec = count / SAMPLE_HZ

    // 选得越多，面板越高；单个时不用占满整屏
    val panelFraction = when (wheels.size) {
        1 -> 0.56f
        2 -> 0.74f
        else -> 0.92f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .fillMaxHeight(panelFraction)
                .background(Ink)
                .border(1.dp, Hairline)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MOTOR", fontFamily = Mono, fontSize = 10.sp, color = TextDim)
                Text(" // ", fontFamily = Mono, fontSize = 10.sp, color = TextFaint)
                Text(
                    text = "HISTORY",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = Phosphor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "%.1fs / %.0fs", spanSec, totalSec),
                    fontFamily = Mono,
                    fontSize = 7.sp,
                    color = TextFaint,
                )
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

            // 电机选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelLabels.forEachIndexed { index, label ->
                    val on = index in selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .background(if (on) Phosphor else Color.Transparent)
                            .border(1.dp, if (on) Phosphor else Hairline)
                            .clickable {
                                val next = selected.toMutableSet()
                                if (on) {
                                    if (next.size > 1) next.remove(index) // 至少留一个
                                } else {
                                    next.add(index)
                                }
                                selected = next
                                HistorySelection.wheels = next
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Ink else TextDim,
                        )
                    }
                }
            }

            // 光标时刻
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (cursor != null) clockFormat.format(Date(cursor.t)) else "--:--:--",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (cursor != null) String.format(Locale.US, "%+.1fs", dtSec) else "",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = TextFaint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "双指缩放 · 单指拖动 · 光标居中",
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    color = TextFaint,
                    maxLines = 1,
                )
            }

            // 波形（多选时纵向排列）
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (count < 2) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "NO HISTORY YET",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = TextFaint,
                        )
                    }
                } else {
                    wheels.forEach { w ->
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            WheelReadout(w, cursor)
                            HistoryChart(
                                samples = samples,
                                wheelIndex = w,
                                visible = visible,
                                endIndex = endIndex,
                                range = yRange,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                gestures = {
                                    detectTransformGestures { _, pan, zoomChange, _ ->
                                        if (zoomChange != 1f) {
                                            val v = (visible / zoomChange)
                                                .coerceIn(20f, maxVisible())
                                            visible = v
                                            endIndex = clampEnd(endIndex, v)
                                        }
                                        if (pan.x != 0f) {
                                            endIndex = clampEnd(
                                                endIndex - pan.x / size.width * visible,
                                                visible,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 时间轴
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%.1fs", -spanSec / 2f),
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    color = TextFaint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(Locale.US, "窗口 %.1fs · 共 %.0fs", spanSec, totalSec),
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    color = TextFaint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(Locale.US, "%.1fs", spanSec / 2f),
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    color = TextFaint,
                )
            }

            // 工具条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton("−") {
                    val v = (visible * 1.6f).coerceIn(20f, maxVisible())
                    visible = v
                    endIndex = clampEnd(endIndex, v)
                }
                ToolButton("＋") {
                    val v = (visible / 1.6f).coerceIn(20f, maxVisible())
                    visible = v
                    endIndex = clampEnd(endIndex, v)
                }
                ToolButton("全部") {
                    val v = maxVisible()
                    visible = v
                    endIndex = clampEnd(endIndex, v)
                }
                ToolButton("最新") {
                    endIndex = (count - 1).toFloat()
                    endIndex = clampEnd(endIndex, visible)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "数据仅存内存",
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    color = TextFaint,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WheelReadout(wheelIndex: Int, sample: MotorSample?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = PanelIndex.getOrElse(wheelIndex) { "--" },
            fontFamily = Mono,
            fontSize = 6.sp,
            color = TextFaint,
        )
        Text(" // ", fontFamily = Mono, fontSize = 6.sp, color = TextFaint)
        Text(
            text = WheelLabels.getOrElse(wheelIndex) { "?" },
            fontFamily = Mono,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = Phosphor,
        )
        Spacer(Modifier.width(6.dp))
        Readout("IQ", sample?.iqPu?.getOrElse(wheelIndex) { 0f }?.f3() ?: "--", Phosphor)
        Spacer(Modifier.width(6.dp))
        Readout("IB", sample?.ibusPu?.getOrElse(wheelIndex) { 0f }?.f3() ?: "--", Amber)
        Spacer(Modifier.width(6.dp))
        Readout("ω", sample?.omegaPu?.getOrElse(wheelIndex) { 0f }?.f3() ?: "--", TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (sample != null) {
                "C${sample.ctrlC.getOrElse(wheelIndex) { -50 }}° M${sample.motorC.getOrElse(wheelIndex) { -50 }}°"
            } else {
                ""
            },
            fontFamily = Mono,
            fontSize = 6.sp,
            color = TextDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).height(5.dp).background(color))
        Spacer(Modifier.width(2.dp))
        Text(label, fontFamily = Mono, fontSize = 6.sp, color = TextFaint)
        Spacer(Modifier.width(2.dp))
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = color)
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .border(1.dp, Hairline)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = TextDim)
    }
}

@Composable
private fun HistoryChart(
    samples: List<MotorSample>,
    wheelIndex: Int,
    visible: Float,
    endIndex: Float,
    range: Float = 1f,
    modifier: Modifier = Modifier,
    gestures: (suspend PointerInputScope.() -> Unit),
) {
    Canvas(
        modifier = modifier
            .background(SurfaceLow.copy(alpha = 0.15f))
            .border(1.dp, Hairline)
            .pointerInput(samples.size) { gestures() },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        for (i in 0..2) {
            val x = w * i / 2f
            val y = h * i / 2f
            drawLine(Hairline, Offset(x, 0f), Offset(x, h), 1f)
            drawLine(Hairline, Offset(0f, y), Offset(w, y), 1f)
        }
        drawLine(
            color = Phosphor.copy(alpha = 0.28f),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1.5f,
        )

        val pad = 2.dp.toPx()
        val drawH = (h - pad * 2f).coerceAtLeast(1f)
        val startIdx = endIndex - visible
        val lastIdx = samples.size - 1

        fun plot(pick: (MotorSample) -> Float, color: Color) {
            val path = Path()
            var started = false
            var x = 0f
            while (x <= w) {
                val idx = (startIdx + x / w * visible).roundToInt()
                if (idx in 0..lastIdx) {
                    val v = pick(samples[idx]).coerceIn(-range, range)
                    val y = pad + (1f - (v / range + 1f) / 2f) * drawH
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                x += 1f
            }
            if (started) drawPath(path, color, style = Stroke(width = 1.2.dp.toPx()))
        }

        plot({ it.iqPu.getOrElse(wheelIndex) { 0f } }, Phosphor)
        plot({ it.ibusPu.getOrElse(wheelIndex) { 0f } }, Amber)
        plot({ it.omegaPu.getOrElse(wheelIndex) { 0f } }, TextPrimary)

        drawLine(
            color = PhosphorHot,
            start = Offset(w / 2f, 0f),
            end = Offset(w / 2f, h),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
