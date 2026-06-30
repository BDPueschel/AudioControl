package com.audiocontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiocontrol.core.*
import com.audiocontrol.data.AudioRepository
import com.audiocontrol.data.ChannelState
import com.audiocontrol.data.DspState
import com.audiocontrol.data.Scene
import com.audiocontrol.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ControlViewModel(
    private val repo: AudioRepository,
    private val activeGroupFlow: StateFlow<String>,
    private val onActiveGroupChange: (String) -> Unit,
    private val settings: StateFlow<Settings>,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val hpfTypeOverride = mutableMapOf<String, FilterType>()
    private val lpfTypeOverride = mutableMapOf<String, FilterType>()

    private fun applyTypeOverrides(state: DspState?): DspState? {
        if (state == null) return null
        fun applyChannel(group: String, ch: ChannelState): ChannelState {
            val hpfType = hpfTypeOverride[group]
            val lpfType = lpfTypeOverride[group]
            return if (hpfType == null && lpfType == null) ch
            else ch.copy(
                hpf = if (hpfType != null) ch.hpf.copy(type = hpfType.wire) else ch.hpf,
                lpf = if (lpfType != null) ch.lpf.copy(type = lpfType.wire) else ch.lpf,
            )
        }
        return state.copy(
            mains = applyChannel("mains", state.mains),
            subs = applyChannel("subs", state.subs),
        )
    }

    private val gateMaster = DragCommitGate()
    private val gateGain = DragCommitGate()
    private var lastHpfDrag: Int? = null
    private var lastLpfDrag: Int? = null
    private var inFlight = false
    private var pending: (suspend () -> Unit)? = null

    fun start() {
        viewModelScope.launch { loadState() }
        viewModelScope.launch {
            while (true) { pollHealth(); delay(5_000) }
        }
    }

    private suspend fun loadState() {
        repo.state()
            .onSuccess { _ui.update { s -> s.copy(dsp = applyTypeOverrides(it), conn = ConnState.CONNECTED, errorBanner = null) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED, errorBanner = "Can't reach panel — pull to retry.") } }
    }

    private suspend fun pollHealth() {
        repo.health()
            .onSuccess { _ui.update { s -> s.copy(conn = ConnState.CONNECTED, errorBanner = null) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED) } }
    }

    private fun applyMutation(block: suspend () -> Result<DspState>) {
        viewModelScope.launch {
            block()
                .onSuccess { st -> _ui.update { it.copy(dsp = applyTypeOverrides(st), conn = ConnState.CONNECTED, errorBanner = null) } }
                .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED, errorBanner = "Couldn't reach the panel — pull to retry.") } }
        }
    }

    // Live-commit: coalesce so at most one request is outstanding; send the latest pending on completion.
    private fun coalesced(block: suspend () -> Result<DspState>) {
        if (inFlight) { pending = { coalesced(block) }; return }
        inFlight = true
        viewModelScope.launch {
            try {
                block()
                    .onSuccess { st -> _ui.update { it.copy(dsp = applyTypeOverrides(st), conn = ConnState.CONNECTED, errorBanner = null) } }
                    .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED, errorBanner = "Couldn't reach the panel — pull to retry.") } }
            } finally {
                inFlight = false
                val p = pending; pending = null; p?.invoke()
            }
        }
    }

    private val dsp get() = _ui.value.dsp

    fun nudgeMaster(d: Double) = dsp?.let {
        applyMutation { repo.masterGain(clampSnap(it.master_gain + d, -60.0, settings.value.masterCap, settings.value.stepMaster)) }
    } ?: Unit
    fun setMaster(v: Double) = applyMutation { repo.masterGain(clampSnap(v, -60.0, settings.value.masterCap, settings.value.stepMaster)) }
    fun dragMaster(v: Double, release: Boolean) {
        val target = clampSnap(v, -60.0, settings.value.masterCap, settings.value.stepMaster)
        if (release) { gateMaster.reset(target); coalesced { repo.masterGain(target) } }
        else if (gateMaster.shouldEmit(target, Ranges.MASTER.step * 2)) coalesced { repo.masterGain(target) }
    }

    fun toggleMute() = dsp?.let { d -> applyMutation { repo.mute(!d.mute) } } ?: Unit

    private fun ch(group: String) = if (group == "mains") dsp?.mains else dsp?.subs

    fun nudgeGain(group: String, d: Double) = ch(group)?.let {
        applyMutation { repo.gain(group, clampSnap(it.gain + d, -24.0, 12.0, settings.value.stepGain)) }
    } ?: Unit
    fun setGain(group: String, v: Double) = applyMutation { repo.gain(group, clampSnap(v, -24.0, 12.0, settings.value.stepGain)) }
    fun dragGain(group: String, v: Double, release: Boolean) {
        val target = clampSnap(v, -24.0, 12.0, settings.value.stepGain)
        if (release) { gateGain.reset(target); coalesced { repo.gain(group, target) } }
        else if (gateGain.shouldEmit(target, Ranges.GAIN.step * 2)) coalesced { repo.gain(group, target) }
    }

    fun nudgeHpf(group: String, d: Int) = ch(group)?.let { c ->
        applyMutation { repo.hpf(group, clampHpf(c.hpf.freq + d, c.lpf.freq, settings.value.stepHpf), null, null) }
    } ?: Unit
    fun nudgeLpf(group: String, d: Int) = ch(group)?.let { c ->
        applyMutation { repo.lpf(group, clampLpf(c.lpf.freq + d, c.hpf.freq, settings.value.stepLpf), null, null) }
    } ?: Unit
    fun setHpfFreq(group: String, v: Int) = ch(group)?.let { c ->
        applyMutation { repo.hpf(group, clampHpf(v, c.lpf.freq, settings.value.stepHpf), null, null) }
    } ?: Unit
    fun setLpfFreq(group: String, v: Int) = ch(group)?.let { c ->
        applyMutation { repo.lpf(group, clampLpf(v, c.hpf.freq, settings.value.stepLpf), null, null) }
    } ?: Unit

    /** Drag the HPF node on the curve. Routes through [coalesced] so rapid drags never queue
     *  more than one outstanding request — the latest pending value always wins. */
    fun dragHpfFreq(group: String, freq: Int, release: Boolean) {
        val c = ch(group) ?: return
        val clamped = clampHpf(freq, c.lpf.freq, settings.value.stepHpf)
        if (release) {
            lastHpfDrag = null
            coalesced { repo.hpf(group, clamped, null, null) }
        } else if (clamped != lastHpfDrag) {
            lastHpfDrag = clamped
            coalesced { repo.hpf(group, clamped, null, null) }
        }
    }

    /** Drag the LPF node on the curve. See [dragHpfFreq] for coalescing semantics. */
    fun dragLpfFreq(group: String, freq: Int, release: Boolean) {
        val c = ch(group) ?: return
        val clamped = clampLpf(freq, c.hpf.freq, settings.value.stepLpf)
        if (release) {
            lastLpfDrag = null
            coalesced { repo.lpf(group, clamped, null, null) }
        } else if (clamped != lastLpfDrag) {
            lastLpfDrag = clamped
            coalesced { repo.lpf(group, clamped, null, null) }
        }
    }
    fun toggleHpf(group: String) = ch(group)?.let { c -> applyMutation { repo.hpf(group, null, !c.hpf.bypass, null) } } ?: Unit
    fun toggleLpf(group: String) = ch(group)?.let { c -> applyMutation { repo.lpf(group, null, !c.lpf.bypass, null) } } ?: Unit
    fun setHpfType(group: String, t: FilterType) {
        hpfTypeOverride[group] = t
        _ui.update { it.copy(dsp = applyTypeOverrides(it.dsp)) }
        applyMutation { repo.hpf(group, null, null, t.wire) }
    }

    fun setLpfType(group: String, t: FilterType) {
        lpfTypeOverride[group] = t
        _ui.update { it.copy(dsp = applyTypeOverrides(it.dsp)) }
        applyMutation { repo.lpf(group, null, null, t.wire) }
    }

    fun reset() {
        val defaultType = FilterType.fromWire(settings.value.defaultFilterType)
        for (g in listOf("mains", "subs")) {
            hpfTypeOverride[g] = defaultType
            lpfTypeOverride[g] = defaultType
        }
        applyMutation { repo.reset() }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { loadState(); pollHealth(); _ui.update { it.copy(refreshing = false) } }
    }

    /** Returns the current DSP state, or null if no state has been loaded yet. */
    fun currentDsp(): DspState? = ui.value.dsp

    /**
     * Replay [scene]'s DSP values through the repo IN ORDER — master-gain, mute, then for each
     * group (mains, subs): gain, hpf(freq+bypass+type), lpf(freq+bypass+type).
     *
     * Runs in a SINGLE sequential coroutine (no fan-out via applyMutation) so calls never
     * interleave with each other.  On any failure: DISCONNECTED + standard error banner.
     * On full success: UI updated from the last response with type-overrides applied.
     */
    fun applyScene(scene: Scene) {
        viewModelScope.launch {
            // Set type overrides upfront so applyTypeOverrides reflects the scene's filter types.
            for (group in listOf("mains", "subs")) {
                val ch = if (group == "mains") scene.dsp.mains else scene.dsp.subs
                hpfTypeOverride[group] = FilterType.fromWire(ch.hpf.type)
                lpfTypeOverride[group] = FilterType.fromWire(ch.lpf.type)
            }

            // Helper: unwrap a Result, or set error state and bail.
            fun <T> step(r: Result<T>): T? = r.onFailure {
                _ui.update {
                    it.copy(
                        conn = ConnState.DISCONNECTED,
                        errorBanner = "Couldn't reach the panel — pull to retry.",
                    )
                }
            }.getOrNull()

            var last = step(repo.masterGain(scene.dsp.master_gain)) ?: return@launch
            last = step(repo.mute(scene.dsp.mute)) ?: return@launch
            for (group in listOf("mains", "subs")) {
                val ch = if (group == "mains") scene.dsp.mains else scene.dsp.subs
                last = step(repo.gain(group, ch.gain)) ?: return@launch
                last = step(repo.hpf(group, ch.hpf.freq, ch.hpf.bypass, ch.hpf.type)) ?: return@launch
                last = step(repo.lpf(group, ch.lpf.freq, ch.lpf.bypass, ch.lpf.type)) ?: return@launch
            }
            _ui.update {
                it.copy(dsp = applyTypeOverrides(last), conn = ConnState.CONNECTED, errorBanner = null)
            }
        }
    }

    val activeGroup: StateFlow<String> = activeGroupFlow
    fun selectGroup(group: String) = onActiveGroupChange(group)
}
