package com.audiocontrol.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Range(val min: Double, val max: Double, val step: Double)

object Ranges {
    val MASTER = Range(-60.0, -20.0, 1.0)
    val GAIN = Range(-24.0, 12.0, 0.5)
    val HPF = Range(20.0, 400.0, 5.0)
    val LPF = Range(40.0, 500.0, 5.0)
    const val GAP = 10
}

fun Range.clampStep(value: Double): Double {
    val clamped = max(min, min(max, value))
    return (clamped / step).roundToInt() * step
}

/** Clamp [value] to [[min],[max]] then snap to the nearest multiple of [step]. */
fun clampSnap(value: Double, min: Double, max: Double, step: Double): Double {
    val clamped = value.coerceIn(min, max)
    return (clamped / step).roundToInt() * step
}

private fun snapN(v: Int, step: Int): Int = ((v + step / 2) / step) * step

fun clampHpf(freq: Int, lpfFreq: Int, step: Int = 5): Int {
    val hi = lpfFreq - Ranges.GAP
    return max(Ranges.HPF.min.toInt(), min(snapN(freq, step), snapN(hi, step)))
}

fun clampLpf(freq: Int, hpfFreq: Int, step: Int = 5): Int {
    val lo = hpfFreq + Ranges.GAP
    return min(Ranges.LPF.max.toInt(), max(snapN(freq, step), snapN(lo, step)))
}
