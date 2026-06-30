package com.audiocontrol.data

import com.audiocontrol.core.FilterType
import kotlinx.serialization.Serializable

@Serializable
data class FilterState(val freq: Int, val bypass: Boolean, val type: String = "lr4") {
    val filterType: FilterType get() = FilterType.fromWire(type)
}

@Serializable
data class ChannelState(val gain: Double, val hpf: FilterState, val lpf: FilterState)

@Serializable
data class DspState(
    val master_gain: Double,
    val mute: Boolean,
    val mains: ChannelState,
    val subs: ChannelState,
)

@Serializable data class Health(val status: String, val device: String)

@Serializable data class GainBody(val value: Double)
@Serializable data class MuteBody(val value: Boolean)
@Serializable data class FilterBody(val freq: Int? = null, val bypass: Boolean? = null, val type: String? = null)
