package com.audiocontrol

import android.content.Context
import com.audiocontrol.data.*
import com.audiocontrol.ui.ControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsActions(
    val setHost: (String) -> Unit,
    val setStepMaster: (Double) -> Unit,
    val setStepGain: (Double) -> Unit,
    val setStepHpf: (Int) -> Unit,
    val setStepLpf: (Int) -> Unit,
    val setMasterCap: (Double) -> Unit,
    val setKeepAwake: (Boolean) -> Unit,
    val setOrientation: (String) -> Unit,
    val setDefaultFilterType: (String) -> Unit,
    val setHaptics: (Boolean) -> Unit,
    val setDragSensitivity: (Float) -> Unit,
)

class AppContainer(context: Context, private val scope: CoroutineScope) {
    val settingsStore = SettingsStore(context.applicationContext)
    val scenesStore = ScenesStore(context.applicationContext)
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

    val scenes: StateFlow<List<Scene>> =
        scenesStore.scenes.stateIn(scope, SharingStarted.Eagerly, emptyList())

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

    val vm = ControlViewModel(repo, activeGroup, { g ->
        scope.launch { settingsStore.setActiveGroup(g) }
    }, settings)

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

    fun saveScene(name: String) {
        val dsp = vm.currentDsp() ?: return
        scope.launch { scenesStore.save(Scene(name, dsp)) }
    }
    fun deleteScene(name: String) { scope.launch { scenesStore.delete(name) } }
    fun renameScene(old: String, new: String) { scope.launch { scenesStore.rename(old, new) } }
    fun applyScene(scene: Scene) = vm.applyScene(scene)

    val settingsActions = SettingsActions(
        setHost = ::setHost,
        setStepMaster = ::setStepMaster,
        setStepGain = ::setStepGain,
        setStepHpf = ::setStepHpf,
        setStepLpf = ::setStepLpf,
        setMasterCap = ::setMasterCap,
        setKeepAwake = ::setKeepAwake,
        setOrientation = ::setOrientation,
        setDefaultFilterType = ::setDefaultFilterType,
        setHaptics = ::setHaptics,
        setDragSensitivity = ::setDragSensitivity,
    )
}
