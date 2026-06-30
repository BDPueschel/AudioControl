package com.audiocontrol.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsDefaultsTest {
    @Test fun normalizeHost_stripsSchemeAndSlash() {
        assertThat(normalizeHost("  http://host:8080/ ")).isEqualTo("host:8080")
        assertThat(normalizeHost("host:8080")).isEqualTo("host:8080")
    }
    @Test fun normalizeHost_blankFallsBackToDefault() {
        assertThat(normalizeHost("   ")).isEqualTo(SettingsDefaults.HOST)
    }
    @Test fun baseUrl_wrapsHost() {
        assertThat(baseUrl("host:8080")).isEqualTo("http://host:8080/")
    }
    @Test fun oledBlackDefault_isFalse() {
        assertThat(SettingsDefaults.OLED_BLACK).isFalse()
    }
}
