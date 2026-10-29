package com.pinta.phone.core

/**
 * 按数据绝对值挑 Y 轴量程：默认 ±1.0，有数据落在 1.0–1.5 用 ±1.5，
 * 超过 1.5（2.0 以内）用 ±2.0。座舱速览和 180s 历史都用它，保证同一个尺度。
 */
fun autoAxisRange(maxAbs: Float): Float = when {
    maxAbs > 1.5f -> 2.0f
    maxAbs > 1.0f -> 1.5f
    else -> 1.0f
}

/**
 * 一帧四电机历史采样（pu 值，未做 SI 换算）。
 * 只放内存，app 退出即消失。
 */
data class MotorSample(
    val t: Long,
    val iqPu: FloatArray,
    val ibusPu: FloatArray,
    val omegaPu: FloatArray,
    val ctrlC: IntArray,
    val motorC: IntArray,
    val fault: IntArray,
)

/**
 * 四电机历史环形缓冲：约 180 秒（20Hz × 180 = 3600 帧）。
 * 座舱速览窗口只取最近几百帧，详情弹窗读全量。
 */
object MotorHistory {

    /** 180s @ 20Hz。 */
    const val CAPACITY = 3600

    private val samples = ArrayDeque<MotorSample>(CAPACITY + 1)

    @Synchronized
    fun record(
        t: Long,
        iqPu: FloatArray,
        ibusPu: FloatArray,
        omegaPu: FloatArray,
        ctrlC: IntArray,
        motorC: IntArray,
        fault: IntArray,
    ) {
        samples.addLast(MotorSample(t, iqPu, ibusPu, omegaPu, ctrlC, motorC, fault))
        while (samples.size > CAPACITY) samples.removeFirst()
    }

    /** 拷贝一份给详情弹窗用（弹窗里的数据在打开那一刻定格）。 */
    @Synchronized
    fun snapshot(): List<MotorSample> = samples.toList()

    @Synchronized
    fun clear() {
        samples.clear()
    }

    @Synchronized
    fun size(): Int = samples.size
}
