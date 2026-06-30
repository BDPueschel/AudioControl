package com.audiocontrol.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.ConnState
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import kotlinx.coroutines.delay

@Composable
fun ControlTopBar(
    conn: ConnState, muted: Boolean,
    onMute: () -> Unit, onReset: () -> Unit, onOpenTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().background(Color(Ink.bg)).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left group — weighted so right group never clips off-screen.
        // Dot is leftmost (always visible), title ellipsizes if space is tight.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedConnectionDot(conn)
            Spacer(Modifier.width(10.dp))
            Text(
                "AUDIO CONTROL CENTER",
                fontSize = 11.sp,
                color = Color(Ink.txt2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Right group: Mute pill, Reset pill, accent-circle theme button, settings cog.
        Row(verticalAlignment = Alignment.CenterVertically) {
            MuteButton(muted, onMute)
            Spacer(Modifier.width(6.dp))
            TwoTapReset(onReset)
            // Theme: compact accent-circle swatch button (tap → theme sheet)
            IconButton(onClick = onOpenTheme) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(accent))
            }
            // Settings cog
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Server settings",
                    tint = Color(Ink.txt2),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, onMute: () -> Unit) {
    OutlinedButton(
        onClick = onMute,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (muted) Color(Ink.err) else Color(Ink.panel),
            contentColor = if (muted) Color(Ink.txt) else Color(Ink.txt2),
        ),
    ) { Text(if (muted) "Muted" else "Mute", fontSize = 11.sp) }
}

@Composable
private fun TwoTapReset(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3_000); armed = false } }
    LaunchedEffect(done) { if (done) { delay(1_200); done = false } }
    val accent = LocalAccent.current
    OutlinedButton(
        onClick = { if (done) return@OutlinedButton; if (armed) { armed = false; done = true; onReset() } else armed = true },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (armed) Color(Ink.err).copy(alpha = 0.08f) else Color(Ink.panel),
            contentColor = when { done -> accent; armed -> Color(Ink.err); else -> Color(Ink.txt2) },
        ),
    ) { Text(if (done) "Reset ✓" else if (armed) "Tap again" else "↺ Reset", fontSize = 11.sp) }
}

@Composable
fun AnimatedConnectionDot(conn: ConnState) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "conn")
    val alpha by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a",
    )
    val (color, label) = when (conn) {
        ConnState.CONNECTED -> accent.copy(alpha = alpha) to "connected"
        ConnState.DISCONNECTED -> Color(Ink.err) to "disconnected"
        ConnState.CONNECTING -> Color(Ink.grey) to "connecting…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color(Ink.txt3))
    }
}

@Composable
fun ErrorBanner(text: String?) {
    if (text == null) return
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(Ink.err).copy(alpha = 0.12f))
            .border(1.dp, Color(Ink.err).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, fontSize = 12.sp, color = Color(Ink.err)) }
}
