package com.pinta.phone.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

enum class FrameDirection { TX, RX }

/** One raw BLE frame, kept for the RAW console. */
data class BleFrame(
    val timestamp: Long,
    val direction: FrameDirection,
    val bytes: ByteArray,
)

sealed interface LinkState {
    data object Disconnected : LinkState
    data object Connecting : LinkState
    data class Connected(val name: String, val address: String) : LinkState

    /**
     * 掉线后自动重连中。[attempt] 从 1 开始，会一直重试到连上，
     * 或者用户主动扫描/连接/断开为止。
     */
    data class Reconnecting(val attempt: Int, val name: String) : LinkState
    data class Error(val message: String) : LinkState
}

/**
 * BLE 透传模块 XY-MBD22A。
 *
 * Service FFE1 / 写 FFE3 / 通知 FFE2。
 * 这一层只负责"链路"：扫描、连接、MTU、通知、收发原始字节。
 * CCU 组帧/拆包属于协议层，不在这里。
 */
object Ble {

    private const val PREFS_NAME = "pinta_link"
    private const val KEY_LAST_ADDRESS = "last_address"
    private const val KEY_LAST_NAME = "last_name"

    /** 单次重连尝试最多等这么久，超时就 close 掉换下一次（模块没上电时 GATT 会拖 30s）。 */
    private const val CONNECT_TIMEOUT_MS = 8_000L

    private val SERVICE_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    private val TX_UUID: UUID = UUID.fromString("0000FFE3-0000-1000-8000-00805F9B34FB")
    private val RX_UUID: UUID = UUID.fromString("0000FFE2-0000-1000-8000-00805F9B34FB")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private var appContext: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var scanning = false

    /** 手动断开：区分"用户点的 DISC"和"模块掉电"，后者要自动重连。 */
    private var manualDisconnect = false
    private var prefs: SharedPreferences? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reconnectJob: Job? = null

    /** 自动重连的一次尝试在等结果；有值就说明这次连接不归 UI 管。 */
    @Volatile
    private var pendingConnect: CompletableDeferred<Boolean>? = null

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _frames = MutableStateFlow<List<BleFrame>>(emptyList())
    val frames: StateFlow<List<BleFrame>> = _frames.asStateFlow()

    private val _mtu = MutableStateFlow(0)
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    /** 实际协商到的 PHY（BluetoothDevice.PHY_LE_*），0 = 还没读回。 */
    private val _phy = MutableStateFlow(0)
    val phy: StateFlow<Int> = _phy.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = manager?.adapter
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** PHY 数值 -> 顶栏短标签；未知返回 null。 */
    fun phyShort(phy: Int): String? = when (phy) {
        BluetoothDevice.PHY_LE_1M -> "1M"
        BluetoothDevice.PHY_LE_2M -> "2M"
        BluetoothDevice.PHY_LE_CODED -> "CODED"
        else -> null
    }

    fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    // ---- scanning ---------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        // 用户开始扫描 = 手动接管，停掉后台重连，别两边抢 GATT
        cancelReconnect()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _link.value = LinkState.Error("蓝牙未开启")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            _devices.value = emptyList()
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            _isScanning.value = true
            appendLog("开始扫描")
        } catch (e: SecurityException) {
            _link.value = LinkState.Error("缺少蓝牙权限")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // ignore
        }
        scanning = false
        _isScanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            // 广播包里的名字优先；没有名字也要显示（XY-MBD22A 有时名字只在 scan response 里），
            // 以前直接 return 会把模块整个过滤掉，列表永远是 0 DEVICE。
            val name = try {
                result.scanRecord?.deviceName?.takeIf { it.isNotBlank() } ?: device.name
            } catch (_: SecurityException) {
                null
            } ?: "未知设备"

            val entry = BleDevice(name = name, address = device.address, rssi = result.rssi)
            val current = _devices.value.toMutableList()
            val index = current.indexOfFirst { it.address == entry.address }
            if (index >= 0) current[index] = entry else current.add(entry)
            // 按发现顺序排列（不再按 RSSI 重排），否则扫着扫着设备条会跳来跳去点错
            _devices.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _isScanning.value = false
            _link.value = LinkState.Error("扫描失败 ($errorCode)")
        }
    }

    // ---- connection -------------------------------------------------------

    /** 用户/界面主动连接：先停掉自动重连，再连一次。 */
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        val context = appContext ?: return
        val current = _link.value
        if (current is LinkState.Connected && current.address == device.address) return
        if (current is LinkState.Connecting) return
        cancelReconnect()
        stopScan()
        closeGatt()

        val remote = try {
            adapter?.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (remote == null) {
            _link.value = LinkState.Error("地址无效")
            return
        }

        manualDisconnect = false
        _link.value = LinkState.Connecting
        appendLog("连接 ${device.name}")
        gatt = remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** 上次连接成功的设备；没记录过返回 null。 */
    fun lastDevice(): BleDevice? {
        val p = prefs ?: return null
        val address = p.getString(KEY_LAST_ADDRESS, null) ?: return null
        val name = p.getString(KEY_LAST_NAME, null) ?: "上次设备"
        return BleDevice(name = name, address = address, rssi = 0)
    }

    /**
     * 开机自动连上次的设备（不需要先扫描）。连不上就一直在后台重试，
     * 顶栏 `[LINK]` 会橙色闪烁，用户看到可以手动接管。
     */
    fun autoConnectLast(): Boolean {
        val context = appContext ?: return false
        if (!isBluetoothEnabled()) return false
        if (!hasPermissions(context)) return false
        if (_link.value !is LinkState.Disconnected) return false
        val device = lastDevice() ?: return false
        startReconnect(device, flashLost = false)
        return true
    }

    private fun rememberDevice(address: String, name: String) {
        prefs?.edit()
            ?.putString(KEY_LAST_ADDRESS, address)
            ?.putString(KEY_LAST_NAME, name)
            ?.apply()
    }

    /**
     * 掉线/开机后的重连循环：一直试，直到连上或者用户主动接管。
     * [flashLost] = true 时先给 1.2s 的红色 `[LINK LOST]`，让掉线看得见，然后转入闪烁重连。
     */
    private fun startReconnect(device: BleDevice, flashLost: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (flashLost) {
                _link.value = LinkState.Error("连接中断")
                delay(1_200)
            }
            var attempt = 0
            while (isActive && _link.value !is LinkState.Connected) {
                attempt++
                _link.value = LinkState.Reconnecting(attempt, device.name)
                appendLog("重连第 $attempt 次 · ${device.name}")
                if (attemptConnect(device)) {
                    appendLog("重连成功")
                    break
                }
                delay(retryDelayMs(attempt))
            }
        }
    }

    private fun retryDelayMs(attempt: Int): Long = when {
        attempt <= 2 -> 1_000L
        attempt <= 5 -> 2_000L
        else -> 4_000L
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * 重连循环里的一次尝试：连接 → MTU → 服务发现，成功返回 true。
     * 8s 还没结果就 close 掉（模块没上电时 GATT 会挂 30s）。
     */
    @SuppressLint("MissingPermission")
    private suspend fun attemptConnect(device: BleDevice): Boolean {
        val context = appContext ?: return false
        if (!isBluetoothEnabled()) return false
        closeGatt()

        val remote = runCatching { adapter?.getRemoteDevice(device.address) }.getOrNull() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pendingConnect = deferred
        val opened = runCatching {
            remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
        if (opened == null) {
            pendingConnect = null
            return false
        }
        gatt = opened
        return try {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deferred.await() } == true
        } finally {
            if (pendingConnect === deferred) pendingConnect = null
            if (_link.value !is LinkState.Connected && gatt === opened) closeGatt()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        cancelReconnect()
        pendingConnect = null
        manualDisconnect = true
        Ccu.stopIoPolling()
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
            // ignore
        }
        closeGatt()
        _mtu.value = 0
        _phy.value = 0
        _link.value = LinkState.Disconnected
        appendLog("断开连接")
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        synchronized(sendLock) { writeInFlight = false }
        try {
            gatt?.close()
        } catch (_: SecurityException) {
            // ignore
        }
        gatt = null
        txChar = null
        rxChar = null
    }

    /**
     * 发送原始字节。返回 false 表示链路还没就绪。
     */
    private val sendLock = Any()
    private var writeInFlight = false
    private var writeInFlightAt = 0L

    /**
     * 发送原始字节。返回 false 表示链路还没就绪 / 上一个写还没完成。
     *
     * Android 的 GATT 同一时刻只允许一个 channel 操作，两个循环（IO 4Hz、dyn_fb 30Hz）同时写
     * 会互相把对方挤掉，所以这里串行化，且"上一次写没完成"时直接返回 false 让调用方下一拍再发。
     */
    @SuppressLint("MissingPermission")
    fun send(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = txChar ?: return false
        synchronized(sendLock) {
            // 上一次写超过 200ms 没回调，认为丢了，别再堵住发送
            if (writeInFlight && System.currentTimeMillis() - writeInFlightAt > 200L) {
                writeInFlight = false
            }
            if (writeInFlight) return false
            return try {
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(
                        c,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                    ) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    c.value = data
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
                if (ok) {
                    writeInFlight = true
                    writeInFlightAt = System.currentTimeMillis()
                    appendFrame(FrameDirection.TX, data)
                }
                ok
            } catch (e: SecurityException) {
                appendLog("发送失败：权限")
                false
            }
        }
    }

    /** 写完成回调：放开下一次写. */
    private fun onWriteDone() {
        synchronized(sendLock) { writeInFlight = false }
    }

    fun clearFrames() {
        _frames.value = emptyList()
    }

    /** 清空链路日志（顶栏 EVENT 条弹出的日志）。 */
    fun clearLog() {
        _log.value = emptyList()
    }

    private fun appendFrame(direction: FrameDirection, data: ByteArray) {
        val frame = BleFrame(System.currentTimeMillis(), direction, data.copyOf())
        val next = _frames.value + frame
        _frames.value = if (next.size > 240) next.takeLast(240) else next
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    appendLog("链路已建立，协商 MTU")
                    _link.value = LinkState.Connecting
                    try {
                        // 与旧 app 一致请求 512：带宽帧是 133B，MTU 不够会写失败
                        g.requestMtu(512)
                    } catch (_: SecurityException) {
                        g.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    appendLog("链路断开 (status=$status)")
                    Ccu.stopIoPolling()
                    Ccu.clearIo()
                    _mtu.value = 0
                    _phy.value = 0
                    closeGatt()

                    // ① 正在跑的自动重连尝试：把结果交回去，循环自己决定下一次
                    val waiter = pendingConnect
                    if (waiter != null) {
                        pendingConnect = null
                        waiter.complete(false)
                        return
                    }
                    // ② 循环在等下一轮（这次回调是超时 close 的余波），交给循环
                    if (reconnectJob?.isActive == true) return

                    if (manualDisconnect) {
                        manualDisconnect = false
                        _link.value = LinkState.Disconnected
                        return
                    }

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        // 模块掉电/超时：先红一下，再转入橙色闪烁的重连
                        _link.value = LinkState.Error("连接中断 ($status)")
                        lastDevice()?.let { startReconnect(it, flashLost = true) }
                    } else {
                        _link.value = LinkState.Disconnected
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            appendLog("MTU=$mtu")
            if (status == BluetoothGatt.GATT_SUCCESS) _mtu.value = mtu
            try {
                g.setPreferredPhy(
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_OPTION_NO_PREFERRED,
                )
            } catch (_: Exception) {
                // 1M PHY 也能用
            }
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                appendLog("服务发现失败 (status=$status)")
                val waiter = pendingConnect
                if (waiter != null) {
                    // 自动重连的一次尝试失败了：交给循环重试
                    pendingConnect = null
                    waiter.complete(false)
                    closeGatt()
                    return
                }
                _link.value = LinkState.Error("服务发现失败")
                closeGatt()
                return
            }
            txChar = service.getCharacteristic(TX_UUID)
            rxChar = service.getCharacteristic(RX_UUID)
            appendLog("服务就绪 TX=${txChar != null} RX=${rxChar != null}")

            rxChar?.let { enableNotify(g, it) }

            val name = try {
                g.device.name
            } catch (_: SecurityException) {
                null
            } ?: "IUPro"
            manualDisconnect = false
            rememberDevice(g.device.address, name)
            _link.value = LinkState.Connected(name, g.device.address)
            // 先置 Connected 再唤醒重连循环，循环醒来就能看到已连上
            pendingConnect?.complete(true)

            // 40Hz 轮询要靠短连接间隔：请求高优先级（Android 側约 11.25ms，模块可协商）
            try {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                appendLog("请求高优先级连接")
            } catch (_: Exception) {
                appendLog("连接优先级请求失败")
            }

            // 连上就开始轮询：IO 4Hz + dyn_fb 尽量 40Hz
            Ccu.startIoPolling()

            // 读回真实 PHY，顶栏不再写死 2M
            try {
                g.readPhy()
            } catch (_: Exception) {
                // 拿不到就保持 "--"
            }
        }

        override fun onPhyUpdate(g: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _phy.value = txPhy
        }

        override fun onPhyRead(g: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _phy.value = txPhy
        }

        @SuppressLint("MissingPermission")
        private fun enableNotify(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onWriteDone()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onRx(value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = try {
                characteristic.value
            } catch (_: SecurityException) {
                null
            }
            if (value != null) onRx(value)
        }
    }

    private fun onRx(value: ByteArray) {
        appendFrame(FrameDirection.RX, value)
        Ccu.onData(value)
        // 不再把每包都写进链路日志：轮询会把 EVENT / LINK LOG 刷满，
        // 原始字节在控制台的 BT RAW 面板里本来就有一份。
    }

    /** 让协议层往链路日志写一行（EVENT 条 / LINK LOG 会看到）。 */
    fun note(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val line = "${timeFmt.format(Date())}  $message"
        val next = _log.value + line
        _log.value = if (next.size > 120) next.takeLast(120) else next
    }
}
