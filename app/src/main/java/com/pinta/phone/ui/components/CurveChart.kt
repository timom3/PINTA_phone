package com.pinta.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.core.ThrottleParams
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Orange
import com.pinta.phone.ui.theme.SurfaceContainer
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * 油门响应曲线。
 *
 * - 拖近折点：改 tpX / tpY（曲线形状）
 * - 拖其它区域：移动输入游标，实时看输出
 */
@Composable
fun ThrottleCurveChart(
    params: ThrottleParams,
    input: Float,
    onInputChange: (Float) -> Unit,
    onParamsChange: (ThrottleParams) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    var draggingHandle by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.width(34.dp).fillMaxHeight().padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabel("100%")
                AxisLabel("50%")
                AxisLabel("0%")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(SurfaceContainer)
                    .pointerInput(params.tpX, params.tpY, interactive) {
                        if (!interactive) return@pointerInput
                        detectDragGestures(
                            onDragStart = { pos ->
                                val handle = Offset(
                                    params.tpX * size.width,
                                    (1f - params.tpY) * size.height,
                                )
                                draggingHandle = (pos - handle).getDistance() < 48.dp.toPx()
                                if (!draggingHandle) {
                                    onInputChange((pos.x / size.width).coerceIn(0f, 1f))
                                }
                            },
                            onDragEnd = { draggingHandle = false },
                            onDragCancel = { draggingHandle = false },
                        ) { change, _ ->
                            change.consume()
                            val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                            val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            if (draggingHandle) {
                                onParamsChange(
                                    params.copy(
                                        tpX = nx.coerceIn(0.05f, 0.95f),
                                        tpY = ny.coerceIn(0.02f, 1f),
                                    ),
                                )
                            } else {
                                onInputChange(nx)
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color(0x14FFFFFF)
                    for (i in 0..4) {
                        val x = size.width * i / 4f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                        val y = size.height * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                    }

                    // 曲线
                    val samples = 120
                    val curve = Path()
                    for (i in 0..samples) {
                        val nx = i / samples.toFloat()
                        val ny = params.curve(nx)
                        val x = nx * size.width
                        val y = size.height - ny * size.height
                        if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
                    }
                    val area = Path().apply {
                        addPath(curve)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            listOf(Orange.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                    drawPath(
                        path = curve,
                        color = Orange,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // 输入游标
                    val cx = input * size.width
                    val cy = size.height - params.curve(input) * size.height
                    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
                    drawLine(
                        color = Color(0x88FFFFFF),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                    drawLine(
                        color = Color(0x55FFFFFF),
                        start = Offset(0f, cy),
                        end = Offset(cx, cy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                    drawCircle(color = Orange, radius = 3.5.dp.toPx(), center = Offset(cx, cy))

                    // 折点把手
                    val hx = params.tpX * size.width
                    val hy = size.height - params.tpY * size.height
                    val half = 6.dp.toPx()
                    drawRect(
                        color = TextPrimary,
                        topLeft = Offset(hx - half, hy - half),
                        size = Size(half * 2, half * 2),
                    )
                    drawRect(
                        color = Orange,
                        topLeft = Offset(hx - half, hy - half),
                        size = Size(half * 2, half * 2),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Spacer(Modifier.width(34.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabel("0")
                AxisLabel("50")
                AxisLabel("100")
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        fontFamily = Mono,
        fontSize = 9.sp,
        color = TextFaint,
    )
}
