package com.audiocontrol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Log-space damping for horizontal drag → frequency mapping. 0.5 = half the log range per full-width drag. */
private const val DAMP = 0.5f

@Composable
fun PassbandCurve(
    hpf: FilterCurveSpec,
    lpf: FilterCurveSpec,
    modifier: Modifier = Modifier,
    onHpfDrag: ((Int, Boolean) -> Unit)? = null,
    onLpfDrag: ((Int, Boolean) -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val density = LocalDensity.current

    // rememberUpdatedState so the pointerInput(Unit) closure always reads the latest values
    // without restarting the gesture detector on every recomposition.
    val hpfState = rememberUpdatedState(hpf)
    val lpfState = rememberUpdatedState(lpf)
    val onHpfDragState = rememberUpdatedState(onHpfDrag)
    val onLpfDragState = rememberUpdatedState(onLpfDrag)

    // Drag state — plain object, not Compose state, so reads don't trigger recomposition.
    val drag = remember {
        object {
            var activeNode: String? = null  // "hpf" | "lpf" | null
            var startXNorm: Double = 0.0
            var accDx: Float = 0f
        }
    }

    // Pulse animations — one per node.
    val hpfPulse = remember { Animatable(0f) }
    val lpfPulse = remember { Animatable(0f) }
    // Plain holders (not Compose state) so updating them doesn't trigger recomposition.
    val hpfSeenHolder = remember { object { var seen = false } }
    val lpfSeenHolder = remember { object { var seen = false } }

    LaunchedEffect(hpf.freq, hpf.type) {
        if (!hpfSeenHolder.seen) { hpfSeenHolder.seen = true; return@LaunchedEffect }
        hpfPulse.snapTo(0f)
        hpfPulse.animateTo(1f, animationSpec = tween(durationMillis = 450))
    }
    LaunchedEffect(lpf.freq, lpf.type) {
        if (!lpfSeenHolder.seen) { lpfSeenHolder.seen = true; return@LaunchedEffect }
        lpfPulse.snapTo(0f)
        lpfPulse.animateTo(1f, animationSpec = tween(durationMillis = 450))
    }

    // Reading animated values in the composable scope registers state reads that drive
    // recomposition (and therefore canvas redraws) on every animation frame.
    val hpfPulseVal = hpfPulse.value
    val lpfPulseVal = lpfPulse.value

    // Header label: show both slopes when they differ.
    val slopeLabel = if (hpf.type.slopeDbPerOct == lpf.type.slopeDbPerOct)
        "${hpf.type.slopeDbPerOct} dB/oct"
    else
        "${hpf.type.slopeDbPerOct} / ${lpf.type.slopeDbPerOct} dB/oct"

    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(Ink.bg))
            .border(1.dp, Color(Ink.line), RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PASSBAND", fontSize = 9.sp, color = Color(Ink.txt3))
            Text(slopeLabel, fontSize = 9.sp, color = Color(Ink.txt3))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val h = hpfState.value
                            val l = lpfState.value
                            val w = size.width.toFloat()
                            val ht = size.height.toFloat()
                            val radiusPx = with(density) { 28.dp.toPx() }

                            // Dot screen positions (sentinel -9999 keeps bypassed nodes un-hittable).
                            val hpfX = if (!h.bypass) (curveXNorm(h.freq.toDouble()) * w).toFloat() else -9999f
                            val hpfY = if (!h.bypass) (curveYNorm(curveDb(h, l, h.freq.toDouble())) * ht).toFloat() else -9999f
                            val lpfX = if (!l.bypass) (curveXNorm(l.freq.toDouble()) * w).toFloat() else -9999f
                            val lpfY = if (!l.bypass) (curveYNorm(curveDb(h, l, l.freq.toDouble())) * ht).toFloat() else -9999f

                            val dxH = offset.x - hpfX; val dyH = offset.y - hpfY
                            val dxL = offset.x - lpfX; val dyL = offset.y - lpfY
                            val distH = sqrt(dxH * dxH + dyH * dyH)
                            val distL = sqrt(dxL * dxL + dyL * dyL)

                            // Pick the closer node within the hit radius; ties go to HPF.
                            drag.activeNode = when {
                                !h.bypass && distH <= radiusPx && (l.bypass || distH <= distL) -> {
                                    drag.startXNorm = curveXNorm(h.freq.toDouble())
                                    drag.accDx = 0f
                                    "hpf"
                                }
                                !l.bypass && distL <= radiusPx -> {
                                    drag.startXNorm = curveXNorm(l.freq.toDouble())
                                    drag.accDx = 0f
                                    "lpf"
                                }
                                else -> null
                            }
                        },
                        onDrag = { _, delta ->
                            val node = drag.activeNode ?: return@detectDragGestures
                            drag.accDx += delta.x
                            val w = size.width.toFloat()
                            // Log-damped relative mapping: accumulate px delta scaled by DAMP
                            // relative to the log-space start position.
                            val newXNorm = (drag.startXNorm + (drag.accDx / w * DAMP).toDouble())
                                .coerceIn(0.0, 1.0)
                            val targetFreq = freqAtXNorm(newXNorm).roundToInt()
                            when (node) {
                                "hpf" -> onHpfDragState.value?.invoke(targetFreq, false)
                                "lpf" -> onLpfDragState.value?.invoke(targetFreq, false)
                            }
                        },
                        onDragEnd = {
                            val node = drag.activeNode ?: return@detectDragGestures
                            val w = size.width.toFloat()
                            val newXNorm = (drag.startXNorm + (drag.accDx / w * DAMP).toDouble())
                                .coerceIn(0.0, 1.0)
                            val targetFreq = freqAtXNorm(newXNorm).roundToInt()
                            when (node) {
                                "hpf" -> onHpfDragState.value?.invoke(targetFreq, true)
                                "lpf" -> onLpfDragState.value?.invoke(targetFreq, true)
                            }
                            drag.activeNode = null
                        },
                        onDragCancel = { drag.activeNode = null },
                    )
                }
        ) {
            val w = size.width; val h = size.height
            val pts = curvePoints(hpf, lpf)
            val line = Path().apply {
                pts.forEachIndexed { i, (x, y) ->
                    val px = (x * w).toFloat(); val py = (y * h).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
            val fill = Path().apply {
                addPath(line); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, accent.copy(alpha = 0.10f))
            drawPath(line, accent, style = Stroke(width = 6f))
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(Pair(hpf, hpfPulseVal), Pair(lpf, lpfPulseVal)).forEach { (fs, pulseVal) ->
                if (!fs.bypass) {
                    val x = (curveXNorm(fs.freq.toDouble()) * w).toFloat()
                    val y = (curveYNorm(curveDb(hpf, lpf, fs.freq.toDouble())) * h).toFloat()
                    drawLine(accent.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, h), pathEffect = dash)
                    drawCircle(accent, radius = 11f, center = Offset(x, y))
                    drawCircle(Color(Ink.bg), radius = 6f, center = Offset(x, y))
                    // Pulse ring: blooms outward from the node, fading to transparent.
                    if (pulseVal > 0f) {
                        val minR = 6.dp.toPx()
                        val maxR = 22.dp.toPx()
                        val pulseR = minR + pulseVal * (maxR - minR)
                        val pulseAlpha = 0.5f * (1f - pulseVal)
                        drawCircle(
                            color = accent.copy(alpha = pulseAlpha),
                            radius = pulseR,
                            center = Offset(x, y),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20", "40", "80", "160", "320", "640 Hz").forEach {
                Text(it, fontSize = 9.sp, color = Color(Ink.txt3))
            }
        }
    }
}
