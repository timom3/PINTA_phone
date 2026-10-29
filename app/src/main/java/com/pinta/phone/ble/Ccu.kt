package com.pinta.phone.ble

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale

/** CCU layout 里的车型号（L1_CCU.h / L1_CCU_LAYOUT_E）。 */
enum class LayoutVehicle(val code: Int, val label: String) {
    NULL(0x00, "NULL"),
    SINGLE(0x10, "SINGLE"),
    SCOOTER_1WD(0x21, "SCOOTER-1WD"),
    SCOOTER_2WD(0x22, "SCOOTER-2WD"),
    TRIKE_1WD(0x31, "TRIKE-1WD"),
    TRIKE_2FWD(0x32, "TRIKE-2FWD"),
    TRIKE_2RWD(0x33, "TRIKE-2RWD"),
    TRIKE_3WD(0x34, "TRIKE-3WD"),
    QUAD_2RWD(0x41, "QUAD-2RWD"),
    QUAD_4WD(0x42, "QUAD-4WD");

    companion object {
        fun of(value: Int): LayoutVehicle {
            val code = value and 0xFF
            return values().firstOrNull { it.code == code } ?: NULL
        }
    }
}

/** 仪表协议（LA_refData.DEVLayout.content.displaySelect）。 */
enum class LayoutDisplay(val code: Int, val label: String) {
    NULL(0x00, "NULL"),
    DU(0x01, "DU/DS4"),
    UART2_DS20(0x02, "DS20"),
    UART1_QS(0x03, "QS"),
    THROTTLE_SW(0x04, "THR-SW");

    companion object {
        fun of(value: Int): LayoutDisplay {
            val code = value and 0xFF
            return values().firstOrNull { it.code == code } ?: NULL
        }
    }
}

/** 控制器在车上的位置（_CCU_DEVPOS_E）。 */
enum class DevPos(val code: Int, val label: String) {
    NULL(0x00, "--"),
    FL(0x01, "FL"),
    FR(0x02, "FR"),
    RL(0x03, "RL"),
    RR(0x04, "RR"),
    IU(0x14, "IU");

    companion object {
        fun of(value: Int): DevPos {
            val code = value and 0xFF
            return values().firstOrNull { it.code == code } ?: NULL
        }
    }
}

/** layout_A 里的一个槽位：12 字节 UID + 1 字节 DEV_POS。 */
data class LayoutSlot(val raw: ByteArray, val pos: DevPos) {
    val bound: Boolean get() = raw.any { it.toInt() != 0 }
    val uidText: String
        get() = raw.toList().chunked(2).joinToString("-") { group ->
            group.joinToString("") { String.format(Locale.US, "%02X", it) }
        }
    val shortUid: String get() = if (bound) uidText.take(9) else "--"
}

/** 一次 layout 读取的解析结果。 */
data class Layout(
    val vehicle: LayoutVehicle,
    val display: LayoutDisplay,
    val slots: List<LayoutSlot>,
) {
    val summary: String
        get() = buildString {
            append("veh=").append(hex2(vehicle.code)).append(' ').append(vehicle.label)
            append(" disp=").append(hex2(display.code)).append(' ').append(display.label)
            slots.forEach { slot ->
                append(" | ").append(slot.pos.label).append(':').append(slot.shortUid)
            }
        }

    private fun hex2(value: Int): String = String.format(Locale.US, "%02X", value)
}

/**
 * CCU 诊断层：组帧 / 拆包 / 请求应答。
 *
 * 帧：0x48 | cmd | len | data[len] | sum8 | 0x54（sum8 = cmd..data 的累加和）
 * 目前只做蓝牙控制台用到的两条：
 *   - bandwidth  0x00 -> 0x01，133B，124B 数据原样回显后逐字节比对
 *   - layout     0x04 ['R'|'S', iuConfig, displayConfig] -> 0x05，54B
 *                = iu + display + 4 × (12B UID + DEV_POS)
 * 变量表 / ACQ 等留给后续协议层。
 */
object Ccu {

    const val HEADER = 0x48
    const val TAIL = 0x54

    private const val BANDWIDTH_Q = 0x00
    private const val BANDWIDTH_A = 0x01
    private const val LAYOUT_Q = 0x04
    private const val LAYOUT_A = 0x05
    private const val READ_VAR_Q = 0x1A
    private const val READ_VAR_A = 0x1B

    private const val BANDWIDTH_FRAME_SIZE = 133
    private const val BANDWIDTH_DATA_OFFSET = 7
    private const val BANDWIDTH_DATA_SIZE = 124

