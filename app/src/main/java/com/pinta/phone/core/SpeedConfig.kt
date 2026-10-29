package com.pinta.phone.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 车速换算的设置。
 *
 * dyn_fb 只给 omega_pu（q1.14），要换成 km/h 需要两个标定量：
 *  - [rpmPerPu]：omega_pu = 1.0 对应多少 rpm
 *  - [wheelCircCm]：轮周长（厘米）
 * 另外双驱/四驱只有部分轮子在实际转动，没转的轮子 speed=0 会把"最低速"拉成 0，
 * 所以每个轮子可以单独勾选是否参与计算。
 */
data class SpeedConfig(
    val enabled: List<Boolean> = listOf(true, true, true, true),
    val rpmPerPu: Float = 2000f,
    val wheelCircCm: Int = 160,
) {
    /**
     * 取勾选轮子里转速最低的那个算车速（转弯时外侧轮更快，用最慢的当车体速度）。
     * 返回 null 表示没有可用轮子/参数不合法。
     */
    fun speedKph(omegaPu: List<Float>): Float? {
        if (rpmPerPu <= 0f || wheelCircCm <= 0) return null
        var slowest = Float.MAX_VALUE
        for (i in 0 until 4) {
            if (!enabled.getOrElse(i) { false }) continue
            val rpm = abs(omegaPu.getOrElse(i) { 0f }) * rpmPerPu
            if (rpm < slowest) slowest = rpm
        }
        if (slowest == Float.MAX_VALUE) return null
        // rpm × 周长(cm) = cm/min；×60/100000 = km/h
        return slowest * wheelCircCm * 0.0006f
    }
}

/** 车速标定的持久化（SharedPreferences：pinta_speed）。 */
object SpeedSettings {

    private const val PREFS = "pinta_speed"
    private const val KEY_RPM = "rpm_per_pu"
    private const val KEY_CIRC = "circ_cm"
    private const val KEY_EN = "enabled"

    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(SpeedConfig())
    val config: StateFlow<SpeedConfig> = _config.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val connected = p.getBoolean("${KEY_EN}0", true)
        _config.value = SpeedConfig(
            enabled = List(4) { p.getBoolean("$KEY_EN$it", connected) },
            rpmPerPu = p.getFloat(KEY_RPM, 2000f),
            wheelCircCm = p.getInt(KEY_CIRC, 160),
        )
    }

    fun update(config: SpeedConfig) {
        _config.value = config
        prefs?.edit()
            ?.putFloat(KEY_RPM, config.rpmPerPu)
            ?.putInt(KEY_CIRC, config.wheelCircCm)
            ?.also { e -> config.enabled.forEachIndexed { i, on -> e.putBoolean("$KEY_EN$i", on) } }
            ?.apply()
    }
}
