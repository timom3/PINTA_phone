package com.pinta.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.LinkState
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.HairlineStrong
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mint
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Orange
import com.pinta.phone.ui.theme.SurfaceHigh
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import kotlin.math.roundToInt

@Composable
fun HairlineH(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

/** 小节标题：小字号 + 字距，作为信息分组的唯一视觉锚点。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = TextFaint,
            maxLines = 1,
        )
        trailing?.invoke()
    }
}

/** 基础卡片：靠色阶分层，只用一条 8% 的发丝边，不用阴影。 */
@Composable
fun PintaCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var base = modifier
        .fillMaxWidth()
        .background(SurfaceLow)
        .border(1.dp, Hairline)
    if (onClick != null) base = base.clickable { onClick() }
    Column(modifier = base.padding(padding), content = content)
}

/** 数值 + 单位，等宽字体，避免数字跳动时布局抖动。 */
@Composable
fun MetricText(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: TextUnit = 26.sp,
    color: Color = TextPrimary,
    unitSize: TextUnit = 11.sp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = value,
            fontFamily = Mono,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = color,
            maxLines = 1,
        )
        if (unit != null) {
            Text(
                text = unit,
                fontFamily = Mono,
                fontSize = unitSize,
                color = TextDim,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** 四轮输出条。0..1 映射到整条宽度。 */
@Composable
fun WheelBar(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = Orange,
) {
    val v = value.coerceIn(0f, 1f)
    Row(
        // 高度由调用方决定（仪表页按权重平分），这里不再写死，
        // 否则小屏（如 S24 的 360×780dp）最后两行会被裁掉。
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 12.sp,
            color = TextDim,
            modifier = Modifier.width(30.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(SurfaceHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(v)
                    .background(color),
            )
        }
        Text(
            text = "${(v * 100).roundToInt()}%",
            fontFamily = Mono,
            fontSize = 14.sp,
            color = TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

/** 迷你滚动曲线。 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        // 50% 参考线：让低幅值的曲线不至于"贴底看不出量级"
        drawLine(
            color = Color(0x10FFFFFF),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1f,
        )
        val step = w / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, raw ->
            val x = i * step
            val y = h - raw.coerceIn(0f, 1f) * (h - 2.dp.toPx()) - 1.dp.toPx()
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)),
            style = Fill,
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = Offset(w, h - values.last().coerceIn(0f, 1f) * (h - 2.dp.toPx()) - 1.dp.toPx()),
        )
    }
}

@Composable
fun PintaButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> SurfaceHigh
        primary -> Orange
        else -> SurfaceHigh
    }
    val fg = when {
        !enabled -> TextFaint
        primary -> Color.Black
        else -> Orange
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .background(bg)
            .border(1.dp, if (primary) Color.Transparent else HairlineStrong)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = fg,
        )
    }
}

@Composable
fun LinkPill(link: LinkState, onClick: () -> Unit) {
    val (dot, text) = when (link) {
        is LinkState.Connected -> Mint to link.name
        LinkState.Connecting -> Orange to "连接中"
        is LinkState.Reconnecting -> Orange to "重连中 ${link.attempt}"
        LinkState.Disconnected -> TextFaint to "未连接"
        is LinkState.Error -> Danger to "链路错误"
    }
    Row(
        modifier = Modifier
            .height(28.dp)
            .background(SurfaceHigh)
            .border(1.dp, Hairline)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(
            text = text,
            fontSize = 11.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
    }
}

/** 全局顶部栏：标题 / 可选返回 / 链路状态。 */
@Composable
fun PintaTopBar(
    title: String,
    link: LinkState,
    onLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(Ink).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        color = TextFaint,
                        maxLines = 1,
                    )
                }
            }
            LinkPill(link = link, onClick = onLinkClick)
        }
        HairlineH()
    }
}

/** 参数单元：标签 + 大号等宽数值 + 单位，点击进入键盘。 */
@Composable
fun ParamCell(
    label: String,
    value: String,
    unit: String,
    changed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceLow)
            .border(1.dp, if (changed) Orange.copy(alpha = 0.45f) else Hairline)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextDim,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (changed) {
                Box(Modifier.size(5.dp).background(Orange, CircleShape))
            }
        }
        Spacer(Modifier.height(5.dp))
        MetricText(
            value = value,
            unit = unit,
            size = 19.sp,
            color = if (changed) Orange else TextPrimary,
            unitSize = 10.sp,
        )
    }
}
