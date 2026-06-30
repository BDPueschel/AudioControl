package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class CurveMathTest {
    private val hpf = FilterCurveSpec(45, false, FilterType.LR4)
    private val lpf = FilterCurveSpec(200, false, FilterType.LR4)

    @Test fun passband_isNearFlatInMiddle() {
        val db = curveDb(hpf, lpf, 100.0)   // well inside 45..200
        assertThat(abs(db)).isLessThan(1.0)
    }
    @Test fun bypassedFilters_contributeZero() {
        val db = curveDb(hpf.copy(bypass = true), lpf.copy(bypass = true), 30.0)
        assertThat(db).isEqualTo(0.0)
    }
    @Test fun hpf_attenuatesBelowCorner() {
        assertThat(curveDb(hpf, lpf.copy(bypass = true), 20.0)).isLessThan(-6.0)
    }
    @Test fun xNorm_spansZeroToOne() {
        assertThat(abs(curveXNorm(20.0))).isLessThan(1e-9)
        assertThat(abs(curveXNorm(640.0) - 1.0)).isLessThan(1e-9)
    }
    @Test fun yNorm_clampsRange() {
        assertThat(curveYNorm(0.0)).isEqualTo(0.0)
        assertThat(curveYNorm(-30.0)).isEqualTo(1.0)
        assertThat(curveYNorm(-99.0)).isEqualTo(1.0)
    }
    @Test fun curvePoints_count() {
        assertThat(curvePoints(hpf, lpf, 160)).hasSize(161)
    }
}
