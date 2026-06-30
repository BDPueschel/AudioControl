@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.audiocontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.FilterCurveSpec
import com.audiocontrol.core.curvePoints
import com.audiocontrol.data.DspState
import com.audiocontrol.data.Scene
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import com.audiocontrol.ui.theme.LocalOled

/**
 * Small Canvas drawing the SUBS passband shape from [dsp].
 * Draws a filled accent path (alpha ~0.15) under a stroked accent curve (width 2f).
 * No labels, no markers. Caller controls size via [modifier].
 */
@Composable
fun SceneThumbnail(dsp: DspState, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val hpf = FilterCurveSpec(dsp.subs.hpf.freq, dsp.subs.hpf.bypass, dsp.subs.hpf.filterType)
    val lpf = FilterCurveSpec(dsp.subs.lpf.freq, dsp.subs.lpf.bypass, dsp.subs.lpf.filterType)
    // Memoize curve computation — FilterCurveSpec is a data class so equality works as a key.
    val pts = remember(hpf, lpf) { curvePoints(hpf, lpf, n = 60) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (pts.isEmpty()) return@Canvas

        val linePath = Path().apply {
            pts.forEachIndexed { i, (x, y) ->
                val px = (x * w).toFloat()
                val py = (y * h).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fillPath, accent.copy(alpha = 0.15f))
        drawPath(linePath, accent, style = Stroke(width = 2f))
    }
}

/**
 * A small rounded card (~84dp wide) showing [scene]'s mini passband thumbnail on top
 * and the scene name (one line, ellipsized) below.
 * Border is [LocalAccent] when [isCurrent], [Ink.line] otherwise.
 * Tap calls [onTap]; long-press calls [onLongPress].
 */
@Composable
fun SceneChip(
    scene: Scene,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val oled = LocalOled.current
    val fill = if (oled) Color(0xFF0D0E10) else Color(Ink.panel)
    val borderColor = if (isCurrent) accent else Color(Ink.line)

    Column(
        modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SceneThumbnail(
            dsp = scene.dsp,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = scene.name,
            fontSize = 12.sp,
            color = Color(Ink.txt),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Horizontally scrollable strip of scene chips with a trailing "+ Save" chip.
 *
 * - Tap a chip → [onApply].
 * - Long-press a chip → dropdown with Overwrite / Rename / Delete.
 * - Tap "+ Save" → name dialog → [onSave] (blank names are ignored).
 * - [isCurrent] is determined by data-class equality: [currentDsp] == scene.dsp.
 * - When [scenes] is empty, shows just the save chip + a muted hint caption.
 */
@Composable
fun ScenesBar(
    scenes: List<Scene>,
    currentDsp: DspState?,
    onApply: (Scene) -> Unit,
    onSave: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which scene chip's dropdown is open (by scene name, or null = none).
    var menuForScene by remember { mutableStateOf<String?>(null) }
    // Save dialog (new scene).
    var showSaveDialog by remember { mutableStateOf(false) }
    // Rename dialog (existing scene old name, or null = not shown).
    var renameTarget by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "SCENES",
            fontSize = 10.sp,
            color = Color(Ink.txt3),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scenes.forEach { scene ->
                // Box anchors the DropdownMenu to the chip.
                Box {
                    SceneChip(
                        scene = scene,
                        isCurrent = currentDsp == scene.dsp,
                        onTap = { onApply(scene) },
                        onLongPress = { menuForScene = scene.name },
                    )
                    DropdownMenu(
                        expanded = menuForScene == scene.name,
                        onDismissRequest = { menuForScene = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Overwrite") },
                            onClick = { onSave(scene.name); menuForScene = null },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { renameTarget = scene.name; menuForScene = null },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { onDelete(scene.name); menuForScene = null },
                        )
                    }
                }
            }
            // Trailing "+ Save" chip — always visible as the primary affordance.
            SaveChip(onClick = { showSaveDialog = true })
        }

        // Empty-state hint below the chip row.
        if (scenes.isEmpty()) {
            Text(
                text = "Save your current setup as a scene",
                fontSize = 11.sp,
                color = Color(Ink.txt3),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    // --- Dialogs (rendered outside the Column so they float above everything) ---

    if (showSaveDialog) {
        SceneNameDialog(
            title = "Save Scene",
            initial = "",
            confirmLabel = "Save",
            onConfirm = { name ->
                if (name.isNotBlank()) onSave(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    renameTarget?.let { oldName ->
        SceneNameDialog(
            title = "Rename Scene",
            initial = oldName,
            confirmLabel = "Rename",
            onConfirm = { newName ->
                if (newName.isNotBlank()) onRename(oldName, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/** Accent-tinted chip that opens the save dialog. Visually matches [SceneChip] height. */
@Composable
private fun SaveChip(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val oled = LocalOled.current
    val fill = if (oled) Color(0xFF0D0E10) else Color(Ink.panel)

    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, Color(Ink.line), RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mirror the SceneChip interior height: thumbnail (40dp) + spacer (4dp) + name row.
        Spacer(Modifier.height(8.dp))
        Text("+", fontSize = 24.sp, color = accent, lineHeight = 28.sp)
        Text("Save", fontSize = 12.sp, color = accent)
        Spacer(Modifier.height(10.dp))
    }
}

/** AlertDialog with a text-field for entering/editing a scene name. */
@Composable
private fun SceneNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Reset text when the dialog reopens with a different initial value (e.g. rename target changes).
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Scene name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
