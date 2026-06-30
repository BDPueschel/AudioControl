package com.audiocontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiocontrol.core.*
import com.audiocontrol.data.AudioRepository
import com.audiocontrol.data.ChannelState
import com.audiocontrol.data.DspState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ControlViewModel(
    private val repo: AudioRepository,
    private val activeGroupFlow: StateFlow<String>,
    private val onActiveGroupChange: (String) -> Unit,
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

    fun nudgeMaster(d: Double) = dsp?.let { applyMutation { repo.masterGain(Ranges.MASTER.clampStep(it.master_gain + d)) } } ?: Unit
    fun setMaster(v: Double) = applyMutation { repo.masterGain(Ranges.MASTER.clampStep(v)) }
    fun dragMaster(v: Double, release: Boolean) {
        val target = Ranges.MASTER.clampStep(v)
        if (release) { gateMaster.reset(target); coalesced { repo.masterGain(target) } }
        else if (gateMaster.shouldEmit(target, Ranges.MASTER.step * 2)) coalesced { repo.masterGain(target) }
    }

    fun toggleMute() = dsp?.let { d -> applyMutation { repo.mute(!d.mute) } } ?: Unit

    private fun ch(group: String) = if (group == "mains") dsp?.mains else dsp?.subs

    fun nudgeGain(group: String, d: Double) = ch(group)?.let { applyMutation { repo.gain(group, Ranges.GAIN.clampStep(it.gain + d)) } } ?: Unit
    fun setGain(group: String, v: Double) = applyMutation { repo.gain(group, Ranges.GAIN.clampStep(v)) }
    fun dragGain(group: String, v: Double, release: Boolean) {
        val target = Ranges.GAIN.clampStep(v)
        if (release) { gateGain.reset(target); coalesced { repo.gain(group, target) } }
        else if (gateGain.shouldEmit(target, Ranges.GAIN.step * 2)) coalesced { repo.gain(group, target) }
    }

    fun nudgeHpf(group: String, d: Int) = ch(group)?.let { c -> applyMutation { repo.hpf(group, clampHpf(c.hpf.freq + d, c.lpf.freq), null, null) } } ?: Unit
    fun nudgeLpf(group: String, d: Int) = ch(group)?.let { c -> applyMutation { repo.lpf(group, clampLpf(c.lpf.freq + d, c.hpf.freq), null, null) } } ?: Unit
    fun setHpfFreq(group: String, v: Int) = ch(group)?.let { c -> applyMutation { repo.hpf(group, clampHpf(v, c.lpf.freq), null, null) } } ?: Unit
    fun setLpfFreq(group: String, v: Int) = ch(group)?.let { c -> applyMutation { repo.lpf(group, clampLpf(v, c.hpf.freq), null, null) } } ?: Unit

    /** Drag the HPF node on the curve. Routes through [coalesced] so rapid drags never queue
     *  more than one outstanding request — the latest pending value always wins. */
    fun dragHpfFreq(group: String, freq: Int, release: Boolean) {
        val c = ch(group) ?: return
        val clamped = clampHpf(freq, c.lpf.freq)
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
        val clamped = clampLpf(freq, c.hpf.freq)
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
        hpfTypeOverride.clear()
        lpfTypeOverride.clear()
        applyMutation { repo.reset() }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { loadState(); pollHealth(); _ui.update { it.copy(refreshing = false) } }
    }

    val activeGroup: StateFlow<String> = activeGroupFlow
    fun selectGroup(group: String) = onActiveGroupChange(group)
}
