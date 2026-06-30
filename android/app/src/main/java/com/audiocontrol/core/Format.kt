package com.audiocontrol.core

import kotlin.math.abs
import java.util.Locale

fun fmtDb(v: Double): String {
    val sign = if (v > 0) "+" else if (v < 0) "−" else ""
    return sign + String.format(Locale.US, "%.1f", abs(v))
}

fun fmtHz(v: Int): String = v.toString()
