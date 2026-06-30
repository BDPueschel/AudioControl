package com.audiocontrol.core

import kotlin.math.abs

fun fmtDb(v: Double): String {
    val sign = if (v > 0) "+" else if (v < 0) "−" else ""
    return sign + String.format("%.1f", abs(v))
}

fun fmtHz(v: Int): String = v.toString()
