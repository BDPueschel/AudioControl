package com.audiocontrol.ui

import com.audiocontrol.data.DspState

enum class ConnState { CONNECTING, CONNECTED, DISCONNECTED }

data class UiState(
    val dsp: DspState? = null,
    val conn: ConnState = ConnState.CONNECTING,
    val refreshing: Boolean = false,
    val errorBanner: String? = null,
)
