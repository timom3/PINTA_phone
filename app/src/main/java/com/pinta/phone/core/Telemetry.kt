package com.pinta.phone.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 仪表盘一帧实时数据。字段与 IU 的真实来源一一对应：
 *  - vbus / ibus / tempC  <- UOP.IO.VbusIU_01V / dyn_fb.Ibus_pu / UOP.IO.temperature_C
 *  - wheels               <- UOP.POC.throttle_pu[4]
 *  - throttle             <- UOP.IO.throttle_pu
 *  - steerDeg             <- stea.angle_output
 *  - faultCode            <- dyn_fb.errCode
 *  - speedKph             <- omega_pu 经轮径/减速比换算（app 侧配置）
 */
data class Telemetry(
    val speedKph: Float = 0f,
    val vbus: Float = 0f,
    val ibus: Float = 0f,
    val tempC: Float = 0f,
    val wheels: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** dyn_fb[n].tptMotor, motor temperature. */
    val wheelTemps: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** dyn_fb[n].tptCtrl, controller temperature. */
    val controllerTemps: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** dyn_fb[n].errCode, one flag per motor. */
    val motorFaultCodes: List<Int> = listOf(0, 0, 0, 0),
    /** dyn_fb[n].Iq_pu × 135 A; n = FL, FR, RL, RR. */
    val motorIq: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** dyn_fb[n].Ibus_pu × 40 A. */
    val motorIbus: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** dyn_fb[n].omega_pu × 2000 rpm. */
    val motorOmega: List<Float> = listOf(0f, 0f, 0f, 0f),
    val throttle: Float = 0f,
    val steerDeg: Float = 0f,
    val faultCode: Int = 0,

    // ---- IUPro internal IO ------------------------------------------------
    /** 1/2/3 gear; 0 = unknown. */
    val gear: Int = 1,
    val reverse: Boolean = false,
    val repair: Boolean = false,
    val park: Boolean = false,
    val turnLeft: Boolean = false,
    val turnRight: Boolean = false,
    val brake: Boolean = false,
    /** Throttle ADC, 0..5 V. */
    val throttleVolt: Float = 0f,
    /** Steering sensor ADC, 0..5 V. */
    val steerVolt: Float = 2.5f,
    /** 2.0 V reference ADC, 4 digits. */
    val ref2vAd: Int = 0,
    /** IU battery voltage input. */
    val vIu: Float = 0f,
    /** Controller ACC voltage. */
    val vAcc: Float = 0f,
    /** 12 V rail. */
    val v12: Float = 0f,
) {
    val outputAvg: Float
        get() = if (wheels.isEmpty()) 0f else wheels.reduce { a, b -> a + b } / wheels.size
}

/**
 * 原型阶段的演示数据源。接上真机后由 CCU 采样引擎替换，UI 不用改。
 */
@Composable
fun rememberDemoTelemetry(): State<Telemetry> = produceState(Telemetry(), Unit) {
    var t = 0.0
    var speed = 0f
    while (true) {
        delay(60)
        t += 0.06

        val breath = (sin(t * 0.42 - PI / 2.0) + 1.0) / 2.0          // 0..1，缓慢呼吸
        val throttle = (0.10 + 0.90 * breath * breath).toFloat().coerceIn(0f, 1f)

        val factors = listOf(0.97f, 0.95f, 1.0f, 0.99f)
        val wheels = factors.map { f ->
            (throttle * f + (Random.nextFloat() - 0.5f) * 0.012f).coerceIn(0f, 1f)
        }

        speed += ((throttle * 42f) - speed) * 0.08f
        val ibus = 5.5f + throttle * 63f + (Random.nextFloat() - 0.5f) * 0.8f
        val motorIq = factors.map { f ->
            (throttle * 135f * f + (Random.nextFloat() - 0.5f) * 2.0f).coerceAtLeast(0f)
        }
        val motorIbus = factors.map { f ->
            (ibus * 0.24f * f + (Random.nextFloat() - 0.5f) * 0.4f).coerceAtLeast(0f)
        }
        val motorOmega = factors.map { f ->
            (speed * 85f * f + (Random.nextFloat() - 0.5f) * 8f).coerceAtLeast(0f)
        }
        val steerDeg = (sin(t * 0.31) * 15.0).toFloat()
        val vbus = 72.4f - throttle * 4.1f + (Random.nextFloat() - 0.5f) * 0.06f
        // Demo only: exercise every key so no icon stays dark forever.
        val demoGear = 1 + ((t / 2.0).toInt() % 3)
        val demoAux = ((t / 1.5).toInt() % 6)

        value = Telemetry(
            speedKph = speed,
            vbus = vbus,
            ibus = ibus,
            tempC = 25.1f + throttle * 8.5f + (Random.nextFloat() - 0.5f) * 0.2f,
            wheels = wheels,
            wheelTemps = listOf(41.2f, 40.4f, 45.1f, 43.6f).map {
                it + throttle * 6f + (Random.nextFloat() - 0.5f) * 0.3f
            },
            controllerTemps = listOf(36.8f, 36.2f, 39.1f, 38.4f).map {
                it + throttle * 5f + (Random.nextFloat() - 0.5f) * 0.3f
            },
            motorFaultCodes = listOf(0, 0, 0, 0),
            motorIq = motorIq,
            motorIbus = motorIbus,
            motorOmega = motorOmega,
            throttle = throttle,
            steerDeg = steerDeg,
            faultCode = 0,
            gear = demoGear,
            reverse = demoAux == 0,
            repair = demoAux == 1,
            park = demoAux == 2,
            turnLeft = demoAux == 3,
            turnRight = demoAux == 4,
            brake = demoAux == 5,
            throttleVolt = (throttle * 5f + (Random.nextFloat() - 0.5f) * 0.02f).coerceIn(0f, 5f),
            steerVolt = (2.5f + steerDeg / 30f * 2.5f).coerceIn(0f, 5f),
            ref2vAd = 1234 + Random.nextInt(-4, 5),
            vIu = vbus,
            vAcc = vbus - 0.6f + (Random.nextFloat() - 0.5f) * 0.1f,
            v12 = 11.9f + (Random.nextFloat() - 0.5f) * 0.1f,
        )
    }
}

/**
 * 迷你曲线用的滚动缓冲：只保留最近 [capacity] 个采样。
 *
 * [tick] 一变就压入一个新采样（默认跟 [value] 走）。实时数据用帧时间戳当 tick，
 * 数值不变也会继续推进；否则曲线会"一卡一卡"，只有数值变了才刷新。
 */
@Composable
fun rememberTrace(value: Float, tick: Any? = value, capacity: Int = 80): List<Float> {
    val buffer = remember { ArrayDeque<Float>(capacity) }
    val snapshot = remember { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(tick) {
        buffer.addLast(value)
        while (buffer.size > capacity) buffer.removeFirst()
        snapshot.value = buffer.toList()
    }
    return snapshot.value
}
