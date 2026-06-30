package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FilterTypeTest {
    @Test fun exponent_derivesFromSlope() {
        assertThat(FilterType.LR4.exponent).isEqualTo(8.0)
        assertThat(FilterType.BUTTER12.exponent).isEqualTo(4.0)
        assertThat(FilterType.BUTTER6.exponent).isEqualTo(2.0)
    }
    @Test fun fromWire_defaultsToLr4() {
        assertThat(FilterType.fromWire(null)).isEqualTo(FilterType.LR4)
        assertThat(FilterType.fromWire("lr4")).isEqualTo(FilterType.LR4)
        assertThat(FilterType.fromWire("butter12")).isEqualTo(FilterType.BUTTER12)
        assertThat(FilterType.fromWire("nonsense")).isEqualTo(FilterType.LR4)
    }
    @Test fun v1Set_excludes48dbOct() {
        assertThat(V1_FILTER_TYPES).contains(FilterType.LR4)
        assertThat(V1_FILTER_TYPES.all { it.slopeDbPerOct <= 24 }).isTrue()
    }
}
