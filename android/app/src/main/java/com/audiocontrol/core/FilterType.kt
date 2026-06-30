package com.audiocontrol.core

enum class FilterType(
    val wire: String,
    val label: String,
    val slopeDbPerOct: Int,
    val caption: String,
) {
    LR2("lr2", "Linkwitz-Riley · 12 dB/oct", 12, "LR2. Gentle 2nd-order crossover, in-phase summation."),
    LR4("lr4", "Linkwitz-Riley · 24 dB/oct", 24, "LR4. The default. Steep, in-phase, the classic sub crossover."),
    BUTTER6("butter6", "Butterworth · 6 dB/oct", 6, "1st-order. Very gentle, maximal overlap."),
    BUTTER12("butter12", "Butterworth · 12 dB/oct", 12, "2nd-order Butterworth. Flat amplitude, +3 dB at corner."),
    BUTTER18("butter18", "Butterworth · 18 dB/oct", 18, "3rd-order Butterworth. Steeper, slight phase rotation."),
    BUTTER24("butter24", "Butterworth · 24 dB/oct", 24, "4th-order Butterworth. Steep, flat amplitude."),
    BESSEL12("bessel12", "Bessel · 12 dB/oct", 12, "2nd-order Bessel. Linear phase, gentle slope."),
    BESSEL24("bessel24", "Bessel · 24 dB/oct", 24, "4th-order Bessel. Linear phase, steeper slope.");

    val exponent: Double get() = slopeDbPerOct / 3.0

    companion object {
        fun fromWire(s: String?): FilterType =
            entries.firstOrNull { it.wire == s } ?: LR4
    }
}

val V1_FILTER_TYPES: List<FilterType> = FilterType.entries.toList()
