package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.theme.ACCENT_PRESET_HUES
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.accentFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(
    hue: Float,
    onHueChange: (Float) -> Unit,
    oledBlack: Boolean,
    onOledToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("OLED BLACK", fontSize = 11.sp, color = Color(Ink.txt3))
                Switch(
                    checked = oledBlack,
                    onCheckedChange = onOledToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = accentFor(hue)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("ACCENT", fontSize = 11.sp, color = Color(Ink.txt3))
            Spacer(Modifier.height(12.dp))
            Slider(
                value = hue, onValueChange = onHueChange, valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = accentFor(hue), activeTrackColor = accentFor(hue)),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENT_PRESET_HUES.forEach { ph ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(accentFor(ph))
                            .clickableNoRipple { onHueChange(ph) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = onClick,
    )