    private const val LAYOUT_DATA_SIZE = 54
    private const val LAYOUT_SLOT_COUNT = 4
    private const val LAYOUT_SLOT_SIZE = 13

    /** readVar 的 dev 字段：IUPro 自己（_CCU_DEVPOS_IUPro）。 */
    private const val DEV_IU = 0x14

    /** LA_register_type_packIU_IO_R = 200，IUPro 打包 IO 数据。 */
    const val TYPE_PACK_IU_IO = 200

    /** LA_register_type_packIU_dynFB_R = 201，IUPro 打包四电机 dyn_fb。 */
    const val TYPE_PACK_IU_DYN = 201

    /** packIU_IO 应答里的数据长度：15 字节（dev/type/addr 之后）。 */
    private const val IU_IO_PAYLOAD = 15

    /** packIU_dynFB 应答：4 轮 × 9 字节 = 36 字节（dev/type/addr 之后）。 */
    private const val IU_DYN_PAYLOAD = 36
    private const val IU_DYN_WHEEL = 9

    /** IO 轮询周期：1 秒 4 次；没应答时降到 1 秒。 */
    private const val IU_IO_PERIOD_MS = 250L
    private const val IU_IO_RETRY_MS = 1_000L

    /** dyn_fb 轮询周期：目标 40Hz；实际能跑多少取决于 BLE 连接间隔。 */
    private const val IU_DYN_PERIOD_MS = 40L

    /** dyn_fb 允许多少个请求同时在飞（流水线深度）。 */
    private const val IU_DYN_DEPTH = 2

    /** IO 请求超时（比原来的 1s 短，丢一帧不至于整秒卡住）. */
    private const val IU_IO_TIMEOUT_MS = 400L

    /** 与旧 app 一致：1s 没回应算超时。 */
    private const val TIMEOUT_MS = 1000L

    /** 没对端时最多连丢 8 帧就停，不要空跑 256 次 × 1s。 */
    private const val BANDWIDTH_MAX_MISS = 8

    private val opRead = 'R'.code.toByte()
    private val unchanged = 0xFF.toByte()

    data class Log(
        val timestamp: Long,
        val direction: FrameDirection,
        val name: String,
        val command: Int,
        val frame: ByteArray,
        val checksumOk: Boolean?,
        val latencyMs: Long?,
    ) {
        val length: Int get() = frame.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
        val dataHex: String
            get() {
                if (frame.size <= 5) return ""
                return frame.copyOfRange(3, frame.size - 2)
                    .joinToString(" ") { String.format(Locale.US, "%02X", it) }
            }
        val checksum: Int get() = frame.getOrNull(frame.size - 2)?.toInt()?.and(0xFF) ?: 0
    }

    data class Latency(
        val timestamp: Long,
        val latencyMs: Long,
    )

    data class Bandwidth(
        val isRunning: Boolean = false,
        val currentValue: Int = 0,
        val successCount: Int = 0,
        val failCount: Int = 0,
        val totalTimeMs: Long = 0,
        val bandwidthBps: Double = 0.0,
        val startTime: Long = 0,
    )

    /**
     * packIU_IO（type=200）解出来的 IUPro 内部 IO。
     *
     * 固件帧：0x48 | 0x1B | 21 | dev(0x14) | type(200) | addr(4B) | payload(15B) | sum | 0x54
     * payload：
     *  +0..1  u16  IO 位（bit0-1 gear, bit2 rev, bit3 repair, bit4 P, bit5 L, bit6 R, bit7 brake）
     *  +2..3  q1.14 转把 pu
     *  +4..5  q1.14 转向 pu
     *  +6     u8   NTC 温度（偏移 -50）
     *  +7..8  u16  2.0V REF AD
     *  +9..10 u16  IU 输入电压（0.1V）
     *  +11..12 u16 ACC 电压（0.1V）
     *  +13..14 u16 12V 电压（0.01V，固件为了提高精度改过；显示仍按 0.1V）
     */
    data class IuIo(
        val gear: Int,
        val reverse: Boolean,
        val repair: Boolean,
        val park: Boolean,
        val turnLeft: Boolean,
        val turnRight: Boolean,
        val brake: Boolean,
        val throttlePu: Float,
        val steeringPu: Float,
        val ntcC: Int,
        val ref2vAd: Int,
        val vIu: Float,
        val vAcc: Float,
        val v12: Float,
        val at: Long,
    )

