package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RangesTest {
    @Test fun clampStep_snapsAndClamps() {
        assertThat(Ranges.GAIN.clampStep(4.3)).isEqualTo(4.5)   // step 0.5
        assertThat(Ranges.GAIN.clampStep(99.0)).isEqualTo(12.0) // max
        assertThat(Ranges.GAIN.clampStep(-99.0)).isEqualTo(-24.0)
        assertThat(Ranges.MASTER.clampStep(-44.6)).isEqualTo(-45.0) // step 1
    }
    @Test fun clampHpf_respectsGapBelowLpf() {
        assertThat(clampHpf(190, 200)).isEqualTo(190)      // gap exactly 10 ok
        assertThat(clampHpf(195, 200)).isEqualTo(190)      // would violate gap -> clamped
        assertThat(clampHpf(5, 200)).isEqualTo(20)         // min floor, snapped
    }
    @Test fun clampLpf_respectsGapAboveHpf() {
        assertThat(clampLpf(55, 45)).isEqualTo(55)
        assertThat(clampLpf(50, 45)).isEqualTo(55)         // hpf+gap=55 floor
        assertThat(clampLpf(999, 45)).isEqualTo(500)       // max ceiling
    }
}
