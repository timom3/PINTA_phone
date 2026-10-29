package com.pinta.phone.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.Ble
import com.pinta.phone.ble.BleDevice
import com.pinta.phone.ble.BleFrame
import com.pinta.phone.ble.Ccu
import com.pinta.phone.ble.FrameDirection
import com.pinta.phone.ble.LinkState
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mint
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val linkTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
fun LinkConsole(
    link: LinkState,
    devices: List<BleDevice>,
    scanning: Boolean,
    onClose: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val frames by Ble.frames.collectAsState()
    val mtu by Ble.mtu.collectAsState()
    val ccuLogs by Ccu.logs.collectAsState()
    val latency by Ccu.latency.collectAsState()
    val bandwidth by Ccu.bandwidth.collectAsState()
    val bandwidthRunning by Ccu.isBandwidthRunning.collectAsState()
    val connected = link is LinkState.Connected

    CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ConsoleHeader(link = link, mtu = mtu, onClose = onClose)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ConnectionStrip(
                    link = link,
                    scanning = scanning,
                    onScan = onScan,
                    onStopScan = onStopScan,
                    onDisconnect = onDisconnect,
                )
                DeviceRail(
                    devices = devices,
                    scanning = scanning,
                    onConnect = onConnect,
                )
                RawPanel(
                    frames = frames,
                    connected = connected,
                    modifier = Modifier.weight(1f),
                )
                CcpPanel(
                    logs = ccuLogs,
                    latency = latency,
                    bandwidth = bandwidth,
                    running = bandwidthRunning,
                    connected = connected,
                    modifier = Modifier.weight(1.15f),
                )
            }
        }
    }
}

@Composable
private fun ConsoleHeader(link: LinkState, mtu: Int, onClose: () -> Unit) {
    val state = when (link) {
        is LinkState.Connected -> "CONNECTED"
        LinkState.Connecting -> "CONNECTING"
        is LinkState.Reconnecting -> "RETRY ${link.attempt}"
        LinkState.Disconnected -> "NO LINK"
        is LinkState.Error -> "ERROR"
    }
    val color = when (link) {
        is LinkState.Connected -> Phosphor
        LinkState.Connecting -> Amber
        is LinkState.Reconnecting -> Amber
        LinkState.Disconnected -> TextFaint
        is LinkState.Error -> Danger
    }
    Column(modifier = Modifier.fillMaxWidth().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 6.dp),
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
            Text(
                text = "[CLOSE]",
                fontFamily = Mono,
                fontSize = 9.sp,
                color = TextDim,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 2.dp),
            )
        }
        HairlineH()
        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("L", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(" // ", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
            Text(
                text = "BLUETOOTH",
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = Phosphor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "MTU ${if (mtu > 0) mtu else "--"}",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = TextFaint,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = state,
                fontFamily = Mono,
                fontSize = 8.sp,
                color = color,
            )
        }
        HairlineH()
    }
}

@Composable
private fun ConnectionStrip(
    link: LinkState,
    scanning: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state = when (link) {
        is LinkState.Connected -> "${link.name} · ${link.address}"
        LinkState.Connecting -> "正在连接…"
        is LinkState.Reconnecting -> "重连中 第${link.attempt}次 · ${link.name}"
        LinkState.Disconnected -> "未连接 · 点击 SCAN 搜索 IUPro"
        is LinkState.Error -> link.message
    }
    val color = when (link) {
        is LinkState.Connected -> Phosphor
        LinkState.Connecting -> Amber
        is LinkState.Reconnecting -> Amber
        LinkState.Disconnected -> TextFaint
        is LinkState.Error -> Danger
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(SurfaceLow.copy(alpha = 0.18f))
            .border(1.dp, Hairline)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state,
                fontFamily = Mono,
                fontSize = 7.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "BLE · FFE1 · 通知 FFE2 / 写 FFE3",
                fontFamily = Mono,
                fontSize = 5.5.sp,
                color = TextFaint,
                maxLines = 1,
            )
        }
        if (scanning) {
            ConsoleButton("STOP", active = true, onClick = onStopScan)
        } else {
            ConsoleButton("SCAN", onClick = onScan)
        }
        if (link is LinkState.Connected || link is LinkState.Connecting || link is LinkState.Reconnecting) {
            Spacer(Modifier.width(4.dp))
            ConsoleButton("DISC", color = Danger, onClick = onDisconnect)
        }
    }
}

