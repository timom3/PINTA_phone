package com.pinta.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowLeft
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.outlined.DiscFull
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.Ble
import com.pinta.phone.ble.Ccu
import com.pinta.phone.ble.LinkState
import com.pinta.phone.core.Telemetry
import com.pinta.phone.core.f0
import com.pinta.phone.core.SpeedSettings
import com.pinta.phone.core.f1
import com.pinta.phone.core.f3
import com.pinta.phone.core.pct
import com.pinta.phone.core.rememberDemoTelemetry
import com.pinta.phone.core.rememberTrace
import com.pinta.phone.core.signed
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.PhosphorDim
import com.pinta.phone.ui.theme.PhosphorHot
import com.pinta.phone.ui.theme.SurfaceHigh
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.roundToInt

private val WheelLabels = listOf("FL", "FR", "RL", "RR")

/**
 * 仪表：一屏工程遥测，而不是四张大字卡片。
 *
 * 结构从 eDEX-UI / btop 借来：固定顶栏、上下文条、页头、7 个密度模块。
 * 字号从 v0.1 的 38sp hero 压到 26sp；层级更多靠对齐、亮度和框线。
 */
@Composable
fun DashScreen(link: LinkState) {
    val demo by rememberDemoTelemetry()
    // 01 DRIVE / 02 IO 走 packIU_IO（4Hz）；四电机走 packIU_dynFB（≈30Hz）；
    // 车速、总电流/功率还是演示数据
    val io by Ccu.io.collectAsState()
    val dyn by Ccu.dyn.collectAsState()

    // dyn_fb 到手就把四电机换成真实值（一律用 pu，不换 SI）；未收到时回落到演示数据
    val dynFrame = dyn
    val t = if (dynFrame != null) demo.copy(
        motorIq = dynFrame.wheels.map { it.iqPu },
        motorIbus = dynFrame.wheels.map { it.ibusPu },
        motorOmega = dynFrame.wheels.map { it.omegaPu },
        controllerTemps = dynFrame.wheels.map { it.ctrlC.toFloat() },
        wheelTemps = dynFrame.wheels.map { it.motorC.toFloat() },
        motorFaultCodes = dynFrame.wheels.map { it.fault },
    ) else demo.copy(
        // 演示数据是 A / rpm，这里一并折成 pu，保证两种状态下的显示和曲线口径一致
        motorIq = demo.motorIq.map { it / 135f },
        motorIbus = demo.motorIbus.map { it / 40f },
        motorOmega = demo.motorOmega.map { it / 2000f },
    )

    // tick 用帧时间戳：数值不变也照常推进，曲线不会"一卡一卡"
    val tick = dynFrame?.at
    val iq0 = t.motorIq.getOrElse(0) { 0f }
    val iq1 = t.motorIq.getOrElse(1) { 0f }
    val iq2 = t.motorIq.getOrElse(2) { 0f }
    val iq3 = t.motorIq.getOrElse(3) { 0f }
    val ib0 = t.motorIbus.getOrElse(0) { 0f }
    val ib1 = t.motorIbus.getOrElse(1) { 0f }
    val ib2 = t.motorIbus.getOrElse(2) { 0f }
    val ib3 = t.motorIbus.getOrElse(3) { 0f }
    val om0 = t.motorOmega.getOrElse(0) { 0f }
    val om1 = t.motorOmega.getOrElse(1) { 0f }
    val om2 = t.motorOmega.getOrElse(2) { 0f }
    val om3 = t.motorOmega.getOrElse(3) { 0f }

    val iqTraces = listOf(
        rememberTrace(iq0, tick ?: iq0),
        rememberTrace(iq1, tick ?: iq1),
        rememberTrace(iq2, tick ?: iq2),
        rememberTrace(iq3, tick ?: iq3),
    )
    val ibusTraces = listOf(
        rememberTrace(ib0, tick ?: ib0),
        rememberTrace(ib1, tick ?: ib1),
        rememberTrace(ib2, tick ?: ib2),
        rememberTrace(ib3, tick ?: ib3),
    )
    val omegaTraces = listOf(
        rememberTrace(om0, tick ?: om0),
        rememberTrace(om1, tick ?: om1),
        rememberTrace(om2, tick ?: om2),
        rememberTrace(om3, tick ?: om3),
    )

    // 车速：dyn_fb 的 ω 取勾选轮子里最慢的一个，按标定的 RPM/pu 与轮周长换算
    val speedConfig by SpeedSettings.config.collectAsState()
    val omegaPu = dynFrame?.wheels?.map { it.omegaPu } ?: emptyList()
    val speedKph = speedConfig.speedKph(omegaPu)
    var showSpeedDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em)) {
        Box(modifier = Modifier.fillMaxSize().background(Ink)) {
            CrtBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // 03–07 is now a 2×2 motor scope matrix. The grid takes the
                // remaining height; only very short screens scroll.
                val compact = maxHeight < 630.dp
                val needScroll = maxHeight < 570.dp || t.faultCode != 0
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (needScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DrivePanel(
                        t = t,
                        io = io,
                        speedKph = speedKph,
                        onSpeedClick = { showSpeedDialog = true },
                        modifier = Modifier.fillMaxWidth().height(94.dp),
                    )

                    IoPanel(
                        t = t,
                        io = io,
                        modifier = Modifier.fillMaxWidth().height(if (compact) 140.dp else 146.dp),
                    )

                    MotorGrid(
                        t = t,
                        iqTraces = iqTraces,
                        ibusTraces = ibusTraces,
                        omegaTraces = omegaTraces,
                        modifier = if (needScroll) Modifier.fillMaxWidth().height(300.dp)
                        else Modifier.weight(1f),
                    )

                    if (t.faultCode != 0) {
                        FaultBanner(t.faultCode)
                    }
                    EventStrip(link)
                }
            }
        }
        }

        if (showSpeedDialog) {
            SpeedSettingsDialog(
                config = speedConfig,
                omegaPu = omegaPu,
                onDismiss = { showSpeedDialog = false },
                onApply = {
                    SpeedSettings.update(it)
                    showSpeedDialog = false
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

@Composable
private fun DashHeader(
    link: LinkState,
    demo: Boolean,
    faultCode: Int,
    onLinkClick: () -> Unit,
) {
    val (linkText, linkColor) = when (link) {
        is LinkState.Connected -> "LINK 2M" to Phosphor
        LinkState.Connecting -> "LINK WAIT" to Amber
        is LinkState.Reconnecting -> "LINK RETRY" to Amber
        LinkState.Disconnected -> "NO LINK" to TextFaint
        is LinkState.Error -> "LINK LOST" to Danger
    }
    val connected = link is LinkState.Connected
    val err = if (faultCode == 0) "ERR 00" else "ERR %02X".format(faultCode)
    val context = if (connected) "PHY 2M · MTU 247 · CAN 4/4 · $err"
    else if (demo) "PHY -- · MTU -- · CAN 4/4 · $err"
    else "PHY -- · MTU -- · CAN -- · $err"

    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PINTA",
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Phosphor,
            )
            Text(
                text = " // FIELD CONSOLE",
                fontFamily = Mono,
                fontSize = 8.sp,
                letterSpacing = 0.4.sp,
                color = TextFaint,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onLinkClick)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "[$linkText]",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = linkColor,
                    maxLines = 1,
                )
            }
        }
        HairlineH()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("00", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(" // ", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(
                text = "COCKPIT",
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = Phosphor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = context,
                fontFamily = Mono,
                fontSize = 8.sp,
                color = TextFaint,
                maxLines = 1,
            )
            if (demo) {
                Spacer(Modifier.width(4.dp))
                Token("SIM", Amber)
            }
        }
        HairlineH()
    }
}

