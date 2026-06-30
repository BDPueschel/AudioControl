package com.audiocontrol.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object Ink {
    const val bg = 0xFF0D0E10
    const val panel = 0xFF16181B
    const val panel2 = 0xFF1D2024
    const val line = 0xFF2A2E33
    const val grey = 0xFF3A3F46
    const val txt = 0xFFE9EBEE
    const val txt2 = 0xFF9AA0A8
    const val txt3 = 0xFF6B7178
    const val err = 0xFFE06B6B
}

private const val ACCENT_S = 0.58f
private const val ACCENT_L = 0.61f

fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

fun accentFor(hue: Float): Color = Color(hslToArgb(hue, ACCENT_S, ACCENT_L))

val ACCENT_PRESET_HUES: List<Float> = listOf(189f, 16f, 145f, 280f, 45f)

val LocalAccent = compositionLocalOf { accentFor(189f) }
