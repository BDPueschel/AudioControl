package com.audiocontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@Composable
fun PassbandCurve(hpf: FilterCurveSpec, lpf: FilterCurveSpec, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(Ink.bg))
            .border(1.dp, Color(Ink.line), RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PASSBAND", fontSize = 9.sp, color = Color(Ink.txt3))
            Text("${hpf.type.slopeDbPerOct} dB/oct", fontSize = 9.sp, color = Color(Ink.txt3))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
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
            listOf(hpf, lpf).forEach { fs ->
                if (!fs.bypass) {
                    val x = (curveXNorm(fs.freq.toDouble()) * w).toFloat()
                    val y = (curveYNorm(curveDb(hpf, lpf, fs.freq.toDouble())) * h).toFloat()
                    drawLine(accent.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, h), pathEffect = dash)
                    drawCircle(accent, radius = 11f, center = Offset(x, y))
                    drawCircle(Color(Ink.bg), radius = 6f, center = Offset(x, y))
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
