package com.audiocontrol.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.data.ChannelState
import com.audiocontrol.data.Settings
import com.audiocontrol.ui.ControlViewModel
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import com.audiocontrol.ui.theme.LocalOled
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Formats a step size as a compact string — whole numbers as Int, fractional with trailing
 *  zeros stripped (e.g. 1.0→"1", 0.5→"0.5", 0.25→"0.25"). */
private fun fmtStep(v: Double): String =
    if (v == floor(v)) v.toInt().toString()
    else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0')

/**
 * Rounded panel with a title/tag header row and slot for content.
 *
 * Collapse support is opt-in: when [onToggleCollapse] is non-null the header becomes clickable
 * and shows a rotating chevron. [collapsed] controls visibility; [collapsedSummary] renders a
 * compact value between the title and the chevron when the card is collapsed.
 * When [onToggleCollapse] is null the card behaves exactly as before (no chevron, content always
 * shown).
 */
@Composable
fun Card(
    title: String,
    tag: String?,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    collapsedSummary: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val oled = LocalOled.current
    val accent = LocalAccent.current
    val fill = if (oled) Color(0xFF0D0E10) else Color(Ink.panel)
    val borderColor = if (oled) accent else Color(Ink.line)
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "chevronRotation",
    )
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(
            modifier = if (onToggleCollapse != null)
                Modifier.fillMaxWidth().clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onToggleCollapse() }
            else
                Modifier.fillMaxWidth(),
            verticalAlignment = if (onToggleCollapse != null) Alignment.CenterVertically else Alignment.Bottom,
        ) {
            Text(title, fontSize = 13.sp, color = Color(Ink.txt))
            if (tag != null) { Spacer(Modifier.width(10.dp)); Text(tag, fontSize = 10.sp, color = Color(Ink.txt3)) }
            if (onToggleCollapse != null) {
                Spacer(Modifier.weight(1f))
                if (collapsed && collapsedSummary != null) {
                    collapsedSummary()
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Expand" else "Collapse",
                    tint = Color(Ink.txt3),
                    modifier = Modifier.size(18.dp).rotate(chevronRotation),
                )
            }
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
fun MasterCard(
    master: Double,
    showRail: Boolean,
    vm: ControlViewModel,
    settings: Settings,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    var editing by remember { mutableStateOf(false) }
    Card(
        title = "MASTER",
        tag = "volume",
        collapsed = collapsed,
        onToggleCollapse = onToggleCollapse,
        collapsedSummary = {
            Text(fmtDb(master) + " dB", fontSize = 13.sp, color = accent)
        },
    ) {
        StepperRow(
            value = fmtDb(master), unit = "dB", stepLabel = "${fmtStep(settings.stepMaster)} dB", positive = false,
            onMinus = { vm.nudgeMaster(-settings.stepMaster) }, onPlus = { vm.nudgeMaster(settings.stepMaster) },
            onTapValue = { editing = true },
            dragStepEnabled = !showRail,
            onDragStep = { dir -> vm.dragMaster(master + dir * settings.stepMaster, release = false) },
            onDragRelease = { vm.dragMaster(master, release = true) },
            dragSensitivity = settings.dragSensitivity,
            haptics = settings.haptics,
        )
        if (showRail) {
            Spacer(Modifier.height(16.dp))
            val frac = ((master - Ranges.MASTER.min) / (settings.masterCap - Ranges.MASTER.min)).toFloat()
            LevelRail(
                fraction = frac,
                onDrag = { f, release -> vm.dragMaster(Ranges.MASTER.min + f * (settings.masterCap - Ranges.MASTER.min), release) },
            )
        }
    }
    if (editing) ValueEditorDialog(
        initial = String.format(java.util.Locale.US, "%.1f", abs(master)),
        prefix = "−",
        onCommit = { it?.let { v -> vm.setMaster(-abs(v)) }; editing = false },
        onDismiss = { editing = false },
    )
}

@Composable
fun GainCard(
    group: String,
    gain: Double,
    showRail: Boolean,
    vm: ControlViewModel,
    settings: Settings,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    Card(
        title = "GAIN",
        tag = null,
        collapsed = collapsed,
        onToggleCollapse = onToggleCollapse,
        collapsedSummary = {
            Text(fmtDb(gain) + " dB", fontSize = 13.sp, color = Color(Ink.txt))
        },
    ) {
        StepperRow(
            value = fmtDb(gain), unit = "dB", stepLabel = fmtStep(settings.stepGain), positive = gain > 0,
            onMinus = { vm.nudgeGain(group, -settings.stepGain) }, onPlus = { vm.nudgeGain(group, settings.stepGain) },
            onTapValue = { editing = true },
            dragStepEnabled = !showRail,
            onDragStep = { dir -> vm.dragGain(group, gain + dir * settings.stepGain, release = false) },
            onDragRelease = { vm.dragGain(group, gain, release = true) },
            dragSensitivity = settings.dragSensitivity,
            haptics = settings.haptics,
        )
        if (showRail) {
            Spacer(Modifier.height(16.dp))
            val frac = ((gain - Ranges.GAIN.min) / (Ranges.GAIN.max - Ranges.GAIN.min)).toFloat()
            LevelRail(
                fraction = frac,
                onDrag = { f, release -> vm.dragGain(group, Ranges.GAIN.min + f * (Ranges.GAIN.max - Ranges.GAIN.min), release) },
            )
        }
    }
    if (editing) ValueEditorDialog(fmtDb(gain).replace("−", "-"), { it?.let { v -> vm.setGain(group, v) }; editing = false }, { editing = false })
}

@Composable
fun CrossoverCard(
    group: String,
    ch: ChannelState,
    vm: ControlViewModel,
    settings: Settings,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
) {
    val hpfLabel = if (ch.hpf.bypass) "—" else fmtHz(ch.hpf.freq)
    val lpfLabel = if (ch.lpf.bypass) "—" else fmtHz(ch.lpf.freq)
    Card(
        title = "CROSSOVER",
        tag = "L / R",
        collapsed = collapsed,
        onToggleCollapse = onToggleCollapse,
        collapsedSummary = {
            Text("HPF $hpfLabel · LPF $lpfLabel Hz", fontSize = 11.sp, color = Color(Ink.txt2))
        },
    ) {
        FilterBlock(
            label = "High-Pass", freq = ch.hpf.freq, bypass = ch.hpf.bypass, type = ch.hpf.filterType,
            onToggle = { vm.toggleHpf(group) },
            onMinus = { vm.nudgeHpf(group, -settings.stepHpf) },
            onPlus = { vm.nudgeHpf(group, settings.stepHpf) },
            onType = { vm.setHpfType(group, it) },
            onSetFreq = { vm.setHpfFreq(group, it) },
            stepLabel = "${settings.stepHpf} Hz",
            haptics = settings.haptics,
        )
        Spacer(Modifier.height(16.dp))
        FilterBlock(
            label = "Low-Pass", freq = ch.lpf.freq, bypass = ch.lpf.bypass, type = ch.lpf.filterType,
            onToggle = { vm.toggleLpf(group) },
            onMinus = { vm.nudgeLpf(group, -settings.stepLpf) },
            onPlus = { vm.nudgeLpf(group, settings.stepLpf) },
            onType = { vm.setLpfType(group, it) },
            onSetFreq = { vm.setLpfFreq(group, it) },
            stepLabel = "${settings.stepLpf} Hz",
            haptics = settings.haptics,
        )
        Spacer(Modifier.height(16.dp))
        PassbandCurve(
            hpf = FilterCurveSpec(ch.hpf.freq, ch.hpf.bypass, ch.hpf.filterType),
            lpf = FilterCurveSpec(ch.lpf.freq, ch.lpf.bypass, ch.lpf.filterType),
            onHpfDrag = { freq, release -> vm.dragHpfFreq(group, freq, release) },
            onLpfDrag = { freq, release -> vm.dragLpfFreq(group, freq, release) },
            dragSensitivity = settings.dragSensitivity,
        )
    }
}

@Composable
private fun FilterBlock(
    label: String, freq: Int, bypass: Boolean, type: FilterType,
    onToggle: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, onType: (FilterType) -> Unit,
    onSetFreq: (Int) -> Unit,
    stepLabel: String = "5 Hz",
    haptics: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), fontSize = 10.sp, color = Color(Ink.txt3))
        BypassSwitch(on = !bypass, onToggle = onToggle)
    }
    Spacer(Modifier.height(8.dp))
    StepperRow(
        value = fmtHz(freq), unit = "Hz", stepLabel = stepLabel, positive = false,
        onMinus = onMinus, onPlus = onPlus, onTapValue = { editing = true }, enabled = !bypass,
        haptics = haptics,
    )
    Spacer(Modifier.height(8.dp))
    FilterTypeDropdown(selected = type, onSelect = onType)
    if (editing) ValueEditorDialog(
        initial = freq.toString(),
        onCommit = { it?.let { v -> onSetFreq(v.roundToInt()) }; editing = false },
        onDismiss = { editing = false },
    )
}
