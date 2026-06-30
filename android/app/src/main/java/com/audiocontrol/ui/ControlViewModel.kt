package com.audiocontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiocontrol.core.*
import com.audiocontrol.data.AudioRepository
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

    private val gate = DragCommitGate()
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
            .onSuccess { _ui.update { s -> s.copy(dsp = it, conn = ConnState.CONNECTED, errorBanner = null) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED, errorBanner = "Can't reach panel — pull to retry.") } }
    }

    private suspend fun pollHealth() {
        repo.health()
            .onSuccess { _ui.update { s -> s.copy(conn = ConnState.CONNECTED) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED) } }
    }

    private fun applyMutation(block: suspend () -> Result<DspState>) {
        viewModelScope.launch {
            block()
                .onSuccess { st -> _ui.update { it.copy(dsp = st, conn = ConnState.CONNECTED, errorBanner = null) } }
                .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED, errorBanner = "Couldn't reach the panel — pull to retry.") } }
        }
    }

    // Live-commit: coalesce so at most one request is outstanding; send the latest pending on completion.
    private fun coalesced(block: suspend () -> Result<DspState>) {
        if (inFlight) { pending = { coalesced(block) }; return }
        inFlight = true
        viewModelScope.launch {
            block()
                .onSuccess { st -> _ui.update { it.copy(dsp = st, conn = ConnState.CONNECTED) } }
                .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED) } }
            inFlight = false
            val p = pending; pending = null; p?.invoke()
        }
    }

    private val dsp get() = _ui.value.dsp

    fun nudgeMaster(d: Double) = dsp?.let { applyMutation { repo.masterGain(Ranges.MASTER.clampStep(it.master_gain + d)) } } ?: Unit
    fun setMaster(v: Double) = applyMutation { repo.masterGain(Ranges.MASTER.clampStep(v)) }
    fun dragMaster(v: Double, release: Boolean) {
        val target = Ranges.MASTER.clampStep(v)
        if (release) { gate.reset(target); coalesced { repo.masterGain(target) } }
        else if (gate.shouldEmit(target, Ranges.MASTER.step * 2)) coalesced { repo.masterGain(target) }
    }

    fun toggleMute() = dsp?.let { d -> applyMutation { repo.mute(!d.mute) } } ?: Unit

    private fun ch(group: String) = if (group == "mains") dsp?.mains else dsp?.subs

    fun nudgeGain(group: String, d: Double) = ch(group)?.let { applyMutation { repo.gain(group, Ranges.GAIN.clampStep(it.gain + d)) } } ?: Unit
    fun setGain(group: String, v: Double) = applyMutation { repo.gain(group, Ranges.GAIN.clampStep(v)) }
    fun dragGain(group: String, v: Double, release: Boolean) {
        val target = Ranges.GAIN.clampStep(v)
        if (release) { gate.reset(target); coalesced { repo.gain(group, target) } }
        else if (gate.shouldEmit(target, Ranges.GAIN.step * 2)) coalesced { repo.gain(group, target) }
    }

    fun nudgeHpf(group: String, d: Int) = ch(group)?.let { c -> applyMutation { repo.hpf(group, clampHpf(c.hpf.freq + d, c.lpf.freq), null, null) } } ?: Unit
    fun nudgeLpf(group: String, d: Int) = ch(group)?.let { c -> applyMutation { repo.lpf(group, clampLpf(c.lpf.freq + d, c.hpf.freq), null, null) } } ?: Unit
    fun setHpfFreq(group: String, v: Int) = ch(group)?.let { c -> applyMutation { repo.hpf(group, clampHpf(v, c.lpf.freq), null, null) } } ?: Unit
    fun setLpfFreq(group: String, v: Int) = ch(group)?.let { c -> applyMutation { repo.lpf(group, clampLpf(v, c.hpf.freq), null, null) } } ?: Unit
    fun toggleHpf(group: String) = ch(group)?.let { c -> applyMutation { repo.hpf(group, null, !c.hpf.bypass, null) } } ?: Unit
    fun toggleLpf(group: String) = ch(group)?.let { c -> applyMutation { repo.lpf(group, null, !c.lpf.bypass, null) } } ?: Unit
    fun setHpfType(group: String, t: FilterType) = applyMutation { repo.hpf(group, null, null, t.wire) }
    fun setLpfType(group: String, t: FilterType) = applyMutation { repo.lpf(group, null, null, t.wire) }

    fun reset() = applyMutation { repo.reset() }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { loadState(); pollHealth(); _ui.update { it.copy(refreshing = false) } }
    }

    val activeGroup: StateFlow<String> = activeGroupFlow
    fun selectGroup(group: String) = onActiveGroupChange(group)
}