    /**
     * packIU_dynFB（type=201）解出来的四电机数据。
     *
     * 固件帧：0x48 | 0x1B | 42 | dev(0x14) | type(201) | addr(4B) | payload(36B) | sum | 0x54
     * payload：每轮 9 字节，顺序 FL / FR / RL / RR
     *  +0..1 q1.14 Iq_pu
     *  +2..3 q1.14 Ibus_pu
     *  +4..5 q1.14 omega_pu
     *  +6    u8  控制器温度（偏移 -50，1℃）
     *  +7    u8  电机温度  （偏移 -50，1℃）
     *  +8    u8  errCode
     */
    data class WheelDyn(
        val iqPu: Float,
        val ibusPu: Float,
        val omegaPu: Float,
        val ctrlC: Int,
        val motorC: Int,
        val fault: Int,
    )

    data class IuDyn(
        val wheels: List<WheelDyn>,
        val at: Long,
    )

    private val _logs = MutableStateFlow<List<Log>>(emptyList())
    val logs: StateFlow<List<Log>> = _logs.asStateFlow()

    private val _latency = MutableStateFlow<List<Latency>>(emptyList())
    val latency: StateFlow<List<Latency>> = _latency.asStateFlow()

    private val _bandwidth = MutableStateFlow(Bandwidth())
    val bandwidth: StateFlow<Bandwidth> = _bandwidth.asStateFlow()

    private val _isBandwidthRunning = MutableStateFlow(false)
    val isBandwidthRunning: StateFlow<Boolean> = _isBandwidthRunning.asStateFlow()

    /** 最近一次 packIU_IO 解析结果；null = 还没收到过. */
    private val _io = MutableStateFlow<IuIo?>(null)
    val io: StateFlow<IuIo?> = _io.asStateFlow()

    /** 最近一次 packIU_dynFB 解析结果；null = 还没收到过. */
    private val _dyn = MutableStateFlow<IuDyn?>(null)
    val dyn: StateFlow<IuDyn?> = _dyn.asStateFlow()

    /** dyn_fb 实测轮询频率（Hz），每 1 秒统计一次. */
    private val _dynHz = MutableStateFlow(0f)
    val dynHz: StateFlow<Float> = _dynHz.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bandwidthJob: Job? = null
    private var ioJob: Job? = null
    private var dynJob: Job? = null
    private var lastTxAt: Long = 0L

    /** 收到的字节流（拆包用），与 pending 一起由 ioLock 保护。 */
    private val rxBuffer = ByteArrayOutputStream()
    private val ioLock = Any()

    /** 同一时刻只允许一条请求在等应答，串行化避免请求/应答错配。 */
    private val requestLock = Mutex()
    private var pending: CompletableDeferred<ByteArray?>? = null
    private var pendingCommand: Int = -1

    /** readVar 应答要求匹配的 type（-1 = 不检查）；IO 和 dyn 共用 0x1B，必须靠它区分. */
    private var pendingType: Int = -1

    /** dyn_fb 流水线：已发出、还没回收的请求数 / 已收到的应答计数. */
    @Volatile
    private var dynOutstanding = 0
    @Volatile
    private var dynRxCount = 0

    /** IO 流水线：最后一次收到 IO 应答的时间 / 当前是不是"没应答"状态. */
    @Volatile
    private var ioLastRxAt = 0L
    @Volatile
    private var ioRxCount = 0
    @Volatile
    private var ioFailing = false

    /** 刚发完一条 IO 时短暂置位，让 dyn 让开一个连接间隔. */
    @Volatile
    private var ioBusy = false

    /** 本次连接是否已经往 EVENT 日志写过一次实际刷新率. */
    @Volatile
    private var rateReported = false

    /** 当前请求是不是静默的（IO 轮询），静默请求不写 CCP 日志。 */
    private var pendingQuiet = false

    private val commandNames = mapOf(
        0x00 to "bandwidth_Q",
        0x01 to "bandwidth_A",
        0x04 to "layout_Q",
        0x05 to "layout_A",
        0x06 to "layout_posMatch_Q",
        0x07 to "layout_posMatch_A",
        0x08 to "layout_angle_Q",
        0x09 to "layout_angle_A",
        0x10 to "vReg_version_Q",
        0x11 to "vReg_version_A",
        0x12 to "vReg_content_Q",
        0x13 to "vReg_content_A",
        0x1A to "read_var_Q",
        0x1B to "read_var_A",
        0x1C to "write_var_Q",
        0x20 to "acq_set_Q",
        0x21 to "acq_set_A",
        0x22 to "acq_start_Q",
        0x23 to "acq_start_A",
        0x24 to "acq_getData_Q",
        0x25 to "acq_getData_A",
        0x30 to "refData_saveTri_Q",
        0x31 to "refData_saveTri_A",
        0x32 to "refData_getCheck_Q",
        0x33 to "refData_getCheck_A",
    )

