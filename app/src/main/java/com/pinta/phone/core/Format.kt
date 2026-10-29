package com.pinta.phone.core

import java.util.Locale
import kotlin.math.roundToInt

fun Float.f1(): String = String.format(Locale.US, "%.1f", this)

fun Float.f2(): String = String.format(Locale.US, "%.2f", this)

fun Float.f3(): String = String.format(Locale.US, "%.3f", this)

fun Float.f0(): String = String.format(Locale.US, "%.0f", this)

fun Float.pct(): String = "${(this * 100).roundToInt()}%"

fun Int.signed(): String = if (this > 0) "+$this" else this.toString()

fun Float.signed(decimals: Int = 0): String =
    (if (this > 0) "+" else "") + String.format(Locale.US, "%.${decimals}f", this)

/** 0.45 -> "0.45"，去掉多余的 0。 */
fun Float.trimmed(decimals: Int = 3): String =
    String.format(Locale.US, "%.${decimals}f", this).trimEnd('0').trimEnd('.')
