package com.audiocontrol.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SceneCodecTest {

    private val dsp1 = DspState(
        master_gain = -30.0,
        mute = false,
        mains = ChannelState(
            gain = 0.0,
            hpf = FilterState(80, true, "lr4"),
            lpf = FilterState(120, true, "lr4"),
        ),
        subs = ChannelState(
            gain = 2.0,
            hpf = FilterState(45, false, "lr4"),
            lpf = FilterState(200, false, "lr4"),
        ),
    )

    private val dsp2 = DspState(
        master_gain = -45.0,
        mute = true,
        mains = ChannelState(
            gain = -3.0,
            hpf = FilterState(60, false, "butter12"),
            lpf = FilterState(100, false, "butter12"),
        ),
        subs = ChannelState(
            gain = 0.0,
            hpf = FilterState(55, false, "butter12"),
            lpf = FilterState(150, false, "butter12"),
        ),
    )

    @Test fun roundTrip_twoScenes() {
        val scenes = listOf(Scene("Movie", dsp1), Scene("Music", dsp2))
        val encoded = SceneCodec.encode(scenes)
        val decoded = SceneCodec.decode(encoded)

        assertThat(decoded).hasSize(2)
        assertThat(decoded[0].name).isEqualTo("Movie")
        assertThat(decoded[0].dsp.master_gain).isEqualTo(-30.0)
        assertThat(decoded[0].dsp.mute).isFalse()
        assertThat(decoded[1].name).isEqualTo("Music")
        assertThat(decoded[1].dsp.master_gain).isEqualTo(-45.0)
        assertThat(decoded[1].dsp.mute).isTrue()
        assertThat(decoded[1].dsp.subs.hpf.freq).isEqualTo(55)
        assertThat(decoded[1].dsp.subs.hpf.type).isEqualTo("butter12")
        assertThat(decoded[1].dsp.mains.lpf.bypass).isFalse()
    }

    @Test fun roundTrip_preservesDspEquality() {
        val original = listOf(Scene("A", dsp1), Scene("B", dsp2))
        val decoded = SceneCodec.decode(SceneCodec.encode(original))
        assertThat(decoded[0].dsp).isEqualTo(dsp1)
        assertThat(decoded[1].dsp).isEqualTo(dsp2)
    }

    @Test fun decode_null_returnsEmptyList() {
        assertThat(SceneCodec.decode(null)).isEmpty()
    }

    @Test fun decode_blank_returnsEmptyList() {
        assertThat(SceneCodec.decode("")).isEmpty()
        assertThat(SceneCodec.decode("   ")).isEmpty()
    }

    @Test fun decode_garbage_returnsEmptyList() {
        assertThat(SceneCodec.decode("{garbage")).isEmpty()
        assertThat(SceneCodec.decode("not json at all")).isEmpty()
    }
}
