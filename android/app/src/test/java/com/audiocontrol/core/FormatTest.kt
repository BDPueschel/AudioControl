package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {
    @Test fun fmtDb_signsAndDecimal() {
        assertThat(fmtDb(4.0)).isEqualTo("+4.0")
        assertThat(fmtDb(0.0)).isEqualTo("0.0")
        assertThat(fmtDb(-45.0)).isEqualTo("−45.0")   // unicode minus
    }
    @Test fun fmtDb_isLocaleIndependent() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertThat(fmtDb(-45.0)).isEqualTo("−45.0")   // not "−45,0"
            assertThat(fmtDb(4.0)).isEqualTo("+4.0")
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
    @Test fun fmtHz_isInteger() {
        assertThat(fmtHz(45)).isEqualTo("45")
    }
}
