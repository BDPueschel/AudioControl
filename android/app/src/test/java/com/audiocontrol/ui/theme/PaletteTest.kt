package com.audiocontrol.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class PaletteTest {
    @Test fun hslToArgb_cyanHueIsCyanish() {
        val argb = hslToArgb(189f, 0.58f, 0.61f)
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // cyan: blue and green high, red low
        assertThat(b).isGreaterThan(150)
        assertThat(g).isGreaterThan(150)
        assertThat(r).isLessThan(150)
    }
    @Test fun hslToArgb_isOpaque() {
        assertThat((hslToArgb(0f, 0.5f, 0.5f) ushr 24) and 0xFF).isEqualTo(255)
    }
    @Test fun presetHues_includeCyanDefault() {
        assertThat(ACCENT_PRESET_HUES).contains(189f)
    }
}
