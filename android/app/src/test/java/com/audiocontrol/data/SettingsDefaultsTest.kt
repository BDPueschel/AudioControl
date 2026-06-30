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
    @Test fun newDefaults_areCorrect() {
        assertThat(SettingsDefaults.STEP_MASTER).isEqualTo(1.0)
        assertThat(SettingsDefaults.STEP_GAIN).isEqualTo(0.5)
        assertThat(SettingsDefaults.STEP_HPF).isEqualTo(5)
        assertThat(SettingsDefaults.STEP_LPF).isEqualTo(5)
        assertThat(SettingsDefaults.MASTER_CAP).isEqualTo(-20.0)
        assertThat(SettingsDefaults.KEEP_AWAKE).isTrue()
        assertThat(SettingsDefaults.ORIENTATION).isEqualTo("auto")
        assertThat(SettingsDefaults.DEFAULT_FILTER_TYPE).isEqualTo("lr4")
        assertThat(SettingsDefaults.HAPTICS).isTrue()
        assertThat(SettingsDefaults.DRAG_SENSITIVITY).isEqualTo(0.5f)
    }
}
