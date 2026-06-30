package com.audiocontrol

import android.content.Context
import com.audiocontrol.data.*
import com.audiocontrol.ui.ControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppContainer(context: Context, private val scope: CoroutineScope) {
    val settingsStore = SettingsStore(context.applicationContext)
    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(
            scope, SharingStarted.Eagerly,
            Settings(SettingsDefaults.HOST, SettingsDefaults.ACCENT_HUE, SettingsDefaults.ACTIVE_GROUP),
        )

    private val hostState: StateFlow<String> =
        settings.map { it.host }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.HOST)

    private val repo = AudioRepository { buildApi(baseUrl(hostState.value)) }

    private val activeGroup: StateFlow<String> =
        settings.map { it.activeGroup }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.ACTIVE_GROUP)

    val vm = ControlViewModel(repo, activeGroup) { g ->
        scope.launch { settingsStore.setActiveGroup(g) }
    }

    // Block bodies so the return type is Unit — required for ::setHost / ::setHue
    // to satisfy (String) -> Unit / (Float) -> Unit at the call site in MainActivity.
    fun setHost(h: String) { scope.launch { settingsStore.setHost(h) } }
    fun setHue(h: Float) { scope.launch { settingsStore.setAccentHue(h) } }
}