@Composable
private fun DeviceRail(
    devices: List<BleDevice>,
    scanning: Boolean,
    onConnect: (BleDevice) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (devices.isEmpty()) {
            Text(
                text = if (scanning) "SCANNING · 0 DEVICE" else "NO DEVICE · TAP SCAN",
                fontFamily = Mono,
                fontSize = 6.5.sp,
                color = if (scanning) Amber else TextFaint,
            )
        } else {
            Text(
                text = if (scanning) "SCAN ${devices.size}" else "DEV ${devices.size}",
                fontFamily = Mono,
                fontSize = 6.sp,
                color = TextFaint,
                modifier = Modifier.width(44.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                devices.take(48).forEach { device ->
                    Row(
                        modifier = Modifier
                            .height(22.dp)
                            .border(1.dp, Hairline)
                            .clickable { onConnect(device) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = device.name.take(14),
                            fontFamily = Mono,
                            fontSize = 6.5.sp,
                            color = TextPrimary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${device.rssi}",
                            fontFamily = Mono,
                            fontSize = 5.5.sp,
                            color = TextFaint,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RawPanel(
    frames: List<BleFrame>,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    var hexInput by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf("500") }
    var cyclic by remember { mutableStateOf(false) }
    var cyclicJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val bytes = remember(hexInput) { parseHex(hexInput) }
    val interval = intervalText.toLongOrNull()?.coerceIn(20L, 60_000L) ?: 500L

    fun stopCyclic() {
        cyclic = false
        cyclicJob?.cancel()
        cyclicJob = null
    }

    fun startCyclic() {
        val data = bytes ?: return
        cyclic = true
        cyclicJob = scope.launch {
            while (isActive) {
                if (!Ble.send(data)) break
                delay(interval)
            }
            cyclic = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { cyclicJob?.cancel() }
    }

    ConsolePanel(
        title = "BT RAW",
        modifier = modifier,
        trailing = {
            Text(
                text = "${frames.size} FRAME",
                fontFamily = Mono,
                fontSize = 6.sp,
                color = TextFaint,
            )
            Spacer(Modifier.width(6.dp))
            ConsoleButton("CLR", onClick = { Ble.clearFrames() })
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HexInput(
                value = hexInput,
                onValueChange = { hexInput = it },
                isError = hexInput.isNotBlank() && bytes == null,
                modifier = Modifier.weight(1f),
            )
            ConsoleButton(
                label = "SEND",
                enabled = connected && bytes != null,
                onClick = { bytes?.let { Ble.send(it) } },
            )
            ConsoleButton(
                label = if (cyclic) "STOP" else "CYC",
                enabled = connected && bytes != null,
                active = cyclic,
                onClick = { if (cyclic) stopCyclic() else startCyclic() },
            )
            IntervalInput(
                value = intervalText,
                onValueChange = { intervalText = it.filter(Char::isDigit).take(6) },
            )
        }
        RawLog(frames = frames, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun CcpPanel(
    logs: List<Ccu.Log>,
    latency: List<Ccu.Latency>,
    bandwidth: Ccu.Bandwidth,
    running: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    ConsolePanel(
        title = "CCP PROTOCOL",
        modifier = modifier,
        trailing = {
            Text(
                text = "${logs.size} FRAME",
                fontFamily = Mono,
                fontSize = 6.sp,
                color = TextFaint,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ConsoleButton(
                label = "LAYOUT",
                enabled = connected,
                onClick = { Ccu.sendLayoutProbe() },
            )
            ConsoleButton(
                label = if (running) "STOP" else "BANDWIDTH",
                enabled = connected,
                active = running,
                color = if (running) Danger else Amber,
                onClick = {
                    if (running) Ccu.stopBandwidthTest() else Ccu.sendBandwidthRequest()
                },
            )
            Spacer(Modifier.weight(1f))
            ConsoleButton("CLR", onClick = { Ccu.clearLogs() })
        }
        BandwidthStrip(bandwidth)
        CcpLog(logs = logs, modifier = Modifier.fillMaxWidth().weight(1f))
        LatencyStrip(records = latency)
    }
}

@Composable
private fun BandwidthStrip(bandwidth: Ccu.Bandwidth) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .border(1.dp, Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BwStat("OK", "${bandwidth.successCount}", Phosphor, Modifier.weight(1f))
        BwDivider()
        BwStat("FAIL", "${bandwidth.failCount}", if (bandwidth.failCount > 0) Danger else TextDim, Modifier.weight(1f))
        BwDivider()
        BwStat("MS", if (bandwidth.totalTimeMs > 0) "${bandwidth.totalTimeMs}" else "--", TextDim, Modifier.weight(1f))
        BwDivider()
        BwStat(
            "B/s",
            if (bandwidth.bandwidthBps > 0) String.format(Locale.US, "%.0f", bandwidth.bandwidthBps) else "--",
            TextPrimary,
            Modifier.weight(1f),
        )
        BwDivider()
        BwStat("VAL", String.format(Locale.US, "%02X", bandwidth.currentValue), Amber, Modifier.weight(1f))
    }
}

@Composable
private fun BwStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontFamily = Mono, fontSize = 5.5.sp, color = TextFaint, maxLines = 1)
        Text(value, fontFamily = Mono, fontSize = 7.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun BwDivider() {
    Box(Modifier.width(1.dp).height(12.dp).background(Hairline))
}

@Composable
private fun LatencyStrip(records: List<Ccu.Latency>) {
    val avg = if (records.isEmpty()) 0L else records.map { it.latencyMs }.average().toLong()
    val max = records.maxOfOrNull { it.latencyMs } ?: 0L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .border(1.dp, Hairline)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("LAT", fontFamily = Mono, fontSize = 5.5.sp, color = TextFaint)
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (records.isEmpty()) "--" else "AVG ${avg}ms  MAX ${max}ms",
            fontFamily = Mono,
            fontSize = 6.sp,
            color = TextDim,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(14.dp)) {
            if (records.size < 2) return@Canvas
            val values = records.takeLast(40).map { it.latencyMs.toFloat() }
            val top = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1).coerceAtLeast(1)
                val y = size.height - (value / top) * (size.height - 2f) - 1f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Amber, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RawLog(frames: List<BleFrame>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty()) listState.animateScrollToItem(frames.lastIndex)
    }
    Box(
        modifier = modifier.background(SurfaceLow.copy(alpha = 0.16f)).border(1.dp, Hairline),
    ) {
        if (frames.isEmpty()) {
            Text(
                text = "NO RAW FRAME · LONG-PRESS TO COPY",
                fontFamily = Mono,
                fontSize = 6.sp,
                color = TextFaint,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(frames) { frame -> RawLine(frame) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RawLine(frame: BleFrame) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dir = if (frame.direction == FrameDirection.TX) "TX" else "RX"
    val color = if (frame.direction == FrameDirection.TX) Phosphor else Mint
    val hex = frame.bytes.toHex()
    val text = "${linkTime.format(Date(frame.timestamp))} $dir $hex"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "RAW copied", Toast.LENGTH_SHORT).show()
                },
            )
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(linkTime.format(Date(frame.timestamp)), fontFamily = Mono, fontSize = 5.5.sp, color = TextFaint)
        Text(dir, fontFamily = Mono, fontSize = 5.5.sp, color = color, fontWeight = FontWeight.Bold)
        Text(hex, fontFamily = Mono, fontSize = 6.sp, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CcpLog(logs: List<Ccu.Log>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    Box(
        modifier = modifier.background(SurfaceLow.copy(alpha = 0.16f)).border(1.dp, Hairline),
    ) {
        if (logs.isEmpty()) {
            Text(
                text = "NO CCP FRAME · LAYOUT / BANDWIDTH",
                fontFamily = Mono,
                fontSize = 6.sp,
                color = TextFaint,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(logs) { log -> CcpLine(log) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CcpLine(log: Ccu.Log) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dir = if (log.direction == FrameDirection.TX) "↑" else "↓"
    val color = if (log.direction == FrameDirection.TX) Phosphor else Mint
    val data = log.dataHex
    val checksumColor = when (log.checksumOk) {
        true -> Phosphor
        false -> Danger
        null -> TextFaint
    }
    val text = buildString {
        append(linkTime.format(Date(log.timestamp)))
        append(" ")
        append(dir)
        append(" [")
        append(log.name)
        append("] cmd=")
        append(String.format(Locale.US, "%02X", log.command))
        append(" len=")
        append(log.length)
        append(" data=")
        append(data)
        append(" chk=")
        append(String.format(Locale.US, "%02X", log.checksum))
        log.latencyMs?.let { append(" Δ${it}ms") }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "CCP copied", Toast.LENGTH_SHORT).show()
                },
            )
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(linkTime.format(Date(log.timestamp)), fontFamily = Mono, fontSize = 5.5.sp, color = TextFaint)
        Text(dir, fontFamily = Mono, fontSize = 6.sp, color = color, fontWeight = FontWeight.Bold)
        Text("[${log.name}]", fontFamily = Mono, fontSize = 6.sp, color = color, maxLines = 1)
        Text("cmd=${String.format(Locale.US, "%02X", log.command)}", fontFamily = Mono, fontSize = 5.5.sp, color = TextDim)
        Text("len=${log.length}", fontFamily = Mono, fontSize = 5.5.sp, color = TextDim)
        if (data.isNotEmpty()) {
            Text("|$data", fontFamily = Mono, fontSize = 5.5.sp, color = Amber, maxLines = 1)
        }
        Text("|chk=${String.format(Locale.US, "%02X", log.checksum)}", fontFamily = Mono, fontSize = 5.5.sp, color = checksumColor)
        log.latencyMs?.let {
            Text("|Δ${it}ms", fontFamily = Mono, fontSize = 5.5.sp, color = TextPrimary)
        }
    }
}

@Composable
private fun ConsolePanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceLow.copy(alpha = 0.18f))
            .border(1.dp, Hairline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontFamily = Mono,
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                color = Phosphor,
            )
            Spacer(Modifier.weight(1f))
            trailing()
        }
        HairlineH()
        Column(
            modifier = Modifier.fillMaxSize().padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content,
        )
    }
}

@Composable
private fun ConsoleButton(
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    color: Color = Phosphor,
    onClick: () -> Unit,
) {
    val fg = when {
        !enabled -> TextFaint
        active -> Color.Black
        else -> color
    }
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(if (active) color else Color.Transparent)
            .border(1.dp, if (active) color else Hairline)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Mono, fontSize = 6.5.sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun HexInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(24.dp)
            .background(SurfaceLow.copy(alpha = 0.3f))
            .border(1.dp, if (isError) Danger else Hairline)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        singleLine = true,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 8.sp, color = TextPrimary),
        cursorBrush = SolidColor(Phosphor),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("FF 01 02", fontFamily = Mono, fontSize = 7.sp, color = TextFaint)
                }
                inner()
            }
        },
    )
}

@Composable
private fun IntervalInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .background(SurfaceLow.copy(alpha = 0.3f))
            .border(1.dp, Hairline)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = Mono, fontSize = 7.sp, color = TextPrimary),
        cursorBrush = SolidColor(Phosphor),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { inner() }
                Text("ms", fontFamily = Mono, fontSize = 5.sp, color = TextFaint)
            }
        },
    )
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { String.format(Locale.US, "%02X", it) }

private fun parseHex(input: String): ByteArray? {
    val clean = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    if (clean.isEmpty() || clean.length % 2 != 0) return null
    return ByteArray(clean.length / 2) {
        clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
