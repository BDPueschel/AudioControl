package com.audiocontrol.ui

import com.audiocontrol.core.FilterType
import com.audiocontrol.data.*
import com.google.common.truth.Truth.assertThat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Recording fake for applyScene tests
// ---------------------------------------------------------------------------
private data class GainCall(val group: String, val value: Double)
private data class FilterCall(val group: String, val freq: Int?, val bypass: Boolean?, val type: String?)

/**
 * An [AudioApi] that records every mutating call and returns state that reflects the inputs,
 * so the final vm.ui.value.dsp mirrors what was replayed.
 */
private class RecordingApi(initialState: DspState) : AudioApi {
    var state = initialState
    val masterGainCalls = mutableListOf<Double>()
    val muteCalls = mutableListOf<Boolean>()
    val gainCalls = mutableListOf<GainCall>()
    val hpfCalls = mutableListOf<FilterCall>()
    val lpfCalls = mutableListOf<FilterCall>()

    override suspend fun getHealth() = Health("ok", "recording")
    override suspend fun getState() = state

    override suspend fun setMasterGain(body: GainBody): DspState {
        masterGainCalls += body.value
        state = state.copy(master_gain = body.value)
        return state
    }

    override suspend fun setMute(body: MuteBody): DspState {
        muteCalls += body.value
        state = state.copy(mute = body.value)
        return state
    }

    override suspend fun setGain(group: String, body: GainBody): DspState {
        gainCalls += GainCall(group, body.value)
        state = when (group) {
            "mains" -> state.copy(mains = state.mains.copy(gain = body.value))
            else    -> state.copy(subs  = state.subs.copy(gain  = body.value))
        }
        return state
    }

    override suspend fun setHpf(group: String, body: FilterBody): DspState {
        hpfCalls += FilterCall(group, body.freq, body.bypass, body.type)
        state = when (group) {
            "mains" -> state.copy(mains = state.mains.copy(hpf = state.mains.hpf.copy(
                freq   = body.freq   ?: state.mains.hpf.freq,
                bypass = body.bypass ?: state.mains.hpf.bypass,
                type   = body.type   ?: state.mains.hpf.type,
            )))
            else    -> state.copy(subs  = state.subs.copy(hpf  = state.subs.hpf.copy(
                freq   = body.freq   ?: state.subs.hpf.freq,
                bypass = body.bypass ?: state.subs.hpf.bypass,
                type   = body.type   ?: state.subs.hpf.type,
            )))
        }
        return state
    }

    override suspend fun setLpf(group: String, body: FilterBody): DspState {
        lpfCalls += FilterCall(group, body.freq, body.bypass, body.type)
        state = when (group) {
            "mains" -> state.copy(mains = state.mains.copy(lpf = state.mains.lpf.copy(
                freq   = body.freq   ?: state.mains.lpf.freq,
                bypass = body.bypass ?: state.mains.lpf.bypass,
                type   = body.type   ?: state.mains.lpf.type,
            )))
            else    -> state.copy(subs  = state.subs.copy(lpf  = state.subs.lpf.copy(
                freq   = body.freq   ?: state.subs.lpf.freq,
                bypass = body.bypass ?: state.subs.lpf.bypass,
                type   = body.type   ?: state.subs.lpf.type,
            )))
        }
        return state
    }

    override suspend fun reset() = state
}

