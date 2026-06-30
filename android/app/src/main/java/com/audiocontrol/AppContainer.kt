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
            Settings(
                SettingsDefaults.HOST,
                SettingsDefaults.ACCENT_HUE,
                SettingsDefaults.ACTIVE_GROUP,
                SettingsDefaults.OLED_BLACK,
                SettingsDefaults.STEP_MASTER,
                SettingsDefaults.STEP_GAIN,
                SettingsDefaults.STEP_HPF,
                SettingsDefaults.STEP_LPF,
                SettingsDefaults.MASTER_CAP,
                SettingsDefaults.KEEP_AWAKE,
                SettingsDefaults.ORIENTATION,
                SettingsDefaults.DEFAULT_FILTER_TYPE,
                SettingsDefaults.HAPTICS,
                SettingsDefaults.DRAG_SENSITIVITY,
            ),
        )

    private val hostState: StateFlow<String> =
        settings.map { it.host }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.HOST)

    // Cache the AudioApi by host: rebuild only when the host string changes.
    // synchronized(this) is sufficient thread-safety for this single-activity app.
    private var cachedHost: String? = null
    private var cachedApi: AudioApi? = null
    private fun getOrBuildApi(): AudioApi = synchronized(this) {
        val host = hostState.value
        if (host != cachedHost || cachedApi == null) {
            cachedApi = buildApi(baseUrl(host))
            cachedHost = host
        }
        cachedApi!!
    }

    private val repo = AudioRepository { getOrBuildApi() }

    private val activeGroup: StateFlow<String> =
        settings.map { it.activeGroup }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.ACTIVE_GROUP)

    val vm = ControlViewModel(repo, activeGroup) { g ->
        scope.launch { settingsStore.setActiveGroup(g) }
    }

    // Block bodies so the return type is Unit — required for ::setHost / ::setHue / ::setOled
    // to satisfy (String) -> Unit / (Float) -> Unit / (Boolean) -> Unit at the call site in MainActivity.
    fun setHost(h: String) { scope.launch { settingsStore.setHost(h) } }
    fun setHue(h: Float) { scope.launch { settingsStore.setAccentHue(h) } }
    fun setOled(v: Boolean) { scope.launch { settingsStore.setOledBlack(v) } }
    fun setStepMaster(v: Double) { scope.launch { settingsStore.setStepMaster(v) } }
    fun setStepGain(v: Double) { scope.launch { settingsStore.setStepGain(v) } }
    fun setStepHpf(v: Int) { scope.launch { settingsStore.setStepHpf(v) } }
    fun setStepLpf(v: Int) { scope.launch { settingsStore.setStepLpf(v) } }
    fun setMasterCap(v: Double) { scope.launch { settingsStore.setMasterCap(v) } }
    fun setKeepAwake(v: Boolean) { scope.launch { settingsStore.setKeepAwake(v) } }
    fun setOrientation(v: String) { scope.launch { settingsStore.setOrientation(v) } }
    fun setDefaultFilterType(v: String) { scope.launch { settingsStore.setDefaultFilterType(v) } }
    fun setHaptics(v: Boolean) { scope.launch { settingsStore.setHaptics(v) } }
    fun setDragSensitivity(v: Float) { scope.launch { settingsStore.setDragSensitivity(v) } }
}
