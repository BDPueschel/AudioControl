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
    @Test fun clampSnap_snapsAndClamps() {
        assertThat(clampSnap(4.3, -24.0, 12.0, 0.5)).isEqualTo(4.5)    // snap to 0.5
        assertThat(clampSnap(99.0, -24.0, 12.0, 0.5)).isEqualTo(12.0)  // max clamp
        assertThat(clampSnap(-99.0, -24.0, 12.0, 0.5)).isEqualTo(-24.0) // min clamp
        assertThat(clampSnap(-10.0, -60.0, -15.0, 1.0)).isEqualTo(-15.0) // cap clamp
    }
    @Test fun clampHpfLpf_step1_noSnapping() {
        // HPF: no snap, gap boundary enforced at sample resolution
        assertThat(clampHpf(193, 300, 1)).isEqualTo(193)   // passes through unchanged
        assertThat(clampHpf(291, 300, 1)).isEqualTo(290)   // hi=290, clamps to 290
        // LPF: no snap, gap floor at lo = hpf+10
        assertThat(clampLpf(207, 200, 1)).isEqualTo(210)   // lo=210 floor → 210
        assertThat(clampLpf(250, 200, 1)).isEqualTo(250)   // above floor, passes through
    }
    @Test fun clampHpfLpf_step10_snapsToTen() {
        // HPF: snap to nearest 10Hz
        assertThat(clampHpf(193, 300, 10)).isEqualTo(190)  // 193 → snaps down to 190
        assertThat(clampHpf(197, 300, 10)).isEqualTo(200)  // 197 → snaps up to 200
        assertThat(clampHpf(295, 300, 10)).isEqualTo(290)  // hi=290, caps at 290
        // LPF: snap to nearest 10Hz
        assertThat(clampLpf(207, 200, 10)).isEqualTo(210)  // lo=210 floor → 210
        assertThat(clampLpf(253, 200, 10)).isEqualTo(250)  // 253 → snaps down to 250
    }
}