    fun commandName(command: Int): String =
        commandNames[command] ?: String.format(Locale.US, "0x%02X", command)

    fun clearLogs() {
        _logs.value = emptyList()
        _latency.value = emptyList()
        synchronized(ioLock) { rxBuffer.reset() }
    }

    fun clearLatency() {
        _latency.value = emptyList()
    }

    // ---- layout -----------------------------------------------------------

    /** 控制台 LAYOUT 按钮：读一次整车布局，结果写进 CCP 日志。 */
    fun sendLayoutProbe() {
        if (Ble.link.value !is LinkState.Connected) return
        scope.launch { readLayout() }
    }

    /** 读 layout：发 'R' + 0xFF + 0xFF，等 layout_A（54B）。 */
    suspend fun readLayout(timeoutMs: Long = TIMEOUT_MS): Layout? {
        val frame = buildFrame(LAYOUT_Q, byteArrayOf(opRead, unchanged, unchanged))
        val response = exchange(frame, "layout_Q", LAYOUT_Q, LAYOUT_A, timeoutMs) ?: return null
        return parseLayout(response)
    }

    /** 写 layout：'S' + iuConfig + displayConfig；固件回同样的 layout_A。 */
    suspend fun writeLayout(
        vehicle: LayoutVehicle,
        display: LayoutDisplay,
        timeoutMs: Long = TIMEOUT_MS,
    ): Layout? {
        val frame = buildFrame(
            LAYOUT_Q,
            byteArrayOf(
                'S'.code.toByte(),
                vehicle.code.toByte(),
                display.code.toByte(),
            ),
        )
        val response = exchange(frame, "layout_Q(S)", LAYOUT_Q, LAYOUT_A, timeoutMs) ?: return null
        return parseLayout(response)
    }

    /** 解析 layout_A：iu + display + 4 × (12B UID + DEV_POS)，应答固定 54B。 */
    fun parseLayout(frame: ByteArray): Layout? {
        if (frame.size < LAYOUT_DATA_SIZE + 5) return null
        if ((frame[1].toInt() and 0xFF) != LAYOUT_A) return null
        if ((frame[2].toInt() and 0xFF) != LAYOUT_DATA_SIZE) return null
        if ((frame.last().toInt() and 0xFF) != TAIL) return null
        if (checksum(frame) != (frame[frame.size - 2].toInt() and 0xFF)) return null

        val vehicle = LayoutVehicle.of(frame[3].toInt())
        val display = LayoutDisplay.of(frame[4].toInt())
        val slots = (0 until LAYOUT_SLOT_COUNT).map { index ->
            val offset = 5 + index * LAYOUT_SLOT_SIZE
            LayoutSlot(
                raw = frame.copyOfRange(offset, offset + 12),
                pos = DevPos.of(frame[offset + 12].toInt()),
            )
        }
        return Layout(vehicle = vehicle, display = display, slots = slots)
    }

    // ---- bandwidth --------------------------------------------------------

    fun sendBandwidthRequest() {
        if (_isBandwidthRunning.value) {
            stopBandwidthTest()
            return
        }
        if (Ble.link.value !is LinkState.Connected) return

        _bandwidth.value = Bandwidth(isRunning = true, startTime = System.currentTimeMillis())
        _isBandwidthRunning.value = true
        bandwidthJob = scope.launch {
            var success = 0
            var fail = 0
            var consecutiveFail = 0

            for (value in 0..255) {
                if (!isActive || !_isBandwidthRunning.value) break
                _bandwidth.value = _bandwidth.value.copy(currentValue = value)

                val ok = sendBandwidthFrameAndWait(buildBandwidthFrame(value.toByte()))
                if (ok) {
                    success++
                    consecutiveFail = 0
                } else {
                    fail++
                    consecutiveFail++
                }

                if (value % 8 == 7 || value == 255) {
                    _bandwidth.value = _bandwidth.value.copy(
                        successCount = success,
                        failCount = fail,
                    )
                }
                // 没有对端时不要去跑满 256 次
                if (consecutiveFail >= BANDWIDTH_MAX_MISS) break
            }
            finishBandwidthTest()
        }
    }

