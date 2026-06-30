package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.data.ChannelState
import com.audiocontrol.ui.ControlViewModel
import com.audiocontrol.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun Card(title: String, tag: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(Ink.panel))
            .border(1.dp, Color(Ink.line), RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 13.sp, color = Color(Ink.txt))
            if (tag != null) { Spacer(Modifier.width(10.dp)); Text(tag, fontSize = 10.sp, color = Color(Ink.txt3)) }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun MasterCard(master: Double, showRail: Boolean, vm: ControlViewModel) {
    var editing by remember { mutableStateOf(false) }
    Card("MASTER", "volume") {
        StepperRow(
            value = fmtDb(master), unit = "dB", stepLabel = "1 dB", positive = false,
            onMinus = { vm.nudgeMaster(-1.0) }, onPlus = { vm.nudgeMaster(1.0) }, onTapValue = { editing = true },
            dragStepEnabled = !showRail,
            onDragStep = { dir -> vm.dragMaster(Ranges.MASTER.clampStep(master + dir * Ranges.MASTER.step), release = false) },
            onDragRelease = { vm.dragMaster(master, release = true) },
        )
        if (showRail) {
            Spacer(Modifier.height(16.dp))
            val frac = ((master - Ranges.MASTER.min) / (Ranges.MASTER.max - Ranges.MASTER.min)).toFloat()
            LevelRail(
                fraction = frac,
                onDrag = { f, release -> vm.dragMaster(Ranges.MASTER.min + f * (Ranges.MASTER.max - Ranges.MASTER.min), release) },
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
fun GainCard(group: String, gain: Double, showRail: Boolean, vm: ControlViewModel) {
    var editing by remember { mutableStateOf(false) }
    Card("GAIN", null) {
        StepperRow(
            value = fmtDb(gain), unit = "dB", stepLabel = "0.5", positive = gain > 0,
            onMinus = { vm.nudgeGain(group, -0.5) }, onPlus = { vm.nudgeGain(group, 0.5) }, onTapValue = { editing = true },
            dragStepEnabled = !showRail,
            onDragStep = { dir -> vm.dragGain(group, Ranges.GAIN.clampStep(gain + dir * Ranges.GAIN.step), release = false) },
            onDragRelease = { vm.dragGain(group, gain, release = true) },
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
fun CrossoverCard(group: String, ch: ChannelState, vm: ControlViewModel) {
    Card("CROSSOVER", "L / R") {
        FilterBlock(
            label = "High-Pass", freq = ch.hpf.freq, bypass = ch.hpf.bypass, type = ch.hpf.filterType,
            onToggle = { vm.toggleHpf(group) }, onMinus = { vm.nudgeHpf(group, -5) }, onPlus = { vm.nudgeHpf(group, 5) },
            onType = { vm.setHpfType(group, it) },
            onSetFreq = { vm.setHpfFreq(group, it) },
        )
        Spacer(Modifier.height(16.dp))
        FilterBlock(
            label = "Low-Pass", freq = ch.lpf.freq, bypass = ch.lpf.bypass, type = ch.lpf.filterType,
            onToggle = { vm.toggleLpf(group) }, onMinus = { vm.nudgeLpf(group, -5) }, onPlus = { vm.nudgeLpf(group, 5) },
            onType = { vm.setLpfType(group, it) },
            onSetFreq = { vm.setLpfFreq(group, it) },
        )
        Spacer(Modifier.height(16.dp))
        PassbandCurve(
            hpf = FilterCurveSpec(ch.hpf.freq, ch.hpf.bypass, ch.hpf.filterType),
            lpf = FilterCurveSpec(ch.lpf.freq, ch.lpf.bypass, ch.lpf.filterType),
            onHpfDrag = { freq, release -> vm.dragHpfFreq(group, freq, release) },
            onLpfDrag = { freq, release -> vm.dragLpfFreq(group, freq, release) },
        )
    }
}

@Composable
private fun FilterBlock(
    label: String, freq: Int, bypass: Boolean, type: FilterType,
    onToggle: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, onType: (FilterType) -> Unit,
    onSetFreq: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), fontSize = 10.sp, color = Color(Ink.txt3))
        BypassSwitch(on = !bypass, onToggle = onToggle)
    }
    Spacer(Modifier.height(8.dp))
    StepperRow(
        value = fmtHz(freq), unit = "Hz", stepLabel = "5 Hz", positive = false,
        onMinus = onMinus, onPlus = onPlus, onTapValue = { editing = true }, enabled = !bypass,
    )
    Spacer(Modifier.height(8.dp))
    FilterTypeDropdown(selected = type, onSelect = onType)
    if (editing) ValueEditorDialog(
        initial = freq.toString(),
        onCommit = { it?.let { v -> onSetFreq(v.roundToInt()) }; editing = false },
        onDismiss = { editing = false },
    )
}
