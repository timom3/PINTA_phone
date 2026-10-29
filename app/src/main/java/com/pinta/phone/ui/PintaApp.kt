package com.pinta.phone.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pinta.phone.ble.Ble
import com.pinta.phone.ble.BleDevice
import com.pinta.phone.ble.Ccu
import com.pinta.phone.ble.LinkState
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.components.PintaButton
import com.pinta.phone.ui.components.SectionLabel
import com.pinta.phone.ui.icons.CockpitIcon
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mint
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Orange
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.SurfaceContainer
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import com.pinta.phone.ui.theme.PintaTheme

enum class Tab(val label: String, val icon: ImageVector) {
    Dash("座舱", CockpitIcon),
    Tune("调校", Icons.Outlined.Tune),
    Scope("监控", Icons.AutoMirrored.Outlined.ShowChart),
    Setup("设置", Icons.Outlined.Settings),
}

/**
 * One fixed console header above every tab.
 * Top rail never changes; the page code/name changes per tab.
 */
@Composable
private fun PintaChromeHeader(
    tab: Tab,
    route: String?,
    link: LinkState,
    mtu: Int,
    phy: Int,
    onLinkClick: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = link is LinkState.Connected
    // 真实读回的 PHY（1M / 2M / CODED），还没读到时为 null
    val phyText = Ble.phyShort(phy)
    val linkText = when (link) {
        is LinkState.Connected -> phyText?.let { "[LINK $it]" } ?: "[LINK]"
        LinkState.Connecting -> "[LINK WAIT]"
        is LinkState.Reconnecting -> "[LINK RETRY]"
        LinkState.Disconnected -> "[NO LINK]"
        is LinkState.Error -> "[LINK LOST]"
    }
    val linkColor = when (link) {
        is LinkState.Connected -> Phosphor
        LinkState.Connecting -> Amber
        is LinkState.Reconnecting -> Amber
        LinkState.Disconnected -> TextFaint
        is LinkState.Error -> Danger
    }
    // 正在连 / 正在重连：橙色呼吸闪烁，用户一眼能看出"还在试"
    val blinking = link is LinkState.Connecting || link is LinkState.Reconnecting
    val blinkTransition = rememberInfiniteTransition(label = "link-blink")
    val blinkAlpha = blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "link-blink-alpha",
    )

    val detail = tab == Tab.Tune && route != null
    val detailCode = when (route) {
        ROUTE_LAYOUT -> 1
        ROUTE_THROTTLE -> 2
        ROUTE_STEERING -> 3
        ROUTE_BATTERY -> 4
        ROUTE_NODES -> 5
        else -> 1
    }
    val code = when {
        tab == Tab.Dash -> "00"
        tab == Tab.Tune && detail -> "10.$detailCode"
        tab == Tab.Tune -> "10"
        tab == Tab.Scope -> "20"
        else -> "30"
    }
    val title = when {
        tab == Tab.Dash -> "COCKPIT"
        tab == Tab.Scope -> "SCOPE"
        tab == Tab.Setup -> "SYSTEM"
        !detail -> "TUNE"
        route == ROUTE_LAYOUT -> "LAYOUT"
        route == ROUTE_THROTTLE -> "THROTTLE"
        route == ROUTE_STEERING -> "STEERING"
        route == ROUTE_BATTERY -> "BATTERY"
        else -> "NODES"
    }
    val context = when {
        tab == Tab.Dash -> if (connected) {
            "PHY ${phyText ?: "--"} · MTU ${if (mtu > 0) mtu else "--"}"
        } else {
            "PHY -- · MTU --"
        }
        tab == Tab.Tune && detail -> when (route) {
            ROUTE_LAYOUT -> "4/4 BOUND · 8 PARAM"
            ROUTE_THROTTLE -> "REFDATA · 8 PARAMS"
            ROUTE_STEERING -> "REFDATA · 9 PARAMS"
            ROUTE_BATTERY -> "4 WHEELS · 20 PARAMS"
            else -> "5 NODES"
        }
        tab == Tab.Tune -> "PROFILE · 111 VAR / 44 REF"
        tab == Tab.Scope -> "8 CH · ACQ_C / 1–255 ms"
        else -> "IUPro v1.260406.3 · CONFIG"
    }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .statusBarsPadding(),
        ) {
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
                        // 只在图层里读动画值：闪烁时只重绘，不重组页面
                        .graphicsLayer { alpha = if (blinking) blinkAlpha.value else 1f }
                        .clickable(onClick = onLinkClick)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = linkText,
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
                if (detail) {
                    Text(
                        text = "←",
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        color = TextDim,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(end = 6.dp),
                    )
                }
                Text(code, fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
                Text(" // ", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
                Text(
                    text = title,
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
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "[SIM]",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Amber,
                    maxLines = 1,
                )
            }
            HairlineH()
        }
    }
}