    fun stopBandwidthTest() {
        if (!_isBandwidthRunning.value && bandwidthJob == null) return
        bandwidthJob?.cancel()
        bandwidthJob = null
        finishBandwidthTest()
    }

    private fun finishBandwidthTest() {
        val current = _bandwidth.value
        val elapsed = (System.currentTimeMillis() - current.startTime).coerceAtLeast(0)
        val seconds = elapsed / 1000.0
        // 每帧 128B 收发：成功次数 × 128 × 2 / 秒
        val bps = if (seconds > 0) current.successCount * 128.0 * 2.0 / seconds else 0.0
        _bandwidth.value = current.copy(
            isRunning = false,
            totalTimeMs = elapsed,
            bandwidthBps = bps,
        )
        _isBandwidthRunning.value = false
    }

    private fun buildBandwidthFrame(fill: Byte): ByteArray {
        val frame = ByteArray(BANDWIDTH_FRAME_SIZE)
        frame[0] = HEADER.toByte()
        frame[1] = BANDWIDTH_Q.toByte()
        frame[2] = (BANDWIDTH_DATA_SIZE + 4).toByte()
        frame[3] = 0x01.toByte()
        frame[4] = 0x02.toByte()
        frame[5] = 0xA0.toByte()
        frame[6] = 0xB0.toByte()
        for (i in 0 until BANDWIDTH_DATA_SIZE) {
            frame[BANDWIDTH_DATA_OFFSET + i] = fill
        }
        frame[frame.size - 2] = checksum(frame).toByte()
        frame[frame.size - 1] = TAIL.toByte()
        return frame
    }

    private suspend fun sendBandwidthFrameAndWait(frame: ByteArray): Boolean {
        val response = exchange(frame, "bandwidth_Q", BANDWIDTH_Q, BANDWIDTH_A) ?: return false
        if (response.size < BANDWIDTH_FRAME_SIZE) return false
        for (i in 0 until BANDWIDTH_DATA_SIZE) {
            if (response[BANDWIDTH_DATA_OFFSET + i] != frame[BANDWIDTH_DATA_OFFSET + i]) return false
        }
        return true
    }

    // ---- packIU_IO / packIU_dynFB 轮询 -------------------------------------

    /** 链路建立后由 Ble 调用：IO 4Hz + dyn_fb 20Hz。 */
    fun startIoPolling() {
        rateReported = false
        startIoLoop()
        startDynLoop()
    }

    /**
     * IO：4Hz，同样是"发完不等"的流水线 —— 应答到了在 handleFrame 里更新。
     *
     * 以前是发完死等应答（最多 400ms），等的时候 dyn 全停，所以每次 IO 刷新 dyn 就顿一下；
     * 现在两边互不相扰，IO 漏一帧也只是晚 250ms 再刷新，不影响 dyn。
     */
    private fun startIoLoop() {
        if (ioJob?.isActive == true) return
        ioJob = scope.launch {
            // 刚连上时通知还没使能，先等一拍，别把第一条当成"无应答"
            delay(400)
            ioLastRxAt = System.currentTimeMillis()
            ioFailing = false
            val request = buildFrame(
                READ_VAR_Q,
                byteArrayOf(DEV_IU.toByte(), TYPE_PACK_IU_IO.toByte(), 0, 0, 0, 0),
            )
            while (isActive && Ble.link.value is LinkState.Connected) {
                val started = System.currentTimeMillis()
                // 1.5s 一条都没回，才在 EVENT 里记一笔
                if (!ioFailing && started - ioLastRxAt > 1_500L) {
                    ioFailing = true
                    Ble.note("IO 轮询无应答")
                }
                // 控制台有 LAYOUT/BANDWIDTH 在等应答时先让路（pending 只给控制台用）
                if (pending == null) {
                    // 发 IO 时让 dyn 停一小段（~1 个连接间隔）：
                    // 模块同一时刻只稳得住一种应答，两种混着发会互相丢包
                    ioBusy = true
                    var sent = false
                    val giveUpAt = started + 60L
                    while (!sent && System.currentTimeMillis() < giveUpAt) {
                        sent = Ble.send(request)
                        if (!sent) delay(8L)
                    }
                    delay(40L)
                    ioBusy = false
                }
                val spent = System.currentTimeMillis() - started
                delay((IU_IO_PERIOD_MS - spent).coerceAtLeast(16L))
            }
        }
    }

