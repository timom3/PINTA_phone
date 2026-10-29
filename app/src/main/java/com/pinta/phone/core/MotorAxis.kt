package com.pinta.phone.core

/**
 * 座舱 03–06 四个速览窗口的 Y 轴量程（全局单例，四张图共用一个值）。
 *
 * 历史弹窗**不读这里**：它按整段快照自己算一次、打开即定格，免得弹窗里 180s 的高峰
 * 把座舱的量程顶上去（见 MotorHistoryDialog）。
 *
 * 规则：按数据绝对值分 ±1.0 / ±1.5 / ±2.0 三档。
 *  - **升档立即生效**（看到尖峰就马上放大，不用等下一帧）
 *  - **降档要等 [HOLD_MS]（默认 2s）**（尖峰滑出 7s 窗口后不马上缩回去，避免看着看着量程跳变）
 *  - 一直有大信号时每次都刷新计时，不会误降档
 *  - 断线时由 DashScreen 调 [reset] 回到 ±1.0
 */
object MotorAxis {

    /** 最近一次出现"高档数据"之后，量程至少保持这么久。 */
    const val HOLD_MS = 2_000L

    @Volatile
    private var range = 1f

    @Volatile
    private var lastHighAt = 0L

    fun current(): Float = range

    @Synchronized
    fun update(maxAbs: Float, now: Long = System.currentTimeMillis()): Float {
        val need = autoAxisRange(maxAbs)
        when {
            need > range -> {
                range = need
                lastHighAt = now
            }
            need == range -> lastHighAt = now
            else -> if (now - lastHighAt > HOLD_MS) range = need
        }
        return range
    }

    /** 断线/重新连接时回到默认档。 */
    @Synchronized
    fun reset() {
        range = 1f
        lastHighAt = 0L
    }
}

