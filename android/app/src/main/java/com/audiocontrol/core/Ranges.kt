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

private fun snap5(v: Int): Int = ((v + 2) / 5) * 5

fun clampHpf(freq: Int, lpfFreq: Int): Int {
    val hi = lpfFreq - Ranges.GAP
    return max(Ranges.HPF.min.toInt(), min(snap5(freq), snap5(hi)))
}

fun clampLpf(freq: Int, hpfFreq: Int): Int {
    val lo = hpfFreq + Ranges.GAP
    return min(Ranges.LPF.max.toInt(), max(snap5(freq), snap5(lo)))
}