    /**
     * dyn_fb：流水线跑法 —— 不等上一条应答就发下一条（最多 [IU_DYN_DEPTH] 条在飞）。
     * 一问一答要占 2 个连接间隔（≈37ms，上限 ~27Hz），流水线后只受链路带宽和固件处理速度限制。
     */
    private fun startDynLoop() {
        if (dynJob?.isActive == true) return
        dynJob = scope.launch {
            delay(400)
            val request = buildFrame(
                READ_VAR_Q,
                byteArrayOf(DEV_IU.toByte(), TYPE_PACK_IU_DYN.toByte(), 0, 0, 0, 0),
            )
            var nextSendAt = System.currentTimeMillis()
            var windowStart = System.currentTimeMillis()
            var baseline = dynRxCount
            var lastSeen = dynRxCount
            var windows = 0
            var ioLogBase = ioRxCount
            var lastRxAt = System.currentTimeMillis()

            while (isActive && Ble.link.value is LinkState.Connected) {
                val now = System.currentTimeMillis()

                if (dynRxCount != lastSeen) {
                    lastSeen = dynRxCount
                    lastRxAt = now
                }
                // 应答迟迟不回（模块忙/丢包）：把在飞计数清零，别让流水线卡死
                if (dynOutstanding > 0 && now - lastRxAt > 500L) {
                    dynOutstanding = 0
                    lastRxAt = now
                }

                if (now >= nextSendAt && dynOutstanding < IU_DYN_DEPTH && pending == null && !ioBusy) {
                    if (Ble.send(request)) {
                        dynOutstanding++
                        nextSendAt = now + IU_DYN_PERIOD_MS
                    } else {
                        nextSendAt = now + 100L
                    }
                }

                if (now - windowStart >= 1_000L) {
                    val elapsed = (now - windowStart).toFloat()
                    val dynHz = (dynRxCount - baseline) * 1_000f / elapsed
                    val ioHz = (ioRxCount - ioLogBase) * 1_000f / elapsed
                    _dynHz.value = dynHz
                    windows++
                    // 收到两种数据后，往 EVENT 日志里写一次实际频率（只写一次）
                    if (!rateReported && windows >= 2 && dynRxCount > 0 && ioRxCount > 0) {
                        rateReported = true
                        Ble.note(
                            String.format(
                                Locale.US,
                                "刷新率 dyn %.1fHz · io %.1fHz",
                                dynHz,
                                ioHz,
                            ),
                        )
                    }
                    baseline = dynRxCount
                    ioLogBase = ioRxCount
                    windowStart = now
                }

                delay(5L)
            }
            _dynHz.value = 0f
        }
    }

    fun stopIoPolling() {
        ioJob?.cancel()
        ioJob = null
        dynJob?.cancel()
        dynJob = null
        _dynHz.value = 0f
    }

    /** 掉线时清掉最后一次 IO，别让座舱显示上次的旧值。 */
    fun clearIo() {
        _io.value = null
        _dyn.value = null
    }

    /** readVar：返回应答帧的 data 段（dev / type / addr / payload）。 */
    private suspend fun readVar(
        devId: Int,
        type: Int,
        address: Int,
        timeoutMs: Long = TIMEOUT_MS,
        lockWaitMs: Long = 0L,
    ): ByteArray? {
        val data = byteArrayOf(
            devId.toByte(),
            type.toByte(),
            (address and 0xFF).toByte(),
            ((address shr 8) and 0xFF).toByte(),
            ((address shr 16) and 0xFF).toByte(),
            ((address shr 24) and 0xFF).toByte(),
        )
        val response = exchange(
            frame = buildFrame(READ_VAR_Q, data),
            name = "read_var_Q",
            command = READ_VAR_Q,
            expected = READ_VAR_A,
            timeoutMs = timeoutMs,
            quiet = true,
            lockWaitMs = lockWaitMs,
            expectedType = type,   // IO 和 dyn 共用 0x1B，必须按 type 认领
        ) ?: return null
        return response.copyOfRange(3, response.size - 2)
    }

