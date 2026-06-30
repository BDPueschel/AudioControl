package com.audiocontrol.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@Composable
fun StepperRow(
    value: String, unit: String, stepLabel: String, positive: Boolean,
    onMinus: () -> Unit, onPlus: () -> Unit, onTapValue: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    dragStepEnabled: Boolean = false,
    onDragStep: (Int) -> Unit = {},
    onDragRelease: () -> Unit = {},
) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    // rememberUpdatedState ensures the pointerInput block (keyed on Unit, never restarted)
    // always calls the latest lambda even as master/gain recomposes during a live drag.
    val latestOnDragStep by rememberUpdatedState(onDragStep)
    val latestOnDragRelease by rememberUpdatedState(onDragRelease)
    var dragging by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", stepLabel, enabled, onMinus)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .alpha(if (enabled) 1f else 0.38f).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val dragMod = if (dragStepEnabled) {
                Modifier.pointerInput(Unit) {
                    // ~28 dp per step = half the original 14 dp sensitivity
                    val thresholdPx = 28.dp.toPx()
                    var accum = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { accum = 0f; dragging = false; latestOnDragRelease() },
                        onDragCancel = { accum = 0f; dragging = false; latestOnDragRelease() },
                    ) { _, dx ->
                        dragging = true
                        accum += dx
                        while (accum >= thresholdPx) { latestOnDragStep(+1); accum -= thresholdPx }
                        while (accum <= -thresholdPx) { latestOnDragStep(-1); accum += thresholdPx }
                    }
                }
            } else Modifier

            // Floating value bubble shown above the drag area while dragging
            if (dragging && dragStepEnabled) {
                Popup(
                    alignment = Alignment.TopCenter,
                    offset = with(density) { IntOffset(0, -56.dp.roundToPx()) },
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(Ink.panel2))
                            .border(1.dp, accent, RoundedCornerShape(12.dp))
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                    ) {
                        Text(
                            "$value $unit",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Row(
                dragMod.clickable(enabled = enabled, onClick = onTapValue),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (positive) accent else Color(Ink.txt),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = Color(Ink.txt3))
            }
        }
        StepBtn("+", stepLabel, enabled, onPlus)
    }
}

@Composable
private fun StepBtn(glyph: String, stepLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(Ink.panel2)).border(1.dp, Color(Ink.line), RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color(Ink.txt))
                Text(stepLabel, fontSize = 8.sp, color = Color(Ink.txt3))
            }
        }
    }
}

@Composable
fun LevelRail(fraction: Float, onDrag: (Float, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var widthPx by remember { mutableStateOf(1f) }
    var current by remember(fraction) { mutableStateOf(fraction) }
    Box(
        modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(9.dp)).background(Color(Ink.grey))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onDrag(current, true) },
                    onDragCancel = { onDrag(current, true) },
                ) { _, dragAmount ->
                    // half-sensitivity relative drag
                    current = (current + (dragAmount / widthPx) * 0.5f).coerceIn(0f, 1f)
                    onDrag(current, false)
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(current).clip(RoundedCornerShape(9.dp)).background(accent))
        Box(
            Modifier.fillMaxWidth(current).fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(Ink.txt)))
        }
    }
}

@Composable
fun BypassSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val knobStart by animateDpAsState(if (on) 21.dp else 3.dp, label = "bypassKnob")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "ENGAGED" else "BYPASSED", fontSize = 9.sp, color = Color(Ink.txt3))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) accent else Color(Ink.grey))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggle() },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = knobStart)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(Ink.txt)),
            )
        }
    }
}

@Composable
fun ValueEditorDialog(
    initial: String,
    onCommit: (Double?) -> Unit,
    onDismiss: () -> Unit,
    prefix: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onCommit(text.toDoubleOrNull()) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            if (prefix != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prefix, color = Color(Ink.txt))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            } else {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
    )
}