@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {
    private val base = DspState(
        master_gain = -45.0, mute = false,
        mains = ChannelState(0.0, FilterState(80, true), FilterState(120, true)),
        subs = ChannelState(4.0, FilterState(45, false), FilterState(200, false)),
    )
    private fun api(state: DspState) = object : AudioApi {
        override suspend fun getHealth() = Health("ok", "mock")
        override suspend fun getState() = state
        override suspend fun setMasterGain(body: GainBody) = state.copy(master_gain = body.value)
        override suspend fun setMute(body: MuteBody) = state.copy(mute = body.value)
        override suspend fun setGain(group: String, body: GainBody) = state
        override suspend fun setHpf(group: String, body: FilterBody) = state
        override suspend fun setLpf(group: String, body: FilterBody) = state
        override suspend fun reset() = base
    }

    private fun defaultSettings() = MutableStateFlow(
        Settings(SettingsDefaults.HOST, SettingsDefaults.ACCENT_HUE, SettingsDefaults.ACTIVE_GROUP)
    )

    // Reconciliation note (brief → actual):
    //   Brief used StandardTestDispatcher() and advanceUntilIdle(). That hangs because
    //   start() launches an infinite health-poll loop (while true + delay 5 s). Two fixes:
    //   (1) Share one TestCoroutineScheduler between Dispatchers.Main and runTest so that
    //       runCurrent() advances both; (2) replace advanceUntilIdle() with runCurrent()
    //   so virtual time is NOT advanced past T=0 (the poll delay never fires again).
    //   (3) vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler at end of each test cancels viewModelScope before runTest's own
    //       advanceUntilIdle() cleanup runs on the shared scheduler — otherwise that cleanup
    //       would advance to T+5000 ms, re-trigger the loop, and hang.
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(testScheduler)

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun start_loadsStateAndConnects() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start()
        runCurrent() // runs T=0 tasks (loadState + first pollHealth); delay(5s) stays in future
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-45.0)
        assertThat(vm.ui.value.conn).isEqualTo(ConnState.CONNECTED)
        vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler
    }

    @Test fun toggleMute_updatesFromResponse() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()
        vm.toggleMute(); runCurrent()
        assertThat(vm.ui.value.dsp?.mute).isTrue()
        vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler
    }

    // I-3: proves latest-pending-wins in coalesced{}: vB is displaced by vC while vA is in-flight.
    @Test fun dragMaster_coalescesLatestPendingWins() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<Double>()
        val coalescingApi = object : AudioApi {
            override suspend fun getHealth() = Health("ok", "mock")
            override suspend fun getState() = base
            override suspend fun setMasterGain(body: GainBody): DspState {
                calls += body.value
                gate.await() // suspend until released
                return base.copy(master_gain = body.value)
            }
            override suspend fun setMute(body: MuteBody) = base
            override suspend fun setGain(group: String, body: GainBody) = base
            override suspend fun setHpf(group: String, body: FilterBody) = base
            override suspend fun setLpf(group: String, body: FilterBody) = base
            override suspend fun reset() = base
        }
        val repo = AudioRepository { coalescingApi }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()

        val vA = -45.0; val vB = -44.0; val vC = -43.0

        vm.dragMaster(vA, release = true) // launches coroutine A, suspends at gate.await()
        runCurrent()                       // coroutine A starts, hits await; inFlight=true
        vm.dragMaster(vB, release = true) // inFlight → pending = vB
        vm.dragMaster(vC, release = true) // inFlight → pending = vC (overwrites vB)
        gate.complete(Unit)               // release: coroutine A resumes, vC scheduled in finally
        runCurrent()                       // coroutine A completes + coroutine C runs to finish
        runCurrent()                       // ensure any residual tasks drain

        assertThat(calls).containsExactly(vA, vC).inOrder()
        vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler
    }

    // I-4: HPF type override wins over server echo (backend still returns "lr4" for the old server).
    @Test fun setHpfType_overrideBeatsServerEcho() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) } // fake setHpf returns base (type="lr4" default)
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()
        vm.setHpfType("subs", FilterType.BUTTER12); runCurrent()
        assertThat(vm.ui.value.dsp!!.subs.hpf.filterType).isEqualTo(FilterType.BUTTER12)
        vm.viewModelScope.cancel()
    }

    // I-5: LPF type override wins over server echo.
    @Test fun setLpfType_overrideBeatsServerEcho() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) } // fake setLpf returns base (type="lr4" default)
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()
        vm.setLpfType("subs", FilterType.BUTTER24); runCurrent()
        assertThat(vm.ui.value.dsp!!.subs.lpf.filterType).isEqualTo(FilterType.BUTTER24)
        vm.viewModelScope.cancel()
    }

    // S-1: masterCap = -15.0 → setMaster(-10.0) clamps to -15.0 (can't exceed cap).
    @Test fun setMaster_respectsConfiguredMasterCap() = runTest(mainDispatcher) {
        val settingsFlow = MutableStateFlow(
            Settings(SettingsDefaults.HOST, SettingsDefaults.ACCENT_HUE, SettingsDefaults.ACTIVE_GROUP,
                masterCap = -15.0)
        )
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, settingsFlow)
        vm.start(); runCurrent()
        vm.setMaster(-10.0); runCurrent()
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-15.0)
        vm.viewModelScope.cancel()
    }

    // S-2: default masterCap = -20.0 → setMaster(-10.0) clamps to -20.0.
    @Test fun setMaster_defaultCapClampsat20() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()
        vm.setMaster(-10.0); runCurrent()
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-20.0)
        vm.viewModelScope.cancel()
    }

    // S-3: reset() applies defaultFilterType from settings — both groups get the configured default.
    @Test fun reset_appliesDefaultFilterType() = runTest(mainDispatcher) {
        val settingsFlow = MutableStateFlow(
            Settings(SettingsDefaults.HOST, SettingsDefaults.ACCENT_HUE, SettingsDefaults.ACTIVE_GROUP,
                defaultFilterType = "butter12") // non-lr4 to prove it's read from settings
        )
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, settingsFlow)
        vm.start(); runCurrent()
        vm.reset(); runCurrent()
        assertThat(vm.ui.value.dsp!!.subs.hpf.filterType).isEqualTo(FilterType.fromWire("butter12"))
        vm.viewModelScope.cancel()
    }

    // SC-1: applyScene replays all 6 endpoint categories in order and updates the UI to the scene.
    @Test fun applyScene_replaysAllValues() = runTest(mainDispatcher) {
        val initial = DspState(
            master_gain = -45.0, mute = false,
            mains = ChannelState(0.0,  FilterState(80,  true,  "lr4"), FilterState(120, true,  "lr4")),
            subs  = ChannelState(4.0,  FilterState(45,  false, "lr4"), FilterState(200, false, "lr4")),
        )
        val recording = RecordingApi(initial)
        val repo = AudioRepository { recording }
        val vm = ControlViewModel(repo, MutableStateFlow("subs"), {}, defaultSettings())
        vm.start(); runCurrent()

        val scene = Scene(
            name = "Night",
            dsp = DspState(
                master_gain = -33.0,
                mute        = true,
                mains = ChannelState(1.0, FilterState(60,  false, "lr4"),      FilterState(100, false, "lr4")),
                subs  = ChannelState(2.0, FilterState(55,  false, "butter12"), FilterState(120, false, "lr4")),
            ),
        )

        vm.applyScene(scene)
        runCurrent()

        // All 6 categories received exactly one call
        assertThat(recording.masterGainCalls).containsExactly(-33.0)
        assertThat(recording.muteCalls).containsExactly(true)
        assertThat(recording.gainCalls).hasSize(2)   // mains + subs
        assertThat(recording.hpfCalls).hasSize(2)    // mains + subs
        assertThat(recording.lpfCalls).hasSize(2)    // mains + subs

        // Correct group ordering: mains before subs
        assertThat(recording.gainCalls[0].group).isEqualTo("mains")
        assertThat(recording.gainCalls[1].group).isEqualTo("subs")

        // subs HPF carries the distinctive values from the scene
        val subsHpf = recording.hpfCalls.first { it.group == "subs" }
        assertThat(subsHpf.freq).isEqualTo(55)
        assertThat(subsHpf.type).isEqualTo("butter12")

        // UI state reflects the scene: master, filter type, connection
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-33.0)
        assertThat(vm.ui.value.dsp?.subs?.hpf?.filterType).isEqualTo(FilterType.BUTTER12)
        assertThat(vm.ui.value.conn).isEqualTo(ConnState.CONNECTED)
        assertThat(vm.ui.value.errorBanner).isNull()

        vm.viewModelScope.cancel()
    }
}