    /** 解析 packIU_dynFB 的应答 data：dev(1) + type(1) + addr(4) + 4×9B。 */
    fun parseIuDyn(data: ByteArray): IuDyn? {
        if (data.size < 6 + IU_DYN_PAYLOAD) return null
        if ((data[1].toInt() and 0xFF) != TYPE_PACK_IU_DYN) return null
        val wheels = (0 until 4).map { i ->
            val p = 6 + i * IU_DYN_WHEEL
            WheelDyn(
                iqPu = s16(data, p) / 16384f,
                ibusPu = s16(data, p + 2) / 16384f,
                omegaPu = s16(data, p + 4) / 16384f,
                ctrlC = (data[p + 6].toInt() and 0xFF) - 50,
                motorC = (data[p + 7].toInt() and 0xFF) - 50,
                fault = data[p + 8].toInt() and 0xFF,
            )
        }
        return IuDyn(wheels = wheels, at = System.currentTimeMillis())
    }

    /** 解析 packIU_IO 的应答 data：dev(1) + type(1) + addr(4) + 15B payload。 */
    fun parseIuIo(data: ByteArray): IuIo? {
        if (data.size < 6 + IU_IO_PAYLOAD) return null
        if ((data[1].toInt() and 0xFF) != TYPE_PACK_IU_IO) return null
        val p = 6
        val bits = u16(data, p)
        return IuIo(
            gear = bits and 0x03,
            reverse = (bits and 0x0004) != 0,
            repair = (bits and 0x0008) != 0,
            park = (bits and 0x0010) != 0,
            turnLeft = (bits and 0x0020) != 0,
            turnRight = (bits and 0x0040) != 0,
            brake = (bits and 0x0080) != 0,
            // 1Q14：1 位整数 + 14 位小数
            throttlePu = s16(data, p + 2) / 16384f,
            steeringPu = s16(data, p + 4) / 16384f,
            ntcC = (data[p + 6].toInt() and 0xFF) - 50,
            ref2vAd = u16(data, p + 7),
            vIu = u16(data, p + 9) / 10f,
            vAcc = u16(data, p + 11) / 10f,
            // 12V 通道固件按 0.01V 打包（1219 = 12.19V），显示时再四舍五入到 0.1V
            v12 = u16(data, p + 13) / 100f,
            at = System.currentTimeMillis(),
        )
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, i: Int): Int = u16(b, i).toShort().toInt()

    // ---- request / response ----------------------------------------------

    /**
     * 发一帧并等指定命令的应答，超时返回 null。
     * 同一时刻只允许一个请求（LAYOUT / BANDWIDTH / IO 轮询共用）。
     *
     * @param quiet     true = 不写 CCP 日志、不记延迟（IO 轮询 4Hz，写日志会把控制台刷爆）
     * @param lockWaitMs 0 = 拿不到锁就放弃这一轮（轮询用）；>0 = 最多等这么久（控制台按钮用）
     * @param expectedType readVar 应答要匹配的 type（-1 = 只看命令字）
     */
    private suspend fun exchange(
        frame: ByteArray,
        name: String,
        command: Int,
        expected: Int,
        timeoutMs: Long = TIMEOUT_MS,
        quiet: Boolean = false,
        lockWaitMs: Long = 1_500L,
        expectedType: Int = -1,
    ): ByteArray? {
        val locked = if (lockWaitMs <= 0L) {
            requestLock.tryLock()
        } else {
            withTimeoutOrNull(lockWaitMs) { requestLock.lock(); true } ?: false
        }
        if (!locked) {
            if (!quiet) addLog(FrameDirection.RX, "$name BUSY", command, ByteArray(0), null, null)
            return null
        }
        val deferred = CompletableDeferred<ByteArray?>()
        try {
            synchronized(ioLock) {
                // rxBuffer 不能清：流水线里的 dyn_fb 应答可能正躺在里面
                pending = deferred
                pendingCommand = expected
                pendingType = expectedType
                pendingQuiet = quiet
            }
            val sent = Ble.send(frame)
            lastTxAt = System.currentTimeMillis()
            if (!quiet) addLog(FrameDirection.TX, name, command, frame, null, sent)
            if (!sent) return null
            return withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            synchronized(ioLock) {
                if (pending === deferred) pending = null
                pendingCommand = -1
                pendingType = -1
                pendingQuiet = false
            }
            requestLock.unlock()
        }
    }