@Composable
private fun Token(text: String, color: Color) {
    Text(
        text = "[$text]",
        fontFamily = Mono,
        fontSize = 8.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun HairlineH() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

// ---------------------------------------------------------------------------
// Modules
// ---------------------------------------------------------------------------

@Composable
private fun DrivePanel(
    t: Telemetry,
    io: Ccu.IuIo?,
    speedKph: Float?,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
) {
    // THR / STR 直接用 packIU_IO 的 pu 值（和 02 IO 显示的是同一个数）
    val throttlePu = (io?.throttlePu ?: t.throttle).coerceIn(0f, 1f)
    val steerPu = (io?.steeringPu ?: (t.steerDeg / 30f)).coerceIn(-1f, 1f)
    val thText = throttlePu.pct()          // 进度条用 pu，读数按百分比显示
    val strText = String.format(Locale.US, "%.2fpu", steerPu)
    // V 跟 V_IU 走；I / P 还没有真实来源，先置 0
    val vText = (io?.vIu ?: t.vbus).f1()

    DashPanel(
        index = "01",
        title = "DRIVE",
        modifier = modifier,
        trailing = { Text("[RUN]", fontFamily = Mono, fontSize = 8.sp, color = TextDim) },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 车速来自 dyn_fb（勾选轮子中最慢的一个）；点开可标定参数
                Row(
                    modifier = Modifier.clickable(onClick = onSpeedClick),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = speedKph?.f1() ?: "0.0",
                        fontFamily = Mono,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = " km/h",
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = TextDim,
                        modifier = Modifier.padding(bottom = 3.dp, start = 2.dp),
                    )
                    Text(
                        text = "SET",
                        fontFamily = Mono,
                        fontSize = 6.sp,
                        color = TextFaint,
                        modifier = Modifier.padding(bottom = 4.dp, start = 3.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                BarLine(
                    label = "THR",
                    value = throttlePu,
                    valueText = thText,
                    color = Phosphor,
                )
                Spacer(Modifier.height(2.dp))
                CenterBarLine(
                    label = "STR",
                    value = steerPu,
                    valueText = strText,
                    color = PhosphorHot,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(Hairline),
            )
            Spacer(Modifier.width(4.dp))
            Column(
                modifier = Modifier.widthIn(min = 112.dp, max = 128.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricLine("V", vText, "V", 12.sp)
                MetricLine("I", "0.0", "A", 12.sp)
                MetricLine("P", "0.00", "kW", 12.sp)
            }
        }
    }
}

@Composable
private fun IoPanel(t: Telemetry, io: Ccu.IuIo?, modifier: Modifier = Modifier) {
    // 有真实 packIU_IO 就用真实的，没连上时退回演示数据（面板右上角会标 SIM）
    val gear = io?.gear ?: t.gear
    val reverse = io?.reverse ?: t.reverse
    val repair = io?.repair ?: t.repair
    val park = io?.park ?: t.park
    val turnLeft = io?.turnLeft ?: t.turnLeft
    val turnRight = io?.turnRight ?: t.turnRight
    val brake = io?.brake ?: t.brake
    // 固件里这两项是 pu（转把曲线输出 / 转向角度输出），不是 ADC 电压
    val thText = io?.let { String.format(Locale.US, "%.2fpu", it.throttlePu) }
        ?: String.format(Locale.US, "%.2fV", t.throttleVolt)
    val strText = io?.let { String.format(Locale.US, "%.2fpu", it.steeringPu) }
        ?: String.format(Locale.US, "%.2fV", t.steerVolt)
    val ntcText = io?.let { "${it.ntcC}℃" } ?: "${t.tempC.f1()}℃"
    val refText = String.format(Locale.US, "%04d", io?.ref2vAd ?: t.ref2vAd)
    val vIuText = "${(io?.vIu ?: t.vIu).f1()}V"
    val vAccText = "${(io?.vAcc ?: t.vAcc).f1()}V"
    val v12Text = "${(io?.v12 ?: t.v12).f1()}V"

    DashPanel(
        index = "02",
        title = "IO",
        modifier = modifier,
        trailing = {
            Text(
                text = if (io != null) "IU" else "IU·SIM",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = if (io != null) Phosphor else TextFaint,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1.25f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KeyCell(
                active = gear == 1,
                color = Phosphor,
                glyph = "1",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = gear == 2,
                color = Phosphor,
                glyph = "2",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = gear == 3,
                color = Phosphor,
                glyph = "3",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = reverse,
                color = Phosphor,
                glyph = "R",
                label = "REV",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = repair,
                color = Phosphor,
                icon = Icons.Outlined.Handyman,
                label = "FIX",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = park,
                color = Phosphor,
                glyph = "P",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = turnLeft,
                color = Phosphor,
                icon = Icons.AutoMirrored.Outlined.ArrowLeft,
                label = "L",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = turnRight,
                color = Phosphor,
                icon = Icons.AutoMirrored.Outlined.ArrowRight,
                label = "R",
                modifier = Modifier.weight(1f),
            )
            KeyCell(
                active = brake,
                color = Phosphor,
                icon = Icons.Outlined.DiscFull,
                label = "BRK",
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReadoutBox(
                label = "TH",
                value = thText,
                labelSize = 8.sp,
                valueSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            ReadoutBox(
                label = "STR",
                value = strText,
                labelSize = 8.sp,
                valueSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReadoutBox(
                label = "NTC",
                value = ntcText,
                labelSize = 6.5.sp,
                valueSize = 8.sp,
                modifier = Modifier.weight(1f),
            )
            ReadoutBox(
                label = "2.0REF",
                value = refText,
                labelSize = 6.5.sp,
                valueSize = 8.sp,
                modifier = Modifier.weight(1f),
            )
            ReadoutBox(
                label = "V_IU",
                value = vIuText,
                labelSize = 6.5.sp,
                valueSize = 8.sp,
                modifier = Modifier.weight(1f),
            )
            ReadoutBox(
                label = "V_ACC",
                value = vAccText,
                labelSize = 6.5.sp,
                valueSize = 8.sp,
                modifier = Modifier.weight(1f),
            )
            ReadoutBox(
                label = "V_12",
                value = v12Text,
                labelSize = 6.5.sp,
                valueSize = 8.sp,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

@Composable
private fun KeyCell(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String? = null,
) {
    val fg = if (active) Color.Black else TextDim
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (active) color else Color.Transparent)
            .border(1.dp, if (active) color else Hairline),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            glyph != null -> Text(
                text = glyph,
                fontFamily = Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        if (label != null) {
            Text(
                text = label,
                fontFamily = Mono,
                fontSize = 6.sp,
                color = if (active) Color.Black.copy(alpha = 0.65f) else TextFaint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReadoutBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 7.sp,
    valueSize: TextUnit = 8.sp,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = labelSize,
            color = TextFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(14.dp).background(Hairline))
        Text(
            text = value,
            fontFamily = Mono,
            fontSize = valueSize,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MotorGrid(
    t: Telemetry,
    iqTraces: List<List<Float>>,
    ibusTraces: List<List<Float>>,
    omegaTraces: List<List<Float>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MotorCell("03", "FL", 0, t, iqTraces, ibusTraces, omegaTraces, Modifier.weight(1f))
            MotorCell("04", "FR", 1, t, iqTraces, ibusTraces, omegaTraces, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MotorCell("05", "RL", 2, t, iqTraces, ibusTraces, omegaTraces, Modifier.weight(1f))
            MotorCell("06", "RR", 3, t, iqTraces, ibusTraces, omegaTraces, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MotorCell(
    index: String,
    label: String,
    motorIndex: Int,
    t: Telemetry,
    iqTraces: List<List<Float>>,
    ibusTraces: List<List<Float>>,
    omegaTraces: List<List<Float>>,
    modifier: Modifier = Modifier,
) {
    val ctrlTemp = t.controllerTemps.getOrElse(motorIndex) { 0f }
    val motorTemp = t.wheelTemps.getOrElse(motorIndex) { 0f }
    val fault = t.motorFaultCodes.getOrElse(motorIndex) { 0 }
    val faultColor = if (fault == 0) Phosphor else Danger
    val faultText = if (fault == 0) "[OK]" else String.format(Locale.US, "[E%02X]", fault)
    val iq = t.motorIq.getOrElse(motorIndex) { 0f }
    val ibus = t.motorIbus.getOrElse(motorIndex) { 0f }
    val omega = t.motorOmega.getOrElse(motorIndex) { 0f }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceLow.copy(alpha = 0.18f))
            .border(1.dp, Hairline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(index, fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
                Text(" // ", fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
                Text(
                    text = label,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.0.sp,
                    color = Phosphor,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CTRL ${ctrlTemp.f0()}°",
                    fontFamily = Mono,
                    fontSize = 7.sp,
                    color = TextDim,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MOT ${motorTemp.f0()}°",
                    fontFamily = Mono,
                    fontSize = 7.sp,
                    color = TextDim,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = faultText,
                    fontFamily = Mono,
                    fontSize = 7.sp,
                    color = faultColor,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        MotorCurves(
            iq = iqTraces.getOrElse(motorIndex) { emptyList() },
            ibus = ibusTraces.getOrElse(motorIndex) { emptyList() },
            omega = omegaTraces.getOrElse(motorIndex) { emptyList() },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MotorLegend("IQ ${iq.f3()}", Phosphor, Modifier.weight(1f))
            MotorLegend("IB ${ibus.f3()}", Amber, Modifier.weight(1f))
            MotorLegend("ω ${omega.f3()}", TextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MotorLegend(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(7.dp).height(2.dp).background(color))
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            fontFamily = Mono,
            fontSize = 6.sp,
            color = TextDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun MotorCurves(
    iq: List<Float>,
    ibus: List<Float>,
    omega: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val grid = Hairline
        for (i in 0..2) {
            val x = size.width * i / 2f
            val y = size.height * i / 2f
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        // ±1 双向量程：中间那条就是 0，画亮一点当零线
        drawLine(
            color = Phosphor.copy(alpha = 0.28f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5f,
        )

        fun plot(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val pad = 2.dp.toPx()
            val w = (size.width - pad * 2f).coerceAtLeast(1f)
            val h = (size.height - pad * 2f).coerceAtLeast(1f)
            val path = Path()
            values.forEachIndexed { i, raw ->
                val x = pad + i * (w / (values.size - 1))
                // -1..+1 -> 底..顶，0 正好落在中线
                val v = raw.coerceIn(-1f, 1f)
                val y = pad + (1f - (v + 1f) / 2f) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        plot(iq, Phosphor)
        plot(ibus, Amber)
        plot(omega, TextPrimary)
    }
}

/**
 * Four controllers' feedback, straight from dyn_fb[n]:
 * Iq_fb (A), Ibus_fb (A), omega_fb (rpm). Values are shown as "--" until the
 * CCU protocol layer fills Telemetry.motor*.
 */
@Composable
private fun MotorPanel(t: Telemetry, modifier: Modifier = Modifier) {
    DashPanel(
        index = "03",
        title = "MOTOR FB",
        modifier = modifier,
        trailing = { Text("4 DEV", fontFamily = Mono, fontSize = 8.sp, color = TextFaint) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHead("WHEEL", 30.dp)
            TableHead("IQ A", 1f, TextAlign.End)
            TableHead("IBUS A", 1f, TextAlign.End)
            TableHead("ω RPM", 1f, TextAlign.End)
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WheelLabels.forEachIndexed { index, label ->
                val iq = t.motorIq.getOrNull(index)
                val ibus = t.motorIbus.getOrNull(index)
                val omega = t.motorOmega.getOrNull(index)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = TextDim,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = iq?.f1() ?: "--",
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = Phosphor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ibus?.f1() ?: "--",
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = PhosphorDim,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = omega?.f0() ?: "--",
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroRow(
    t: Telemetry,
    link: LinkState,
    powerKw: Float,
    maxTemp: Float,
    minTemp: Float,
    hotIndex: Int,
    modifier: Modifier = Modifier.fillMaxWidth().height(84.dp),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MicroPanel(
            index = "04",
            title = "POWER",
            modifier = Modifier.weight(1f),
        ) {
            MicroStat("V", t.vbus.f1(), "V")
            MicroStat("I", t.ibus.f1(), "A")
            MicroStat("P", String.format(Locale.US, "%.2f", powerKw), "kW")
        }
        MicroPanel(
            index = "05",
            title = "THERMAL",
            modifier = Modifier.weight(1f),
        ) {
            MicroStat("TMAX", maxTemp.f0(), "°C", if (maxTemp >= 70f) Amber else TextPrimary)
            MicroStat("ΔT", (maxTemp - minTemp).f1(), "°C")
            MicroStat("HOT", WheelLabels.getOrElse(hotIndex) { "--" }, null)
        }
        MicroPanel(
            index = "06",
            title = "LINK",
            modifier = Modifier.weight(1f),
        ) {
            val connected = link is LinkState.Connected
            MicroStat("STATE", if (connected) "2M" else "--", null)
            MicroStat("MTU", if (connected) "247" else "--", null)
            MicroStat("CAN", "4/4", null)
        }
    }
}

@Composable
private fun ResponsePanel(
    t: Telemetry,
    throttleTrace: List<Float>,
    steerTrace: List<Float>,
    modifier: Modifier = Modifier,
) {
    val thrMin = throttleTrace.minOrNull()?.pct() ?: "--"
    val thrMax = throttleTrace.maxOrNull()?.pct() ?: "--"
    val steerMin = steerTrace.minOrNull()?.let { ((it * 60f) - 30f).signed(0) + "°" } ?: "--"
    val steerMax = steerTrace.maxOrNull()?.let { ((it * 60f) - 30f).signed(0) + "°" } ?: "--"

    DashPanel(
        index = "07",
        title = "RESPONSE",
        modifier = modifier,
        trailing = { Text("2 CH · 60ms", fontFamily = Mono, fontSize = 8.sp, color = TextFaint) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TraceRow(
                label = "THR",
                value = t.throttle.pct(),
                min = thrMin,
                max = thrMax,
                values = throttleTrace,
                color = Phosphor,
                modifier = Modifier.weight(1f),
            )
            TraceRow(
                label = "STR",
                value = "${t.steerDeg.signed(0)}°",
                min = steerMin,
                max = steerMax,
                values = steerTrace,
                color = PhosphorHot,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EventStrip(link: LinkState) {
    val logs by Ble.log.collectAsState()
    var showLog by remember { mutableStateOf(false) }
    val latest = logs.lastOrNull()
    // 最新一条蓝牙消息的颜色：连上是灰的，重连是橙的，错误是红的
    val latestColor = when (link) {
        is LinkState.Connected -> TextFaint
        is LinkState.Error -> Danger
        LinkState.Connecting, is LinkState.Reconnecting -> Amber
        LinkState.Disconnected -> TextDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clickable { showLog = true }
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("EVENT", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
        Spacer(Modifier.width(6.dp))
        Text(
            text = latest ?: "NO BT EVENT",
            fontFamily = Mono,
            fontSize = 8.sp,
            color = latestColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    if (showLog) {
        LinkLogSheet(logs = logs, onDismiss = { showLog = false })
    }
}

/** 点 EVENT 条弹出的蓝牙链路日志：能翻到"链路断开 / 重连"这些事件。 */
private val LogRowHeight = 11.dp
private const val LogMaxVisibleRows = 8
private val LogRowGap = 3.dp
private val LogListPadding = 6.dp
private val LogListMaxHeight =
    LogRowHeight * LogMaxVisibleRows + LogRowGap * (LogMaxVisibleRows - 1) + LogListPadding * 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkLogSheet(logs: List<String>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            // 等一帧让 maxValue 反映最新高度，再贴到底（新日志在下方）
            withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    // 面板高度按 8 条算（LogListMaxHeight），再多就在列表里自己滑，不会一直往上长
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(36.dp, 3.dp).background(Hairline))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LINK LOG",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = Phosphor,
                )
                Spacer(Modifier.width(6.dp))
                Text("${logs.size}", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "CLR",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = TextDim,
                    modifier = Modifier.clickable { Ble.clearLog() }.padding(horizontal = 8.dp),
                )
                Text(
                    text = "CLOSE",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = TextDim,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LogListMaxHeight)
                    .background(SurfaceLow.copy(alpha = 0.2f))
                    .border(1.dp, Hairline),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "NO BLE EVENT",
                        fontFamily = Mono,
                        fontSize = 8.sp,
                        color = TextFaint,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 24.dp),
                    )
                } else {
                    // 普通 Column + scroll：内容少时不高、内容多时固定 8 行高，滚到底不会有空白
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(LogListPadding),
                        verticalArrangement = Arrangement.spacedBy(LogRowGap),
                    ) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                fontFamily = Mono,
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                color = logLineColor(line),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(LogRowHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun logLineColor(line: String): Color = when {
    line.contains("断开") || line.contains("失败") ||
        line.contains("未成功") || line.contains("中断") -> Danger
    line.contains("重连") -> Amber
    line.contains("服务就绪") || line.contains("MTU=") -> Phosphor
    else -> TextDim
}

@Composable
private fun FaultBanner(code: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(Danger.copy(alpha = 0.12f))
            .border(1.dp, Danger.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", fontFamily = Mono, fontSize = 10.sp, color = Danger)
        Spacer(Modifier.width(5.dp))
        Text(
            text = "FAULT 0x%02X / CHECK DRIVE".format(code),
            fontFamily = Mono,
            fontSize = 9.sp,
            color = Danger,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Panel primitives
// ---------------------------------------------------------------------------

@Composable
private fun DashPanel(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(SurfaceLow.copy(alpha = 0.28f))
            .border(1.dp, Hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(index, fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(" // ", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(
                text = title,
                fontFamily = Mono,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.0.sp,
                color = Phosphor,
            )
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun MicroPanel(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DashPanel(index = index, title = title, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

@Composable
private fun TableHead(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        fontFamily = Mono,
        fontSize = 7.sp,
        color = TextFaint,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun RowScope.TableHead(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        fontFamily = Mono,
        fontSize = 7.sp,
        color = TextFaint,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun MetricLine(
    label: String,
    value: String,
    unit: String,
    valueSize: TextUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = TextFaint,
            modifier = Modifier.width(12.dp),
        )
        Text(
            text = value,
            fontFamily = Mono,
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
        Text(
            text = unit,
            fontFamily = Mono,
            fontSize = 7.sp,
            color = TextDim,
            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
        )
    }
}

@Composable
private fun MicroStat(
    label: String,
    value: String,
    unit: String?,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 7.sp,
            color = TextFaint,
            maxLines = 1,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = value,
            fontFamily = Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (unit != null) {
            Text(
                text = unit,
                fontFamily = Mono,
                fontSize = 7.sp,
                color = TextFaint,
                modifier = Modifier.padding(start = 1.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Data primitives
// ---------------------------------------------------------------------------

@Composable
private fun BarLine(
    label: String,
    value: Float,
    valueText: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = TextFaint, modifier = Modifier.width(24.dp))
        SegmentBar(value = value, color = color, modifier = Modifier.weight(1f))
        Text(
            text = valueText,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = TextDim,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun CenterBarLine(
    label: String,
    value: Float,
    valueText: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = TextFaint, modifier = Modifier.width(24.dp))
        CenterBar(value = value, color = color, modifier = Modifier.weight(1f))
        Text(
            text = valueText,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = TextDim,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun SegmentBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 20,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val gap = 1.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        if (w <= 0f) return@Canvas
        val filled = (value.coerceIn(0f, 1f) * segments).roundToInt()
        for (i in 0 until segments) {
            val x = i * (w + gap)
            drawRect(
                color = if (i < filled) color else SurfaceHigh,
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
        }
    }
}

@Composable
private fun CenterBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 20,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val gap = 1.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        if (w <= 0f) return@Canvas
        val center = segments / 2
        val target = (center + value.coerceIn(-1f, 1f) * center).roundToInt()
        for (i in 0 until segments) {
            val x = i * (w + gap)
            val active = if (target >= center) i in center until target else i in target until center
            drawRect(
                color = if (active) color else SurfaceHigh,
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
        }
        drawLine(
            color = TextFaint,
            start = Offset(center * (w + gap) - gap / 2f, 0f),
            end = Offset(center * (w + gap) - gap / 2f, size.height),
            strokeWidth = 1f,
        )
    }
}

@Composable
private fun TraceRow(
    label: String,
    value: String,
    min: String,
    max: String,
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(94.dp)) {
            Text(label, fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    maxLines = 1,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        fontFamily = Mono,
                        fontSize = 7.sp,
                        color = TextDim,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
            }
            Text(
                text = "MIN $min  MAX $max",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = TextDim,
                maxLines = 1,
            )
        }
        DashSparkline(
            values = values,
            color = color,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun DashSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
) {
    Canvas(modifier = modifier) {
        if (size.height <= 0f || size.width <= 0f) return@Canvas
        if (showGrid) {
            val grid = Hairline
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
        if (values.size < 2) return@Canvas

        val step = size.width / (values.size - 1)
        val pad = 2.dp.toPx()
        val h = (size.height - pad * 2f).coerceAtLeast(1f)
        val path = Path()
        values.forEachIndexed { index, raw ->
            val x = index * step
            val y = pad + (1f - raw.coerceIn(0f, 1f)) * h
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.12f), Color.Transparent),
            ),
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round),
        )
        val last = values.last().coerceIn(0f, 1f)
        drawCircle(
            color = color,
            radius = 1.8.dp.toPx(),
            center = Offset(size.width, pad + (1f - last) * h),
        )
    }
}

// ---------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------

@Composable
private fun CrtBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Ink)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                center = Offset(size.width / 2f, size.height * 0.36f),
                radius = size.maxDimension * 0.72f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Phosphor.copy(alpha = 0.035f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.22f,
            ),
        )
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.04f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}
