package com.audiocontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.SettingsActions
import com.audiocontrol.data.Settings
import com.audiocontrol.ui.components.*
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    vm: ControlViewModel,
    settings: Settings,
    settingsActions: SettingsActions,
    onHueChange: (Float) -> Unit,
    onOledChange: (Boolean) -> Unit,
    selectGroup: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var showTheme by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pull = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ControlTopBar(
            conn = ui.conn,
            muted = ui.dsp?.mute == true,
            onMute = vm::toggleMute,
            onReset = vm::reset,
            onOpenTheme = { showTheme = true },
            onOpenSettings = { showSettings = true },
        )
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = vm::refresh,
            state = pull,
            modifier = Modifier.weight(1f),
        ) {
            BoxWithConstraints {
                val expanded = maxWidth >= 600.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ErrorBanner(ui.errorBanner)
                    val dsp = ui.dsp
                    if (dsp == null) {
                        Text("Loading…", color = Color(Ink.txt3))
                        return@Column
                    }
                    MasterCard(dsp.master_gain, showRail = expanded, vm = vm, settings = settings)
                    GroupToggle(settings.activeGroup, selectGroup)
                    val ch = if (settings.activeGroup == "mains") dsp.mains else dsp.subs
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) {
                                GainCard(settings.activeGroup, ch.gain, showRail = true, vm = vm, settings = settings)
                            }
                            Box(Modifier.weight(1f)) {
                                CrossoverCard(settings.activeGroup, ch, vm = vm, settings = settings)
                            }
                        }
                    } else {
                        GainCard(settings.activeGroup, ch.gain, showRail = false, vm = vm, settings = settings)
                        CrossoverCard(settings.activeGroup, ch, vm = vm, settings = settings)
                    }
                }
            }
        }
    }

    if (showTheme) ThemeSheet(settings.accentHue, onHueChange, settings.oledBlack, onOledChange) { showTheme = false }
    if (showSettings) SettingsSheet(settings, settingsActions) { showSettings = false }
}

@Composable
private fun GroupToggle(active: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("mains" to "Main Speakers", "subs" to "Subwoofers").forEach { (key, label) ->
            val on = key == active
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (on) accent else Color(Ink.panel),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label.uppercase(),
                    fontSize = 12.sp,
                    color = if (on) Color(Ink.bg) else Color(Ink.txt2),
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