    /**
     * 收到 BLE 通知：累积字节、拆帧、逐帧处理（粘包/半包都能处理）。
     * 从 Ble 的 GATT 回调线程调用。
     */
    fun onData(data: ByteArray) {
        val frames = ArrayList<ByteArray>()
        synchronized(ioLock) {
            rxBuffer.write(data)
            while (true) {
                val buf = rxBuffer.toByteArray()
                if (buf.size < 5) break

                val headerIndex = buf.indexOfHeader()
                if (headerIndex < 0) {
                    rxBuffer.reset()
                    break
                }
                if (headerIndex > 0) {
                    rxBuffer.reset()
                    rxBuffer.write(buf, headerIndex, buf.size - headerIndex)
                    continue
                }

                val total = (buf[2].toInt() and 0xFF) + 5
                if (total < 5 || total > 1024) {
                    rxBuffer.reset()
                    rxBuffer.write(buf, 1, buf.size - 1)
                    continue
                }
                if (buf.size < total) break
                if ((buf[total - 1].toInt() and 0xFF) != TAIL) {
                    // 帧尾不对，说明这个 0x48 只是数据里的巧合，往后挪一格
                    rxBuffer.reset()
                    rxBuffer.write(buf, 1, buf.size - 1)
                    continue
                }

                frames.add(buf.copyOfRange(0, total))
                rxBuffer.reset()
                rxBuffer.write(buf, total, buf.size - total)
            }
        }
        frames.forEach { handleFrame(it) }
    }

    private fun handleFrame(frame: ByteArray) {
        val command = frame[1].toInt() and 0xFF
        val checksumOk = checksum(frame) == (frame[frame.size - 2].toInt() and 0xFF)
        val latency = if (lastTxAt > 0) System.currentTimeMillis() - lastTxAt else null
        val quiet = pendingQuiet

        // readVar 的 data[1] 是 type，用来区分同一个 0x1B 是 IO(200) 还是 dyn(201)
        val varType = if (command == READ_VAR_A && frame.size > 6) frame[4].toInt() and 0xFF else -1

        // ① 流水线里的应答：不经过 pending，直接更新数据
        if (varType == TYPE_PACK_IU_IO && checksumOk) {
            parseIuIo(frame.copyOfRange(3, frame.size - 2))?.let {
                _io.value = it
                ioLastRxAt = System.currentTimeMillis()
                ioRxCount++
                if (ioFailing) {
                    ioFailing = false
                    Ble.note("IO 轮询恢复")
                }
            }
        }
        if (varType == TYPE_PACK_IU_DYN && checksumOk) {
            parseIuDyn(frame.copyOfRange(3, frame.size - 2))?.let {
                _dyn.value = it
                dynRxCount++
                dynOutstanding = (dynOutstanding - 1).coerceAtLeast(0)
            }
        }

        if (!quiet) {
            val layout = if (command == LAYOUT_A && checksumOk) parseLayout(frame) else null
            val name = if (layout != null) "layout_A ${layout.summary}" else commandName(command)

            addLog(FrameDirection.RX, name, command, frame, latency, checksumOk)
            if (latency != null) {
                val next = _latency.value + Latency(System.currentTimeMillis(), latency)
                _latency.value = if (next.size > 200) next.takeLast(200) else next
            }
        }

        val waiter = synchronized(ioLock) {
            val typeOk = pendingType < 0 || pendingType == varType
            if (pending != null && command == pendingCommand && typeOk) {
                pending.also { pending = null }
            } else {
                null
            }
        }
        waiter?.complete(frame)
    }

    private fun addLog(
        direction: FrameDirection,
        name: String,
        command: Int,
        frame: ByteArray,
        latency: Long?,
        checksumOk: Boolean? = null,
    ) {
        val entry = Log(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            name = name,
            command = command,
            frame = frame.copyOf(),
            checksumOk = checksumOk,
            latencyMs = latency,
        )
        val next = _logs.value + entry
        _logs.value = if (next.size > 200) next.takeLast(200) else next
    }

    // ---- frame helpers ----------------------------------------------------

    private fun buildFrame(command: Int, data: ByteArray): ByteArray {
        val frame = ByteArray(data.size + 5)
        frame[0] = HEADER.toByte()
        frame[1] = command.toByte()
        frame[2] = data.size.toByte()
        data.copyInto(frame, 3)
        frame[frame.size - 2] = checksum(frame).toByte()
        frame[frame.size - 1] = TAIL.toByte()
        return frame
    }

    /** Sum8：cmd..最后一个数据字节。 */
    private fun checksum(frame: ByteArray): Int {
        var sum = 0
        for (i in 1 until frame.size - 2) sum += frame[i].toInt() and 0xFF
        return sum and 0xFF
    }

    private fun ByteArray.indexOfHeader(): Int {
        for (i in indices) if ((this[i].toInt() and 0xFF) == HEADER) return i
        return -1
    }
}
