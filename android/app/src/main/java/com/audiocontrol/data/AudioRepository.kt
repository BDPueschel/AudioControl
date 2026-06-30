package com.audiocontrol.data

class AudioRepository(private val apiProvider: () -> AudioApi) {
    private suspend fun <T> call(block: suspend (AudioApi) -> T): Result<T> =
        runCatching { block(apiProvider()) }

    suspend fun health() = call { it.getHealth() }
    suspend fun state() = call { it.getState() }
    suspend fun masterGain(v: Double) = call { it.setMasterGain(GainBody(v)) }
    suspend fun mute(v: Boolean) = call { it.setMute(MuteBody(v)) }
    suspend fun gain(group: String, v: Double) = call { it.setGain(group, GainBody(v)) }
    suspend fun hpf(group: String, freq: Int?, bypass: Boolean?, type: String?) =
        call { it.setHpf(group, FilterBody(freq, bypass, type)) }
    suspend fun lpf(group: String, freq: Int?, bypass: Boolean?, type: String?) =
        call { it.setLpf(group, FilterBody(freq, bypass, type)) }
    suspend fun reset() = call { it.reset() }
}
