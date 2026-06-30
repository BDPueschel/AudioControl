package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.SettingsActions
import com.audiocontrol.core.FilterType
import com.audiocontrol.data.Settings
import com.audiocontrol.data.normalizeHost
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: Settings, actions: SettingsActions, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    var hostText by remember { mutableStateOf(settings.host) }
    var capConfirmPending by remember { mutableStateOf<Double?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            // ── SERVER ──────────────────────────────────────────────
            SettingsSection("SERVER") {
                OutlinedTextField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    singleLine = true,
                    label = { Text("host:port") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { actions.setHost(normalizeHost(hostText)); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }

            // ── STEP SIZES ──────────────────────────────────────────
            SettingsSection("STEP SIZES") {
                StepRow(
                    label = "Master",
                    options = listOf("0.5", "1", "2"),
                    currentValue = settings.stepMaster,
                    accent = accent,
                ) { actions.setStepMaster(it.toDouble()) }
                Spacer(Modifier.height(10.dp))
                StepRow(
                    label = "Gain",
                    options = listOf("0.25", "0.5", "1"),
                    currentValue = settings.stepGain,
                    accent = accent,
                ) { actions.setStepGain(it.toDouble()) }
                Spacer(Modifier.height(10.dp))
                StepRow(
                    label = "High-pass",
                    options = listOf("1", "5", "10"),
                    currentValue = settings.stepHpf.toDouble(),
                    accent = accent,
                ) { actions.setStepHpf(it.toInt()) }
                Spacer(Modifier.height(10.dp))
                StepRow(
                    label = "Low-pass",
                    options = listOf("1", "5", "10"),
                    currentValue = settings.stepLpf.toDouble(),
                    accent = accent,
                ) { actions.setStepLpf(it.toInt()) }
            }

            // ── MASTER CAP ─────────────────────────────────────────
            SettingsSection("MASTER CAP") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${String.format("%.1f", settings.masterCap)} dB",
                        color = Color(Ink.txt),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val newVal = settings.masterCap - 1.0
                                if (newVal >= -40.0) actions.setMasterCap(newVal)
                            },
                        ) { Text("−") }
                        OutlinedButton(
                            onClick = {
                                val newVal = settings.masterCap + 1.0
                                if (newVal <= -5.0) {
                                    if (newVal > -20.0) {
                                        capConfirmPending = newVal
                                    } else {
                                        actions.setMasterCap(newVal)
                                    }
                                }
                            },
                        ) { Text("+") }
                    }
                }
                Text(
                    "Maximum output ceiling (−40 to −5 dB)",
                    fontSize = 11.sp,
                    color = Color(Ink.txt3),
                )
            }

            // ── DISPLAY ─────────────────────────────────────────────
            SettingsSection("DISPLAY") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Keep screen awake", color = Color(Ink.txt))
                    Switch(
                        checked = settings.keepAwake,
                        onCheckedChange = actions.setKeepAwake,
                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Orientation", color = Color(Ink.txt), modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("auto" to "Auto", "portrait" to "Portrait", "landscape" to "Land.").forEach { (key, label) ->
                            SegmentedChip(
                                label = label,
                                selected = settings.orientation == key,
                                accent = accent,
                                onClick = { actions.setOrientation(key) },
                            )
                        }
                    }
                }
            }

            // ── DEFAULT FILTER ──────────────────────────────────────
            SettingsSection("DEFAULT FILTER") {
                FilterTypeDropdown(
                    selected = FilterType.fromWire(settings.defaultFilterType),
                    onSelect = { actions.setDefaultFilterType(it.wire) },
                )
            }

            // ── FEEL ────────────────────────────────────────────────
            SettingsSection("FEEL") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Haptics", color = Color(Ink.txt))
                    Switch(
                        checked = settings.haptics,
                        onCheckedChange = actions.setHaptics,
                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Drag sensitivity", color = Color(Ink.txt), modifier = Modifier.weight(1f))
                    Text(
                        "${(settings.dragSensitivity * 100).toInt()}%",
                        color = Color(Ink.txt2),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Slider(
                    value = settings.dragSensitivity,
                    onValueChange = actions.setDragSensitivity,
                    valueRange = 0.2f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Master cap raise-above-−20 confirm dialog — rendered outside the sheet so it overlays it
    capConfirmPending?.let { pending ->
        AlertDialog(
            onDismissRequest = { capConfirmPending = null },
            title = { Text("Raise volume ceiling?") },
            text = { Text("This allows louder output to your subs.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.setMasterCap(pending)
                    capConfirmPending = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { capConfirmPending = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 11.sp, color = Color(Ink.txt3), letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * A single step-size row: label on the left, segmented chip group on the right.
 * [currentValue] is the stored Double value; options are strings (e.g. "0.5", "1", "10").
 * Comparison is done by converting both to Double so "1" matches 1.0.
 */
@Composable
private fun StepRow(
    label: String,
    options: List<String>,
    currentValue: Double,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(Ink.txt2), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt ->
                SegmentedChip(
                    label = opt,
                    selected = opt.toDoubleOrNull() == currentValue,
                    accent = accent,
                    onClick = { onSelect(opt) },
                )
            }
        }
    }
}

@Composable
private fun SegmentedChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent else Color.Transparent)
            .then(
                if (!selected) Modifier.border(1.dp, Color(Ink.line), shape)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Color(Ink.bg) else Color(Ink.txt2),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
