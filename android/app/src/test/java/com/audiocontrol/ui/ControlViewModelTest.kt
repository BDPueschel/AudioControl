package com.audiocontrol.ui

import app.cash.turbine.test
import com.audiocontrol.data.*
import com.google.common.truth.Truth.assertThat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

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
        val vm = ControlViewModel(repo, MutableStateFlow("subs")) {}
        vm.start()
        runCurrent() // runs T=0 tasks (loadState + first pollHealth); delay(5s) stays in future
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-45.0)
        assertThat(vm.ui.value.conn).isEqualTo(ConnState.CONNECTED)
        vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler // cancel viewModelScope; removes pending delay from scheduler
    }

    @Test fun toggleMute_updatesFromResponse() = runTest(mainDispatcher) {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs")) {}
        vm.start(); runCurrent()
        vm.toggleMute(); runCurrent()
        assertThat(vm.ui.value.dsp?.mute).isTrue()
        vm.viewModelScope.cancel() // cancel viewModelScope; removes pending delay from shared scheduler
    }
}
