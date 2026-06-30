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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
        // Left group — dot + status label only (title removed)
        AnimatedConnectionDot(conn)
        Spacer(Modifier.weight(1f))
        // Right group: Mute(mic) · Reset(autorenew) · Theme(accent circle) · Settings(cog)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MuteIconButton(muted, onMute)
            TwoTapResetIcon(onReset)
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
private fun MuteIconButton(muted: Boolean, onMute: () -> Unit) {
    IconButton(onClick = onMute) {
        Icon(
            imageVector = if (muted) IconMicOff else IconMic,
            contentDescription = if (muted) "Muted" else "Mute",
            tint = if (muted) Color(Ink.err) else Color(Ink.txt2),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TwoTapResetIcon(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3_000); armed = false } }
    LaunchedEffect(done) { if (done) { delay(1_200); done = false } }

    val accent = LocalAccent.current
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, animationSpec = tween(600, easing = LinearEasing))
        }
    }

    val density = LocalDensity.current
    val chipOffsetY = with(density) { -40.dp.roundToPx() }

    Box {
        if (armed) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, chipOffsetY),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    Modifier
                        .background(Color(Ink.panel2), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(Ink.err), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("Tap again", fontSize = 10.sp, color = Color(Ink.err))
                }
            }
        }
        IconButton(
            onClick = {
                if (done) return@IconButton
                if (armed) { armed = false; done = true; onReset() } else armed = true
            },
        ) {
            Icon(
                imageVector = IconAutorenew,
                contentDescription = "Reset",
                tint = when {
                    done -> accent
                    armed -> Color(Ink.err)
                    else -> Color(Ink.txt2)
                },
                modifier = Modifier.size(22.dp).rotate(rotation.value),
            )
        }
    }
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