@Composable
fun PintaApp() {
    val context = LocalContext.current
    val link by Ble.link.collectAsState()
    val devices by Ble.devices.collectAsState()
    val scanning by Ble.isScanning.collectAsState()
    val log by Ble.log.collectAsState()
    val mtu by Ble.mtu.collectAsState()
    val phy by Ble.phy.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.Dash) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var showLink by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) Ble.startScan()
    }

    fun scanWithPermission() {
        val missing = Ble.requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) Ble.startScan() else permissionLauncher.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose { Ble.stopScan() }
    }

    BackHandler(enabled = route != null) { route = null }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        Scaffold(
            containerColor = Ink,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PintaBottomBar(current = tab) {
                    tab = it
                    route = null
                }
            },
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    PintaChromeHeader(
                        tab = tab,
                        route = route,
                        link = link,
                        mtu = mtu,
                        phy = phy,
                        onLinkClick = { showLink = true },
                        onBack = { route = null },
                    )
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (tab) {
                            Tab.Dash -> DashScreen(link = link)

                            Tab.Tune -> {
                                val current = route
                                when {
                                    current == null -> TuneScreen(
                                        link = link,
                                        onLinkClick = { showLink = true },
                                        onOpen = { route = it },
                                    )

                                    current == ROUTE_THROTTLE -> ThrottleScreen(
                                        link = link,
                                        onLinkClick = { showLink = true },
                                        onBack = { route = null },
                                    )

                                    else -> TuneDetailPlaceholder(
                                        title = titleOf(current),
                                        onBack = { route = null },
                                        link = link,
                                        onLinkClick = { showLink = true },
                                    )
                                }
                            }

                            Tab.Scope -> ScopeScreen(link = link, onLinkClick = { showLink = true })
                            Tab.Setup -> SetupScreen(link = link, onLinkClick = { showLink = true })
                        }
                    }
                }
            }
        }

        if (showLink) {
            // 控制台打开期间暂停 IO 轮询：否则 BT RAW 每秒被 8 帧刷屏，还会跟 LAYOUT/BANDWIDTH 抢请求锁
            LaunchedEffect(Unit) { Ccu.stopIoPolling() }
            LinkConsole(
                link = link,
                devices = devices,
                scanning = scanning,
                onClose = {
                    Ble.stopScan()
                    Ccu.stopBandwidthTest()
                    Ccu.startIoPolling()
                    showLink = false
                },
                onScan = { scanWithPermission() },
                onStopScan = { Ble.stopScan() },
                onConnect = { Ble.connect(it) },
                onDisconnect = { Ble.disconnect() },
            )
        }
    }
}

private fun titleOf(route: String): String = when (route) {
    ROUTE_LAYOUT -> "整车布局"
    ROUTE_STEERING -> "转向与差速"
    ROUTE_BATTERY -> "电池与保护"
    ROUTE_NODES -> "控制器与固件"
    else -> "调校"
}

@Composable
private fun PintaBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .navigationBarsPadding(),
    ) {
        HairlineH()
        Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Tab.values().forEach { item ->
                val selected = item == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) Phosphor else Color.Transparent),
                    )
                    Spacer(Modifier.height(3.dp))
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) Phosphor else TextFaint,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) Phosphor else TextFaint,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(
    link: LinkState,
    devices: List<BleDevice>,
    scanning: Boolean,
    log: List<String>,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stateText: String = when (link) {
        is LinkState.Connected -> "已连接 · ${link.address}"
        LinkState.Connecting -> "正在连接…"
        is LinkState.Reconnecting -> "正在重连（第 ${link.attempt} 次）…"
        LinkState.Disconnected -> "未连接"
        is LinkState.Error -> link.message
    }
    val stateColor: Color = when (link) {
        is LinkState.Connected -> Mint
        LinkState.Connecting -> Orange
        is LinkState.Reconnecting -> Orange
        LinkState.Disconnected -> TextFaint
        is LinkState.Error -> com.pinta.phone.ui.theme.Danger
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(36.dp, 3.dp).background(Hairline))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "蓝牙链路",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = stateText, fontSize = 11.sp, color = stateColor, maxLines = 1)
                }
                Text(
                    text = "XY-MBD22A · FFE1",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = TextFaint,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PintaButton(
                    label = if (scanning) "扫描中…" else "扫描设备",
                    primary = false,
                    enabled = !scanning,
                    modifier = Modifier.weight(1f),
                    onClick = onScan,
                )
                if (link is LinkState.Connected || link is LinkState.Connecting) {
                    PintaButton(
                        label = "断开",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = onDisconnect,
                    )
                }
            }

            if (devices.isEmpty()) {
                Text(
                    text = "未发现设备。确认 IUPro 已上电、蓝牙模块未被其他手机占用。",
                    fontSize = 11.sp,
                    color = TextFaint,
                )
            } else {
                devices.take(6).forEach { device ->
                    val connected = link is LinkState.Connected && link.address == device.address
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceLow)
                            .clickable { if (!connected) onConnect(device) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = device.address,
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                color = TextFaint,
                            )
                        }
                        Text(
                            text = if (connected) "已连接" else "${device.rssi} dBm",
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            color = if (connected) Mint else TextDim,
                        )
                    }
                }
            }

            if (log.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                SectionLabel("链路日志")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    log.takeLast(5).forEach { line ->
                        Text(
                            text = line,
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = TextFaint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
