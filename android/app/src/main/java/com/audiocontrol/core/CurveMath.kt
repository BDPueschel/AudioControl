package com.audiocontrol.core

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class FilterCurveSpec(val freq: Int, val bypass: Boolean, val type: FilterType)

fun curveDb(hpf: FilterCurveSpec, lpf: FilterCurveSpec, f: Double): Double {
    var d = 0.0
    if (!hpf.bypass) d += -10.0 * log10(1.0 + (hpf.freq / f).pow(hpf.type.exponent))
    if (!lpf.bypass) d += -10.0 * log10(1.0 + (f / lpf.freq).pow(lpf.type.exponent))
    return d
}

fun curveXNorm(f: Double): Double = ln(f / 20.0) / ln(32.0)

fun curveYNorm(db: Double): Double = min(30.0, max(0.0, -db)) / 30.0

fun curvePoints(hpf: FilterCurveSpec, lpf: FilterCurveSpec, n: Int = 160): List<Pair<Double, Double>> =
    (0..n).map { i ->
        val frac = i.toDouble() / n
        val f = 20.0 * 32.0.pow(frac)
        frac to curveYNorm(curveDb(hpf, lpf, f))
    }

/** Inverse of [curveXNorm]: maps normalised x in [0,1] back to frequency in Hz (20–640 Hz). */
fun freqAtXNorm(xNorm: Double): Double = 20.0 * 32.0.pow(xNorm.coerceIn(0.0, 1.0))
