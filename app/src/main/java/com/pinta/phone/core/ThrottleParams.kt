package com.pinta.phone.core

import kotlin.math.roundToInt

/**
 * IU 的油门 refData，对应固件 LA_refData.throttle.content。
 *
 * 曲线算法照抄 L1_throttle.c 的 L1_THR_curve()：
 *  - AD_min = release + lowDZ，AD_max = depress - highDZ，再线性归一化到 [0,1]
 *  - 两段折线：(0,0) -> (tpX,tpY) -> (1,1)
 *  - 输出若落在 (0, 0.02) 区间会被抬到 0.02，大于 1 则截到 1
 *  - UV / OV 是故障阈值，不参与曲线形状
 */
data class ThrottleParams(
    val uv: Int = 200,
    val release: Int = 220,
    val depress: Int = 3850,
    val ov: Int = 4000,
    val lowDz: Int = 60,
    val highDz: Int = 80,
    val tpX: Float = 0.45f,
    val tpY: Float = 0.52f,
) {
    val adMin: Int get() = release + lowDz
    val adMax: Int get() = depress - highDz
    val adRange: Int get() = (adMax - adMin).coerceAtLeast(1)

    fun normalized(ad: Float): Float = ((ad - adMin) / adRange).coerceIn(0f, 1f)

    fun curve(x: Float): Float {
        val cx = x.coerceIn(0f, 1f)
        val y = if (cx <= tpX) {
            if (tpX <= 0.0001f) 1f else (cx / tpX) * tpY
        } else {
            tpY + (cx - tpX) / ((1f - tpX).coerceAtLeast(0.0001f)) * (1f - tpY)
        }
        return when {
            y > 1f -> 1f
            y > 0f && y < 0.02f -> 0.02f
            y < 0f -> 0f
            else -> y
        }
    }

    fun outputForAd(ad: Float): Float = curve(normalized(ad))

    fun isFault(ad: Float): Boolean = ad < uv || ad > ov

    fun q14(value: Float): Int = (value.coerceIn(0f, 1f) * 16383f).roundToInt()

    companion object {
        const val AD_MAX = 4095
        val Default = ThrottleParams()
    }
}
