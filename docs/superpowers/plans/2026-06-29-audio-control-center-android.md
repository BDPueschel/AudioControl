# Audio Control Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Kotlin/Jetpack Compose Android app, Audio Control Center, that is a faithful, polished port of the live AudioControl DSP control surface, talking to the existing FastAPI backend over HTTP.

**Architecture:** Single-module Compose app. Pure-Kotlin core (domain model, range/clamp, curve math, filter catalog, accent color math, drag throttle) is JVM-unit-tested with TDD. Retrofit + kotlinx.serialization for the network layer; one `AudioRepository` and one `ControlViewModel` exposing `StateFlow<UiState>`. DataStore holds three local settings. UI is built bottom-up: primitives → leaf composables → cards → adaptive screen. Backend is the single source of truth; every mutation returns the full `DspState`.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2025.01.01, Material3, Retrofit 2.11 + kotlinx.serialization 1.7.x + OkHttp 4.12, DataStore Preferences 1.1.1, Coroutines 1.9.0. Tests: JUnit4 4.13.2, Truth 1.4.4, Turbine 1.2.0, MockK 1.13.13, OkHttp MockWebServer.

## Global Constraints

- Module location: `AudioControl/android/` (subdir of the existing backend repo).
- Package: `com.audiocontrol`.
- compileSdk 35, minSdk 26, targetSdk 35. JDK 17 (`sourceCompatibility`/`targetCompatibility`/`jvmTarget` = 17).
- **Release builds only** (workspace rule; debug keys break install). Build gate command is `./gradlew assembleRelease` unless a task is pure-JVM (`./gradlew test`).
- Design language: monochrome greys/blacks + a single accent token (default cyan `#5EC8D8`). No hardcoded colors in composables — every accent use reads the theme `accent`. Background/surface greys read theme tokens too.
- All numeric ranges/steps (verbatim from spec §5):
  - master_gain −60..−20 dB step 1; gain −24..+12 dB step 0.5; hpf 20..400 Hz step 5; lpf 40..500 Hz step 5; **HPF/LPF min gap 10 Hz**.
- Mutations return full `DspState`; render from the server response (no optimistic UI in v1).
- Default server host: `poolroom-syn.taildbeee4.ts.net:8080`; overridable via in-app setting (DataStore).
- No Hilt, no Room, no Navigation library (single screen + sheets).
- Pure-logic files must not import `android.*` or `androidx.*` so they run under `./gradlew test` (plain JUnit). DataStore/Retrofit code is exempt and tested via fakes/MockWebServer.

---

### Task 1: Project scaffold

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/gradle/wrapper/gradle-wrapper.properties`, `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`
- Create: `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/proguard-rules.pro`
- Create: `android/app/src/main/java/com/audiocontrol/MainActivity.kt`
- Create: `android/app/src/main/java/com/audiocontrol/ui/theme/Theme.kt` (minimal placeholder; full theme in Task 7)
- Create: `android/app/src/main/res/values/strings.xml`, `android/.gitignore`

**Interfaces:**
- Produces: a buildable, installable empty Compose app with `MainActivity` showing a dark `Surface` and the text "Audio Control Center". `com.audiocontrol.ui.theme.AppTheme { }` composable wrapper (placeholder, replaced in Task 7).

- [ ] **Step 1: Copy the Gradle wrapper from an existing project so versions match**

```bash
cd /Users/bpueschel/Documents/CodeProjects
mkdir -p AudioControl/android/gradle/wrapper AudioControl/android/app/src/main/java/com/audiocontrol/ui/theme AudioControl/android/app/src/main/res/values
cp Resonate/gradlew AudioControl/android/gradlew
cp Resonate/gradlew.bat AudioControl/android/gradlew.bat
cp Resonate/gradle/wrapper/gradle-wrapper.jar AudioControl/android/gradle/wrapper/gradle-wrapper.jar
cp Resonate/gradle/wrapper/gradle-wrapper.properties AudioControl/android/gradle/wrapper/gradle-wrapper.properties
chmod +x AudioControl/android/gradlew
```

- [ ] **Step 2: Write `android/settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AudioControlCenter"
include(":app")
```

- [ ] **Step 3: Write `android/gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2025.01.01"
coroutines = "1.9.0"
lifecycle = "2.8.7"
datastore = "1.1.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
retrofit-serialization = "1.0.0"
activity-compose = "1.9.3"
junit = "4.13.2"
truth = "1.4.4"
turbine = "1.2.0"
mockk = "1.13.13"
coroutines-test = "1.9.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-serialization = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofit-serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 4: Write root `android/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 5: Write `android/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Write `android/app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.audiocontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.audiocontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed release so `assembleRelease` produces an installable APK without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
```

- [ ] **Step 7: Write `android/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:label="Audio Control Center"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="fullUser"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Note: `usesCleartextTraffic=true` is required — the backend is plain HTTP on the tailnet.

- [ ] **Step 8: Write `android/app/proguard-rules.pro`** (empty placeholder)

```proguard
# kotlinx.serialization keeps @Serializable metadata automatically via the plugin.
```

- [ ] **Step 9: Write `android/app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Audio Control Center</string>
</resources>
```

- [ ] **Step 10: Write the placeholder theme `android/app/src/main/java/com/audiocontrol/ui/theme/Theme.kt`**

```kotlin
package com.audiocontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0D0E10),
            surface = Color(0xFF16181B),
            primary = Color(0xFF5EC8D8),
        ),
        content = content,
    )
}
```

- [ ] **Step 11: Write `android/app/src/main/java/com/audiocontrol/MainActivity.kt`**

```kotlin
package com.audiocontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.audiocontrol.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { Surface { Text("Audio Control Center") } }
        }
    }
}
```

- [ ] **Step 12: Write `android/.gitignore`**

```gitignore
.gradle/
build/
local.properties
.idea/
*.iml
```

- [ ] **Step 13: Create `android/local.properties` pointing at the SDK (not committed)**

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > /Users/bpueschel/Documents/CodeProjects/AudioControl/android/local.properties
```

- [ ] **Step 14: Build to verify the scaffold compiles**

Run: `cd /Users/bpueschel/Documents/CodeProjects/AudioControl/android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`, an APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 15: Commit**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
git add android/ && git commit -m "feat(android): project scaffold, empty Compose app"
```

---

### Task 2: Domain model + ranges, clamps, formatting

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/core/Ranges.kt`
- Create: `android/app/src/main/java/com/audiocontrol/core/Format.kt`
- Test: `android/app/src/test/java/com/audiocontrol/core/RangesTest.kt`
- Test: `android/app/src/test/java/com/audiocontrol/core/FormatTest.kt`

**Interfaces:**
- Produces:
  - `data class Range(val min: Double, val max: Double, val step: Double)`
  - `object Ranges { val MASTER: Range; val GAIN: Range; val HPF: Range; val LPF: Range; const val GAP: Int = 10 }`
  - `fun Range.clampStep(value: Double): Double` — clamp to [min,max] then snap to nearest step.
  - `fun clampHpf(freq: Int, lpfFreq: Int): Int` — clamp into [HPF.min, lpfFreq - GAP], snapped to step 5.
  - `fun clampLpf(freq: Int, hpfFreq: Int): Int` — clamp into [hpfFreq + GAP, LPF.max], snapped to step 5.
  - `fun fmtDb(v: Double): String` — e.g. `+4.0`, `−45.0`, `0.0` (unicode minus U+2212, one decimal).
  - `fun fmtHz(v: Int): String` — e.g. `45`.

- [ ] **Step 1: Write the failing tests `RangesTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.RangesTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement `Ranges.kt`**

```kotlin
package com.audiocontrol.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Range(val min: Double, val max: Double, val step: Double)

object Ranges {
    val MASTER = Range(-60.0, -20.0, 1.0)
    val GAIN = Range(-24.0, 12.0, 0.5)
    val HPF = Range(20.0, 400.0, 5.0)
    val LPF = Range(40.0, 500.0, 5.0)
    const val GAP = 10
}

fun Range.clampStep(value: Double): Double {
    val clamped = max(min, min(max, value))
    return (clamped / step).roundToInt() * step
}

private fun snap5(v: Int): Int = ((v + 2) / 5) * 5

fun clampHpf(freq: Int, lpfFreq: Int): Int {
    val hi = lpfFreq - Ranges.GAP
    return max(Ranges.HPF.min.toInt(), min(snap5(freq), snap5(hi)))
}

fun clampLpf(freq: Int, hpfFreq: Int): Int {
    val lo = hpfFreq + Ranges.GAP
    return min(Ranges.LPF.max.toInt(), max(snap5(freq), snap5(lo)))
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.RangesTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing tests `FormatTest.kt`**

```kotlin
package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {
    @Test fun fmtDb_signsAndDecimal() {
        assertThat(fmtDb(4.0)).isEqualTo("+4.0")
        assertThat(fmtDb(0.0)).isEqualTo("0.0")
        assertThat(fmtDb(-45.0)).isEqualTo("−45.0")   // unicode minus
    }
    @Test fun fmtHz_isInteger() {
        assertThat(fmtHz(45)).isEqualTo("45")
    }
}
```

- [ ] **Step 6: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.FormatTest"`
Expected: FAIL.

- [ ] **Step 7: Implement `Format.kt`**

```kotlin
package com.audiocontrol.core

import kotlin.math.abs

fun fmtDb(v: Double): String {
    val sign = if (v > 0) "+" else if (v < 0) "−" else ""
    return sign + String.format("%.1f", abs(v))
}

fun fmtHz(v: Int): String = v.toString()
```

- [ ] **Step 8: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
git add android/app/src/main/java/com/audiocontrol/core android/app/src/test/java/com/audiocontrol/core
git commit -m "feat(android): domain ranges, clamps, dB/Hz formatting (TDD)"
```

---

### Task 3: Filter-type catalog (group-0 budget set)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/core/FilterType.kt`
- Test: `android/app/src/test/java/com/audiocontrol/core/FilterTypeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class FilterType(val wire: String, val label: String, val slopeDbPerOct: Int, val caption: String)` with entries fitting crossover group 0 (≤ 4th order): `LR2, LR4, BUTTER6, BUTTER12, BUTTER18, BUTTER24, BESSEL12, BESSEL24`. `LR4` is the default.
  - `val FilterType.exponent: Double` = `slopeDbPerOct / 3.0` (used by curve math; 24→8.0, 12→4.0).
  - `FilterType.fromWire(s: String?): FilterType` — maps the backend `type` string back to the enum, defaulting to `LR4` on null/unknown.
  - `val V1_FILTER_TYPES: List<FilterType>` — ordered list for the dropdown.

- [ ] **Step 1: Write the failing test `FilterTypeTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.FilterTypeTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `FilterType.kt`**

```kotlin
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
```

- [ ] **Step 4: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.FilterTypeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/core/FilterType.kt android/app/src/test/java/com/audiocontrol/core/FilterTypeTest.kt
git commit -m "feat(android): filter-type catalog (group-0 budget set, TDD)"
```

---

### Task 4: Passband curve math

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/core/CurveMath.kt`
- Test: `android/app/src/test/java/com/audiocontrol/core/CurveMathTest.kt`

**Interfaces:**
- Consumes: `FilterType.exponent` (Task 3).
- Produces:
  - `data class FilterCurveSpec(val freq: Int, val bypass: Boolean, val type: FilterType)`
  - `fun curveDb(hpf: FilterCurveSpec, lpf: FilterCurveSpec, f: Double): Double` — combined attenuation at frequency f, using `-10*log10(1 + (fc/f)^E)` for HPF and `-10*log10(1 + (f/fc)^E)` for LPF; a bypassed filter contributes 0.
  - `fun curveXNorm(f: Double): Double` = `ln(f/20)/ln(32)` (0..1 over 20..640 Hz).
  - `fun curveYNorm(db: Double): Double` = `clamp(-db, 0, 30)/30` (0 at top = 0 dB, 1 at bottom = −30 dB).
  - `fun curvePoints(hpf: FilterCurveSpec, lpf: FilterCurveSpec, n: Int = 160): List<Pair<Double, Double>>` — n+1 normalized (x,y) points spanning 20..640 Hz.

- [ ] **Step 1: Write the failing test `CurveMathTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.CurveMathTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `CurveMath.kt`**

```kotlin
package com.audiocontrol.core

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class FilterCurveSpec(val freq: Int, val bypass: Boolean, val type: FilterType)

fun curveDb(hpf: FilterCurveSpec, lpf: FilterCurveSpec, f: Double): Double {
    var d = 0.0
    if (!hpf.bypass) d += -10.0 * log10(1.0 + (hpf.freq / f).pow(hpf.type.exponent))
    if (!lpf.bypass) d += -10.0 * log10(1.0 + (f / lpf.freq).pow(lpf.type.exponent))
    return d
}

fun curveXNorm(f: Double): Double = ln(f / 20.0) / ln(32.0)

fun curveYNorm(db: Double): Double = min(30.0, max(0.0, -db)) / 30.0

fun curvePoints(hpf: FilterCurveSpec, lpf: FilterCurveSpec, n: Int = 160): List<Pair<Double, Double>> =
    (0..n).map { i ->
        val frac = i.toDouble() / n
        val f = 20.0 * 32.0.pow(frac)
        frac to curveYNorm(curveDb(hpf, lpf, f))
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.CurveMathTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/core/CurveMath.kt android/app/src/test/java/com/audiocontrol/core/CurveMathTest.kt
git commit -m "feat(android): generalized passband curve math (TDD)"
```

---

### Task 5: Network layer (model, service, repository)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/data/DspModels.kt`
- Create: `android/app/src/main/java/com/audiocontrol/data/AudioApi.kt`
- Create: `android/app/src/main/java/com/audiocontrol/data/AudioRepository.kt`
- Test: `android/app/src/test/java/com/audiocontrol/data/AudioRepositoryTest.kt`

**Interfaces:**
- Consumes: `FilterType` (Task 3).
- Produces:
  - Serializable `DspState`, `ChannelState`, `FilterState(freq, bypass, type: String = "lr4")`, `Health(status, device)`.
  - `FilterState.filterType: FilterType get() = FilterType.fromWire(type)`.
  - `interface AudioApi` (Retrofit) with: `getHealth`, `getState`, `setMasterGain`, `setMute`, `setGain(group)`, `setHpf(group)`, `setLpf(group)`, `reset`. Request bodies: `GainBody(value)`, `MuteBody(value)`, `FilterBody(freq: Int?, bypass: Boolean?, type: String?)`.
  - `class AudioRepository(private val apiProvider: () -> AudioApi)` with suspend funcs returning `Result<DspState>` / `Result<Health>`: `state()`, `health()`, `masterGain(v)`, `mute(v)`, `gain(group, v)`, `hpf(group, freq?, bypass?, type?)`, `lpf(group, freq?, bypass?, type?)`, `reset()`. `apiProvider` is a lambda so the repository rebuilds Retrofit when the host setting changes (Task 6 wires the real provider).
  - `fun buildApi(baseUrl: String): AudioApi` — Retrofit factory (OkHttp + kotlinx.serialization converter, base URL `http://host:port/`).

- [ ] **Step 1: Write the failing test `AudioRepositoryTest.kt`** (uses MockWebServer)

```kotlin
package com.audiocontrol.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AudioRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AudioRepository

    private val stateJson = """
      {"master_gain":-45.0,"mute":false,
       "mains":{"gain":0.0,"hpf":{"freq":80,"bypass":true,"type":"lr4"},"lpf":{"freq":120,"bypass":true,"type":"lr4"}},
       "subs":{"gain":4.0,"hpf":{"freq":45,"bypass":false,"type":"lr4"},"lpf":{"freq":200,"bypass":false,"type":"lr4"}}}
    """.trimIndent()

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val api = buildApi(server.url("/").toString())
        repo = AudioRepository { api }
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun state_parsesDspState() = runTest {
        server.enqueue(MockResponse().setBody(stateJson))
        val s = repo.state().getOrThrow()
        assertThat(s.master_gain).isEqualTo(-45.0)
        assertThat(s.subs.hpf.freq).isEqualTo(45)
        assertThat(s.subs.hpf.filterType.wire).isEqualTo("lr4")
    }
    @Test fun hpf_sendsTypeInBody() = runTest {
        server.enqueue(MockResponse().setBody(stateJson))
        repo.hpf("subs", freq = 50, bypass = null, type = "butter12").getOrThrow()
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/api/subs/hpf")
        assertThat(req.body.readUtf8()).contains("butter12")
    }
    @Test fun networkError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThat(repo.state().isFailure).isTrue()
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.data.AudioRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `DspModels.kt`**

```kotlin
package com.audiocontrol.data

import com.audiocontrol.core.FilterType
import kotlinx.serialization.Serializable

@Serializable
data class FilterState(val freq: Int, val bypass: Boolean, val type: String = "lr4") {
    val filterType: FilterType get() = FilterType.fromWire(type)
}

@Serializable
data class ChannelState(val gain: Double, val hpf: FilterState, val lpf: FilterState)

@Serializable
data class DspState(
    val master_gain: Double,
    val mute: Boolean,
    val mains: ChannelState,
    val subs: ChannelState,
)

@Serializable data class Health(val status: String, val device: String)

@Serializable data class GainBody(val value: Double)
@Serializable data class MuteBody(val value: Boolean)
@Serializable data class FilterBody(val freq: Int? = null, val bypass: Boolean? = null, val type: String? = null)
```

- [ ] **Step 4: Implement `AudioApi.kt`**

```kotlin
package com.audiocontrol.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface AudioApi {
    @GET("api/health") suspend fun getHealth(): Health
    @GET("api/state") suspend fun getState(): DspState
    @POST("api/master-gain") suspend fun setMasterGain(@Body body: GainBody): DspState
    @POST("api/mute") suspend fun setMute(@Body body: MuteBody): DspState
    @POST("api/{group}/gain") suspend fun setGain(@Path("group") group: String, @Body body: GainBody): DspState
    @POST("api/{group}/hpf") suspend fun setHpf(@Path("group") group: String, @Body body: FilterBody): DspState
    @POST("api/{group}/lpf") suspend fun setLpf(@Path("group") group: String, @Body body: FilterBody): DspState
    @POST("api/reset") suspend fun reset(): DspState
}

fun buildApi(baseUrl: String): AudioApi {
    val json = Json { ignoreUnknownKeys = true }
    val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AudioApi::class.java)
}
```

- [ ] **Step 5: Implement `AudioRepository.kt`**

```kotlin
package com.audiocontrol.data

class AudioRepository(private val apiProvider: () -> AudioApi) {
    private suspend fun <T> call(block: suspend (AudioApi) -> T): Result<T> =
        runCatching { block(apiProvider()) }

    suspend fun health() = call { it.getHealth() }
    suspend fun state() = call { it.getState() }
    suspend fun masterGain(v: Double) = call { it.setMasterGain(GainBody(v)) }
    suspend fun mute(v: Boolean) = call { it.setMute(MuteBody(v)) }
    suspend fun gain(group: String, v: Double) = call { it.setGain(group, GainBody(v)) }
    suspend fun hpf(group: String, freq: Int?, bypass: Boolean?, type: String?) =
        call { it.setHpf(group, FilterBody(freq, bypass, type)) }
    suspend fun lpf(group: String, freq: Int?, bypass: Boolean?, type: String?) =
        call { it.setLpf(group, FilterBody(freq, bypass, type)) }
    suspend fun reset() = call { it.reset() }
}
```

- [ ] **Step 6: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.data.AudioRepositoryTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/data android/app/src/test/java/com/audiocontrol/data
git commit -m "feat(android): Retrofit network layer + repository (TDD via MockWebServer)"
```

---

### Task 6: Settings store (DataStore)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/data/SettingsStore.kt`
- Test: `android/app/src/test/java/com/audiocontrol/data/SettingsDefaultsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class Settings(val host: String, val accentHue: Float, val activeGroup: String)`.
  - `object SettingsDefaults { const val HOST = "poolroom-syn.taildbeee4.ts.net:8080"; const val ACCENT_HUE = 189f; const val ACTIVE_GROUP = "subs" }` (189° ≈ the cyan `#5EC8D8`).
  - `class SettingsStore(context: Context)` with `val settings: Flow<Settings>` and suspend setters `setHost(String)`, `setAccentHue(Float)`, `setActiveGroup(String)`.
  - `fun normalizeHost(raw: String): String` — trims, strips any `http://` / trailing `/`, falls back to `SettingsDefaults.HOST` if blank. Pure, unit-tested.
  - `fun baseUrl(host: String): String` = `"http://$host/"`.

- [ ] **Step 1: Write the failing test `SettingsDefaultsTest.kt`** (pure helpers only — DataStore IO is verified on-device)

```kotlin
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
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.data.SettingsDefaultsTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `SettingsStore.kt`**

```kotlin
package com.audiocontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SettingsDefaults {
    const val HOST = "poolroom-syn.taildbeee4.ts.net:8080"
    const val ACCENT_HUE = 189f
    const val ACTIVE_GROUP = "subs"
}

data class Settings(val host: String, val accentHue: Float, val activeGroup: String)

fun normalizeHost(raw: String): String {
    val t = raw.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    return if (t.isBlank()) SettingsDefaults.HOST else t
}

fun baseUrl(host: String): String = "http://$host/"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val HOST = stringPreferencesKey("host")
        val HUE = floatPreferencesKey("accent_hue")
        val GROUP = stringPreferencesKey("active_group")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            host = p[Keys.HOST] ?: SettingsDefaults.HOST,
            accentHue = p[Keys.HUE] ?: SettingsDefaults.ACCENT_HUE,
            activeGroup = p[Keys.GROUP] ?: SettingsDefaults.ACTIVE_GROUP,
        )
    }

    suspend fun setHost(host: String) { context.dataStore.edit { it[Keys.HOST] = normalizeHost(host) } }
    suspend fun setAccentHue(hue: Float) { context.dataStore.edit { it[Keys.HUE] = hue } }
    suspend fun setActiveGroup(group: String) { context.dataStore.edit { it[Keys.GROUP] = group } }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.data.SettingsDefaultsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/data/SettingsStore.kt android/app/src/test/java/com/audiocontrol/data/SettingsDefaultsTest.kt
git commit -m "feat(android): DataStore settings (host, accent hue, active group)"
```

---

### Task 7: Theme system (accent from hue)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/theme/Palette.kt`
- Modify: `android/app/src/main/java/com/audiocontrol/ui/theme/Theme.kt`
- Test: `android/app/src/test/java/com/audiocontrol/ui/theme/PaletteTest.kt`

**Interfaces:**
- Consumes: nothing (pure math + Compose tokens).
- Produces:
  - `object Ink` — fixed greys: `bg=0xFF0D0E10`, `panel=0xFF16181B`, `panel2=0xFF1D2024`, `line=0xFF2A2E33`, `grey=0xFF3A3F46`, `txt=0xFFE9EBEE`, `txt2=0xFF9AA0A8`, `txt3=0xFF6B7178`, `err=0xFFE06B6B` (all `Long` ARGB).
  - `fun accentFor(hue: Float): Color` — HSL→Color with S/L pinned to the cyan's (S≈0.58, L≈0.61) so any hue stays on-language. Pure enough to test via its RGB at the cyan hue.
  - `fun hslToArgb(h: Float, s: Float, l: Float): Int` — pure, unit-tested.
  - `val ACCENT_PRESET_HUES: List<Float>` — `[189f, 16f, 145f, 280f, 45f]` (cyan, coral, green, violet, amber).
  - `val LocalAccent: ProvidableCompositionLocal<Color>` and `AppTheme(accentHue: Float, content)` providing it.

- [ ] **Step 1: Write the failing test `PaletteTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.ui.theme.PaletteTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `Palette.kt`**

```kotlin
package com.audiocontrol.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object Ink {
    const val bg = 0xFF0D0E10
    const val panel = 0xFF16181B
    const val panel2 = 0xFF1D2024
    const val line = 0xFF2A2E33
    const val grey = 0xFF3A3F46
    const val txt = 0xFFE9EBEE
    const val txt2 = 0xFF9AA0A8
    const val txt3 = 0xFF6B7178
    const val err = 0xFFE06B6B
}

private const val ACCENT_S = 0.58f
private const val ACCENT_L = 0.61f

fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

fun accentFor(hue: Float): Color = Color(hslToArgb(hue, ACCENT_S, ACCENT_L))

val ACCENT_PRESET_HUES: List<Float> = listOf(189f, 16f, 145f, 280f, 45f)

val LocalAccent = compositionLocalOf { accentFor(189f) }
```

- [ ] **Step 4: Replace `Theme.kt` to provide the accent**

```kotlin
package com.audiocontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(accentHue: Float = 189f, content: @Composable () -> Unit) {
    val accent = accentFor(accentHue)
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color(Ink.bg),
                surface = Color(Ink.panel),
                surfaceVariant = Color(Ink.panel2),
                primary = accent,
                error = Color(Ink.err),
                onBackground = Color(Ink.txt),
                onSurface = Color(Ink.txt),
            ),
            content = content,
        )
    }
}
```

- [ ] **Step 5: Run to verify pass + build**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.ui.theme.PaletteTest" && ./gradlew assembleRelease`
Expected: PASS + `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/theme android/app/src/test/java/com/audiocontrol/ui/theme
git commit -m "feat(android): accent-from-hue theme system (TDD)"
```

---

### Task 8: ControlViewModel (state, intents, live-commit throttle)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/UiState.kt`
- Create: `android/app/src/main/java/com/audiocontrol/ui/ControlViewModel.kt`
- Create: `android/app/src/main/java/com/audiocontrol/core/DragCommit.kt`
- Test: `android/app/src/test/java/com/audiocontrol/core/DragCommitTest.kt`
- Test: `android/app/src/test/java/com/audiocontrol/ui/ControlViewModelTest.kt`

**Interfaces:**
- Consumes: `AudioRepository` (Task 5), `Ranges`/clamps (Task 2), `FilterType` (Task 3).
- Produces:
  - `enum class ConnState { CONNECTING, CONNECTED, DISCONNECTED }`
  - `data class UiState(val dsp: DspState?, val conn: ConnState, val refreshing: Boolean, val errorBanner: String?)`
  - `class DragCommitGate` — pure coalescing gate: `fun shouldEmit(value: Double, stepsThreshold: Double): Boolean` tracks last-emitted value and only returns true once movement ≥ `stepsThreshold` accumulates; `fun reset(value: Double)`. Unit-tested.
  - `class ControlViewModel(private val repo: AudioRepository, private val activeGroupFlow, private val onActiveGroupChange)` exposing `val ui: StateFlow<UiState>` and intents: `nudgeMaster(d)`, `setMaster(v)`, `dragMaster(v, release)`, `toggleMute()`, `nudgeGain(group,d)`, `setGain(group,v)`, `dragGain(group,v,release)`, `nudgeHpf(group,d)`, `nudgeLpf(group,d)`, `setHpfFreq(group,v)`, `setLpfFreq(group,v)`, `toggleHpf(group)`, `toggleLpf(group)`, `setHpfType(group,FilterType)`, `setLpfType(group,FilterType)`, `reset()`, `refresh()`, `start()`. All mutations replace `dsp` from the returned state; failures flip `conn=DISCONNECTED` and set `errorBanner`.

- [ ] **Step 1: Write the failing test `DragCommitTest.kt`**

```kotlin
package com.audiocontrol.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DragCommitTest {
    @Test fun emitsOnlyAfterThresholdMovement() {
        val gate = DragCommitGate()
        gate.reset(0.0)
        assertThat(gate.shouldEmit(0.5, 1.0)).isFalse()   // moved 0.5 < 1.0
        assertThat(gate.shouldEmit(1.0, 1.0)).isTrue()    // moved 1.0 >= threshold
        assertThat(gate.shouldEmit(1.4, 1.0)).isFalse()   // only 0.4 since last emit
        assertThat(gate.shouldEmit(2.0, 1.0)).isTrue()
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.DragCommitTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `DragCommit.kt`**

```kotlin
package com.audiocontrol.core

import kotlin.math.abs

class DragCommitGate {
    private var lastEmitted = 0.0
    fun reset(value: Double) { lastEmitted = value }
    fun shouldEmit(value: Double, stepsThreshold: Double): Boolean {
        if (abs(value - lastEmitted) >= stepsThreshold) { lastEmitted = value; return true }
        return false
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.core.DragCommitTest"`
Expected: PASS.

- [ ] **Step 5: Write `UiState.kt`**

```kotlin
package com.audiocontrol.ui

import com.audiocontrol.data.DspState

enum class ConnState { CONNECTING, CONNECTED, DISCONNECTED }

data class UiState(
    val dsp: DspState? = null,
    val conn: ConnState = ConnState.CONNECTING,
    val refreshing: Boolean = false,
    val errorBanner: String? = null,
)
```

- [ ] **Step 6: Write the failing test `ControlViewModelTest.kt`** (fake repo + Turbine)

```kotlin
package com.audiocontrol.ui

import app.cash.turbine.test
import com.audiocontrol.data.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class ControlViewModelTest {
    private val base = DspState(
        master_gain = -45.0, mute = false,
        mains = ChannelState(0.0, FilterState(80, true), FilterState(120, true)),
        subs = ChannelState(4.0, FilterState(45, false), FilterState(200, false)),
    )
    private fun api(state: DspState) = object : AudioApi {
        override suspend fun getHealth() = Health("ok", "mock")
        override suspend fun getState() = state
        override suspend fun setMasterGain(body: GainBody) = state.copy(master_gain = body.value)
        override suspend fun setMute(body: MuteBody) = state.copy(mute = body.value)
        override suspend fun setGain(group: String, body: GainBody) = state
        override suspend fun setHpf(group: String, body: FilterBody) = state
        override suspend fun setLpf(group: String, body: FilterBody) = state
        override suspend fun reset() = base
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun start_loadsStateAndConnects() = runTest {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs")) {}
        vm.start()
        advanceUntilIdle()
        assertThat(vm.ui.value.dsp?.master_gain).isEqualTo(-45.0)
        assertThat(vm.ui.value.conn).isEqualTo(ConnState.CONNECTED)
    }

    @Test fun toggleMute_updatesFromResponse() = runTest {
        val repo = AudioRepository { api(base) }
        val vm = ControlViewModel(repo, MutableStateFlow("subs")) {}
        vm.start(); advanceUntilIdle()
        vm.toggleMute(); advanceUntilIdle()
        assertThat(vm.ui.value.dsp?.mute).isTrue()
    }
}
```

- [ ] **Step 7: Run to verify failure**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.ui.ControlViewModelTest"`
Expected: FAIL.

- [ ] **Step 8: Implement `ControlViewModel.kt`**

```kotlin
package com.audiocontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiocontrol.core.*
import com.audiocontrol.data.AudioRepository
import com.audiocontrol.data.DspState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ControlViewModel(
    private val repo: AudioRepository,
    private val activeGroupFlow: StateFlow<String>,
    private val onActiveGroupChange: (String) -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val gate = DragCommitGate()
    private var inFlight = false
    private var pending: (suspend () -> Unit)? = null

    fun start() {
        viewModelScope.launch { loadState() }
        viewModelScope.launch {
            while (true) { pollHealth(); delay(5_000) }
        }
    }

    private suspend fun loadState() {
        repo.state()
            .onSuccess { _ui.update { s -> s.copy(dsp = it, conn = ConnState.CONNECTED, errorBanner = null) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED, errorBanner = "Can't reach panel — pull to retry.") } }
    }

    private suspend fun pollHealth() {
        repo.health()
            .onSuccess { _ui.update { s -> s.copy(conn = ConnState.CONNECTED) } }
            .onFailure { _ui.update { s -> s.copy(conn = ConnState.DISCONNECTED) } }
    }

    private fun apply(block: suspend () -> Result<DspState>) {
        viewModelScope.launch {
            block()
                .onSuccess { st -> _ui.update { it.copy(dsp = st, conn = ConnState.CONNECTED, errorBanner = null) } }
                .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED, errorBanner = "Couldn't reach the panel — pull to retry.") } }
        }
    }

    // Live-commit: coalesce so at most one request is outstanding; send the latest pending on completion.
    private fun coalesced(block: suspend () -> Result<DspState>) {
        if (inFlight) { pending = { coalesced(block) }; return }
        inFlight = true
        viewModelScope.launch {
            block()
                .onSuccess { st -> _ui.update { it.copy(dsp = st, conn = ConnState.CONNECTED) } }
                .onFailure { _ui.update { it.copy(conn = ConnState.DISCONNECTED) } }
            inFlight = false
            val p = pending; pending = null; p?.invoke()
        }
    }

    private val dsp get() = _ui.value.dsp

    fun nudgeMaster(d: Double) = dsp?.let { apply { repo.masterGain(Ranges.MASTER.clampStep(it.master_gain + d)) } } ?: Unit
    fun setMaster(v: Double) = apply { repo.masterGain(Ranges.MASTER.clampStep(v)) }
    fun dragMaster(v: Double, release: Boolean) {
        val target = Ranges.MASTER.clampStep(v)
        if (release) { gate.reset(target); coalesced { repo.masterGain(target) } }
        else if (gate.shouldEmit(target, Ranges.MASTER.step * 2)) coalesced { repo.masterGain(target) }
    }

    fun toggleMute() = dsp?.let { d -> apply { repo.mute(!d.mute) } } ?: Unit

    private fun ch(group: String) = if (group == "mains") dsp?.mains else dsp?.subs

    fun nudgeGain(group: String, d: Double) = ch(group)?.let { apply { repo.gain(group, Ranges.GAIN.clampStep(it.gain + d)) } } ?: Unit
    fun setGain(group: String, v: Double) = apply { repo.gain(group, Ranges.GAIN.clampStep(v)) }
    fun dragGain(group: String, v: Double, release: Boolean) {
        val target = Ranges.GAIN.clampStep(v)
        if (release) { gate.reset(target); coalesced { repo.gain(group, target) } }
        else if (gate.shouldEmit(target, Ranges.GAIN.step * 2)) coalesced { repo.gain(group, target) }
    }

    fun nudgeHpf(group: String, d: Int) = ch(group)?.let { c -> apply { repo.hpf(group, clampHpf(c.hpf.freq + d, c.lpf.freq), null, null) } } ?: Unit
    fun nudgeLpf(group: String, d: Int) = ch(group)?.let { c -> apply { repo.lpf(group, clampLpf(c.lpf.freq + d, c.hpf.freq), null, null) } } ?: Unit
    fun setHpfFreq(group: String, v: Int) = ch(group)?.let { c -> apply { repo.hpf(group, clampHpf(v, c.lpf.freq), null, null) } } ?: Unit
    fun setLpfFreq(group: String, v: Int) = ch(group)?.let { c -> apply { repo.lpf(group, clampLpf(v, c.hpf.freq), null, null) } } ?: Unit
    fun toggleHpf(group: String) = ch(group)?.let { c -> apply { repo.hpf(group, null, !c.hpf.bypass, null) } } ?: Unit
    fun toggleLpf(group: String) = ch(group)?.let { c -> apply { repo.lpf(group, null, !c.lpf.bypass, null) } } ?: Unit
    fun setHpfType(group: String, t: FilterType) = apply { repo.hpf(group, null, null, t.wire) }
    fun setLpfType(group: String, t: FilterType) = apply { repo.lpf(group, null, null, t.wire) }

    fun reset() = apply { repo.reset() }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { loadState(); pollHealth(); _ui.update { it.copy(refreshing = false) } }
    }

    val activeGroup: StateFlow<String> = activeGroupFlow
    fun selectGroup(group: String) = onActiveGroupChange(group)
}
```

- [ ] **Step 9: Run to verify pass**

Run: `cd android && ./gradlew test --tests "com.audiocontrol.ui.ControlViewModelTest" "com.audiocontrol.core.DragCommitTest"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui android/app/src/main/java/com/audiocontrol/core/DragCommit.kt android/app/src/test/java/com/audiocontrol/ui android/app/src/test/java/com/audiocontrol/core/DragCommitTest.kt
git commit -m "feat(android): ControlViewModel with live-commit coalescing (TDD)"
```

---

> **Parallelizable block:** Tasks 9-13 each create independent leaf composables and do not import each other. They can be dispatched concurrently after Task 8. Each is gated by `./gradlew assembleRelease` (no unit tests; these are UI). Integration happens in Tasks 14-15.

### Task 9: Control primitives

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/Primitives.kt`

**Interfaces:**
- Consumes: `LocalAccent`, `Ink` (Task 7), `fmtDb`/`fmtHz` (Task 2).
- Produces composables:
  - `StepperRow(value: String, unit: String, stepLabel: String, positive: Boolean, onMinus, onPlus, onTapValue, modifier)` — `−` button (with step caption), center tappable value, `+` button. Matches the web `.btn`/`.valmid` look (50dp rounded buttons, tabular value, accent when `positive`).
  - `LevelRail(fraction: Float, onDrag: (Float, Boolean) -> Unit, modifier)` — horizontal rail with accent fill + knob; reports normalized fraction during drag at half-sensitivity and `release=true` on pointer up. Shown in landscape.
  - `BypassSwitch(on: Boolean, onToggle, modifier)` — 42x24 pill toggle, accent when on; plus a small "engaged/bypassed" status label.
  - `ValueEditorDialog(initial: String, onCommit: (Double?) -> Unit, onDismiss)` — number entry dialog for tap-to-type.

- [ ] **Step 1: Implement `Primitives.kt`** (full composables)

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import kotlin.math.roundToInt

@Composable
fun StepperRow(
    value: String, unit: String, stepLabel: String, positive: Boolean,
    onMinus: () -> Unit, onPlus: () -> Unit, onTapValue: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", stepLabel, enabled, onMinus)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .then(if (enabled) Modifier else Modifier).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (positive) accent else Color(Ink.txt),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = Color(Ink.txt3))
            }
            TextButton(onClick = onTapValue, enabled = enabled) { Text("edit", fontSize = 9.sp, color = Color(Ink.txt3)) }
        }
        StepBtn("+", stepLabel, enabled, onPlus)
    }
}

@Composable
private fun StepBtn(glyph: String, stepLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(Ink.panel2)).border(1.dp, Color(Ink.line), RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color(Ink.txt))
                Text(stepLabel, fontSize = 8.sp, color = Color(Ink.txt3))
            }
        }
    }
}

@Composable
fun LevelRail(fraction: Float, onDrag: (Float, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var widthPx by remember { mutableStateOf(1f) }
    var current by remember(fraction) { mutableStateOf(fraction) }
    Box(
        modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(9.dp)).background(Color(Ink.grey))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onDrag(current, true) },
                ) { _, dragAmount ->
                    // half-sensitivity relative drag
                    current = (current + (dragAmount / widthPx) * 0.5f).coerceIn(0f, 1f)
                    onDrag(current, false)
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(current).clip(RoundedCornerShape(9.dp)).background(accent))
        Box(
            Modifier.fillMaxWidth(current).fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(Ink.txt)))
        }
    }
}

@Composable
fun BypassSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "ENGAGED" else "BYPASSED", fontSize = 9.sp, color = Color(Ink.txt3))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(width = 42.dp, height = 24.dp).clip(RoundedCornerShape(14.dp))
                .background(if (on) accent else Color(Ink.grey)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            TextButton(onClick = onToggle, contentPadding = PaddingValues(3.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
fun ValueEditorDialog(initial: String, onCommit: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onCommit(text.toDoubleOrNull()) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/Primitives.kt
git commit -m "feat(android): control primitives (stepper, level rail, bypass switch, editor)"
```

---

### Task 10: Passband curve Canvas

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/PassbandCurve.kt`

**Interfaces:**
- Consumes: `curvePoints`/`curveXNorm`/`curveYNorm`/`curveDb`/`FilterCurveSpec` (Task 4), `LocalAccent`/`Ink` (Task 7).
- Produces: `PassbandCurve(hpf: FilterCurveSpec, lpf: FilterCurveSpec, modifier)` — a Canvas drawing the filled accent passband, the stroked curve, dashed HPF/LPF marker lines + dots (hidden when that filter is bypassed), with the axis labels 20/40/80/160/320/640 Hz beneath.

- [ ] **Step 1: Implement `PassbandCurve.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@Composable
fun PassbandCurve(hpf: FilterCurveSpec, lpf: FilterCurveSpec, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(Ink.bg))
            .border(1.dp, Color(Ink.line), RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PASSBAND", fontSize = 9.sp, color = Color(Ink.txt3))
            Text("${hpf.type.slopeDbPerOct} dB/oct", fontSize = 9.sp, color = Color(Ink.txt3))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width; val h = size.height
            val pts = curvePoints(hpf, lpf)
            val line = Path().apply {
                pts.forEachIndexed { i, (x, y) ->
                    val px = (x * w).toFloat(); val py = (y * h).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
            val fill = Path().apply {
                addPath(line); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, accent.copy(alpha = 0.10f))
            drawPath(line, accent, style = Stroke(width = 6f))
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(hpf, lpf).forEach { fs ->
                if (!fs.bypass) {
                    val x = (curveXNorm(fs.freq.toDouble()) * w).toFloat()
                    val y = (curveYNorm(curveDb(hpf, lpf, fs.freq.toDouble())) * h).toFloat()
                    drawLine(accent.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, h), pathEffect = dash)
                    drawCircle(accent, radius = 11f, center = Offset(x, y))
                    drawCircle(Color(Ink.bg), radius = 6f, center = Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20", "40", "80", "160", "320", "640 Hz").forEach {
                Text(it, fontSize = 9.sp, color = Color(Ink.txt3))
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/PassbandCurve.kt
git commit -m "feat(android): passband curve Compose Canvas"
```

---

### Task 11: Filter-type dropdown + live caption

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/FilterTypeDropdown.kt`

**Interfaces:**
- Consumes: `FilterType`, `V1_FILTER_TYPES` (Task 3), `Ink`/`LocalAccent` (Task 7).
- Produces: `FilterTypeDropdown(selected: FilterType, onSelect: (FilterType) -> Unit, modifier)` — an `ExposedDropdownMenuBox` listing `V1_FILTER_TYPES` by `label`, with the selected type's `caption` rendered live beneath (the Live Caption Beneath Selection pattern).

- [ ] **Step 1: Implement `FilterTypeDropdown.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.FilterType
import com.audiocontrol.core.V1_FILTER_TYPES
import com.audiocontrol.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterTypeDropdown(selected: FilterType, onSelect: (FilterType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.label, onValueChange = {}, readOnly = true,
                label = { Text("Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                V1_FILTER_TYPES.forEach { t ->
                    DropdownMenuItem(text = { Text(t.label) }, onClick = { onSelect(t); expanded = false })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(selected.caption, fontSize = 11.sp, color = Color(Ink.txt2))
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/FilterTypeDropdown.kt
git commit -m "feat(android): filter-type dropdown with live caption"
```

---

### Task 12: Theme sheet (hue slider + preset chips)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/ThemeSheet.kt`

**Interfaces:**
- Consumes: `accentFor`, `ACCENT_PRESET_HUES`, `Ink` (Task 7).
- Produces: `ThemeSheet(hue: Float, onHueChange: (Float) -> Unit, onDismiss: () -> Unit)` — a `ModalBottomSheet` with a 0..360 hue `Slider` (track tinted by the live accent) and a row of preset chips from `ACCENT_PRESET_HUES`, each a tappable swatch of `accentFor(presetHue)`.

- [ ] **Step 1: Implement `ThemeSheet.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.theme.ACCENT_PRESET_HUES
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.accentFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(hue: Float, onHueChange: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("ACCENT", fontSize = 11.sp, color = Color(Ink.txt3))
            Spacer(Modifier.height(12.dp))
            Slider(
                value = hue, onValueChange = onHueChange, valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = accentFor(hue), activeTrackColor = accentFor(hue)),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENT_PRESET_HUES.forEach { ph ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(accentFor(ph))
                            .clickableNoRipple { onHueChange(ph) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick,
    ))
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/ThemeSheet.kt
git commit -m "feat(android): accent theme sheet (hue slider + preset chips)"
```

---

### Task 13: Top bar (mute, two-tap reset, animated connection dot) + error banner

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/TopBar.kt`

**Interfaces:**
- Consumes: `ConnState` (Task 8), `Ink`/`LocalAccent` (Task 7).
- Produces:
  - `ControlTopBar(conn: ConnState, muted: Boolean, onMute, onReset, onOpenTheme)` — title, Mute toggle (fills error color when muted), two-tap Reset (`armed` morph with 3s auto-revert via `LaunchedEffect`, brief "Reset ✓"), a theme button, and an `AnimatedConnectionDot`.
  - `AnimatedConnectionDot(conn: ConnState)` — pulsing accent when connected (the Animated Status Indicator pattern), solid error when disconnected, dim grey when connecting.
  - `ErrorBanner(text: String?)` — full-width error-tinted bar shown when `text != null`.

- [ ] **Step 1: Implement `TopBar.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.ConnState
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent
import kotlinx.coroutines.delay

@Composable
fun ControlTopBar(
    conn: ConnState, muted: Boolean,
    onMute: () -> Unit, onReset: () -> Unit, onOpenTheme: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(Ink.bg)).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AUDIO CONTROL CENTER", fontSize = 12.sp, color = Color(Ink.txt2))
            Spacer(Modifier.width(14.dp))
            MuteButton(muted, onMute)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwoTapReset(onReset)
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onOpenTheme) { Text("Theme", fontSize = 11.sp, color = Color(Ink.txt2)) }
            Spacer(Modifier.width(10.dp))
            AnimatedConnectionDot(conn)
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, onMute: () -> Unit) {
    OutlinedButton(
        onClick = onMute,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (muted) Color(Ink.err) else Color(Ink.panel),
            contentColor = if (muted) Color.White else Color(Ink.txt2),
        ),
    ) { Text(if (muted) "Muted" else "Mute", fontSize = 11.sp) }
}

@Composable
private fun TwoTapReset(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3_000); armed = false } }
    LaunchedEffect(done) { if (done) { delay(1_200); done = false } }
    val accent = LocalAccent.current
    OutlinedButton(
        onClick = { if (done) return@OutlinedButton; if (armed) { armed = false; done = true; onReset() } else armed = true },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (armed) Color(Ink.err).copy(alpha = 0.08f) else Color(Ink.panel),
            contentColor = when { done -> accent; armed -> Color(Ink.err); else -> Color(Ink.txt2) },
        ),
    ) { Text(if (done) "Reset ✓" else if (armed) "Tap again" else "↺ Reset", fontSize = 11.sp) }
}

@Composable
fun AnimatedConnectionDot(conn: ConnState) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "conn")
    val alpha by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a",
    )
    val (color, label) = when (conn) {
        ConnState.CONNECTED -> accent.copy(alpha = alpha) to "connected"
        ConnState.DISCONNECTED -> Color(Ink.err) to "disconnected"
        ConnState.CONNECTING -> Color(Ink.grey) to "connecting…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color(Ink.txt3))
    }
}

@Composable
fun ErrorBanner(text: String?) {
    if (text == null) return
    Box(
        Modifier.fillMaxWidth().background(Color(Ink.err).copy(alpha = 0.12f))
            .border(1.dp, Color(Ink.err).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, fontSize = 12.sp, color = Color(Ink.err)) }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/TopBar.kt
git commit -m "feat(android): top bar with two-tap reset + animated connection dot"
```

---

### Task 14: Cards (Master, Gain, Crossover)

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/Cards.kt`

**Interfaces:**
- Consumes: primitives (Task 9), `PassbandCurve` (Task 10), `FilterTypeDropdown` (Task 11), `DspState`/`ChannelState` (Task 5), `ControlViewModel` intents (Task 8), `fmtDb`/`fmtHz`/`Ranges` (Task 2), `FilterCurveSpec` (Task 4), `Ink` (Task 7).
- Produces:
  - `Card(title: String, tag: String?, content)` — shared rounded panel matching the web `.card`.
  - `MasterCard(master: Double, showRail: Boolean, vm: ControlViewModel)`.
  - `GainCard(group: String, gain: Double, showRail: Boolean, vm: ControlViewModel)`.
  - `CrossoverCard(group: String, ch: ChannelState, vm: ControlViewModel)` — HPF block (bypass switch, stepper, type dropdown), LPF block, and `PassbandCurve` built from `ch`.

- [ ] **Step 1: Implement `Cards.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.*
import com.audiocontrol.data.ChannelState
import com.audiocontrol.ui.ControlViewModel
import com.audiocontrol.ui.theme.Ink

@Composable
fun Card(title: String, tag: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(Ink.panel))
            .border(1.dp, Color(Ink.line), RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 13.sp, color = Color(Ink.txt))
            if (tag != null) { Spacer(Modifier.width(10.dp)); Text(tag, fontSize = 10.sp, color = Color(Ink.txt3)) }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun MasterCard(master: Double, showRail: Boolean, vm: ControlViewModel) {
    var editing by remember { mutableStateOf(false) }
    Card("MASTER", "volume") {
        StepperRow(
            value = fmtDb(master), unit = "dB", stepLabel = "1 dB", positive = false,
            onMinus = { vm.nudgeMaster(-1.0) }, onPlus = { vm.nudgeMaster(1.0) }, onTapValue = { editing = true },
        )
        if (showRail) {
            Spacer(Modifier.height(16.dp))
            val frac = ((master - Ranges.MASTER.min) / (Ranges.MASTER.max - Ranges.MASTER.min)).toFloat()
            LevelRail(frac) { f, release -> vm.dragMaster(Ranges.MASTER.min + f * (Ranges.MASTER.max - Ranges.MASTER.min), release) }
        }
    }
    if (editing) ValueEditorDialog(fmtDb(master).replace("−", "-"), { it?.let(vm::setMaster); editing = false }, { editing = false })
}

@Composable
fun GainCard(group: String, gain: Double, showRail: Boolean, vm: ControlViewModel) {
    var editing by remember { mutableStateOf(false) }
    Card("GAIN", null) {
        StepperRow(
            value = fmtDb(gain), unit = "dB", stepLabel = "0.5", positive = gain > 0,
            onMinus = { vm.nudgeGain(group, -0.5) }, onPlus = { vm.nudgeGain(group, 0.5) }, onTapValue = { editing = true },
        )
        if (showRail) {
            Spacer(Modifier.height(16.dp))
            val frac = ((gain - Ranges.GAIN.min) / (Ranges.GAIN.max - Ranges.GAIN.min)).toFloat()
            LevelRail(frac) { f, release -> vm.dragGain(group, Ranges.GAIN.min + f * (Ranges.GAIN.max - Ranges.GAIN.min), release) }
        }
    }
    if (editing) ValueEditorDialog(fmtDb(gain).replace("−", "-"), { it?.let { v -> vm.setGain(group, v) }; editing = false }, { editing = false })
}

@Composable
fun CrossoverCard(group: String, ch: ChannelState, vm: ControlViewModel) {
    Card("CROSSOVER", "L / R") {
        FilterBlock(
            label = "High-Pass", freq = ch.hpf.freq, bypass = ch.hpf.bypass, type = ch.hpf.filterType,
            onToggle = { vm.toggleHpf(group) }, onMinus = { vm.nudgeHpf(group, -5) }, onPlus = { vm.nudgeHpf(group, 5) },
            onType = { vm.setHpfType(group, it) },
        )
        Spacer(Modifier.height(16.dp))
        FilterBlock(
            label = "Low-Pass", freq = ch.lpf.freq, bypass = ch.lpf.bypass, type = ch.lpf.filterType,
            onToggle = { vm.toggleLpf(group) }, onMinus = { vm.nudgeLpf(group, -5) }, onPlus = { vm.nudgeLpf(group, 5) },
            onType = { vm.setLpfType(group, it) },
        )
        Spacer(Modifier.height(16.dp))
        PassbandCurve(
            hpf = FilterCurveSpec(ch.hpf.freq, ch.hpf.bypass, ch.hpf.filterType),
            lpf = FilterCurveSpec(ch.lpf.freq, ch.lpf.bypass, ch.lpf.filterType),
        )
    }
}

@Composable
private fun FilterBlock(
    label: String, freq: Int, bypass: Boolean, type: FilterType,
    onToggle: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, onType: (FilterType) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), fontSize = 10.sp, color = Color(Ink.txt3))
        BypassSwitch(on = !bypass, onToggle = onToggle)
    }
    Spacer(Modifier.height(8.dp))
    StepperRow(
        value = fmtHz(freq), unit = "Hz", stepLabel = "5 Hz", positive = false,
        onMinus = onMinus, onPlus = onPlus, onTapValue = {}, enabled = !bypass,
    )
    Spacer(Modifier.height(8.dp))
    FilterTypeDropdown(selected = type, onSelect = onType)
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol/ui/components/Cards.kt
git commit -m "feat(android): Master, Gain, and split Crossover cards"
```

---

### Task 15: Adaptive screen, settings sheet, app wiring

**Files:**
- Create: `android/app/src/main/java/com/audiocontrol/ui/ControlScreen.kt`
- Create: `android/app/src/main/java/com/audiocontrol/ui/components/SettingsSheet.kt`
- Create: `android/app/src/main/java/com/audiocontrol/AppContainer.kt`
- Modify: `android/app/src/main/java/com/audiocontrol/MainActivity.kt`

**Interfaces:**
- Consumes: everything above.
- Produces:
  - `class AppContainer(context)` — builds `SettingsStore`, exposes `repoFor(host)` and a `ControlViewModel` factory bound to the current host + active-group persistence.
  - `SettingsSheet(host, onHostChange, onDismiss)` — `ModalBottomSheet` with a host:port text field (default shown), Save commits via `normalizeHost`.
  - `ControlScreen(vm, settings, onHostChange, onHueChange)` — `PullToRefreshBox` wrapping a scroll column. Top bar, error banner, then the segmented Mains/Subs toggle and the cards. **Adaptive:** compact width → single column (Master, toggle, Gain, Crossover; rails hidden); expanded width → Master full-width on top, then Gain and Crossover side by side (rails shown). Uses `LocalConfiguration` orientation / `BoxWithConstraints` maxWidth ≥ 600dp as the expanded breakpoint.

- [ ] **Step 1: Implement `SettingsSheet.kt`**

```kotlin
package com.audiocontrol.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.theme.Ink
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(host: String, onHostChange: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(host) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("SERVER", fontSize = 11.sp, color = Color(Ink.txt3))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("host:port") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onHostChange(text); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}
```

- [ ] **Step 2: Implement `AppContainer.kt`**

```kotlin
package com.audiocontrol

import android.content.Context
import com.audiocontrol.data.*
import com.audiocontrol.ui.ControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class AppContainer(context: Context, private val scope: CoroutineScope) {
    val settingsStore = SettingsStore(context.applicationContext)
    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, Settings(SettingsDefaults.HOST, SettingsDefaults.ACCENT_HUE, SettingsDefaults.ACTIVE_GROUP))

    private val hostState: StateFlow<String> = settings.map { it.host }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.HOST)
    private val repo = AudioRepository { buildApi(baseUrl(hostState.value)) }
    private val activeGroup: StateFlow<String> = settings.map { it.activeGroup }.stateIn(scope, SharingStarted.Eagerly, SettingsDefaults.ACTIVE_GROUP)

    val vm = ControlViewModel(repo, activeGroup) { g -> scope.launch { settingsStore.setActiveGroup(g) } }

    fun setHost(h: String) = scope.launch { settingsStore.setHost(h) }
    fun setHue(h: Float) = scope.launch { settingsStore.setAccentHue(h) }
}
```

(Add the needed `import kotlinx.coroutines.launch`.)

- [ ] **Step 3: Implement `ControlScreen.kt`**

```kotlin
package com.audiocontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.data.Settings
import com.audiocontrol.ui.components.*
import com.audiocontrol.ui.theme.Ink
import com.audiocontrol.ui.theme.LocalAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    vm: ControlViewModel, settings: Settings,
    onHostChange: (String) -> Unit, onHueChange: (Float) -> Unit,
    selectGroup: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var showTheme by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pull = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize()) {
        ControlTopBar(
            conn = ui.conn, muted = ui.dsp?.mute == true,
            onMute = vm::toggleMute, onReset = vm::reset, onOpenTheme = { showTheme = true },
        )
        PullToRefreshBox(
            isRefreshing = ui.refreshing, onRefresh = vm::refresh, state = pull,
            modifier = Modifier.weight(1f),
        ) {
            BoxWithConstraints {
                val expanded = maxWidth >= 600.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ErrorBanner(ui.errorBanner)
                    val dsp = ui.dsp
                    if (dsp == null) { Text("Loading…", color = Color(Ink.txt3)) ; return@Column }
                    MasterCard(dsp.master_gain, showRail = expanded, vm = vm)
                    GroupToggle(settings.activeGroup, selectGroup)
                    val ch = if (settings.activeGroup == "mains") dsp.mains else dsp.subs
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) { GainCard(settings.activeGroup, ch.gain, showRail = true, vm = vm) }
                            Box(Modifier.weight(1f)) { CrossoverCard(settings.activeGroup, ch, vm = vm) }
                        }
                    } else {
                        GainCard(settings.activeGroup, ch.gain, showRail = false, vm = vm)
                        CrossoverCard(settings.activeGroup, ch, vm = vm)
                    }
                    TextButton(onClick = { showSettings = true }) { Text("Server settings", fontSize = 11.sp, color = Color(Ink.txt3)) }
                }
            }
        }
    }
    if (showTheme) ThemeSheet(settings.accentHue, onHueChange) { showTheme = false }
    if (showSettings) SettingsSheet(settings.host, onHostChange) { showSettings = false }
}

@Composable
private fun GroupToggle(active: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("mains" to "Main Speakers", "subs" to "Subwoofers").forEach { (key, label) ->
            val on = key == active
            Surface(
                onClick = { onSelect(key) }, shape = RoundedCornerShape(8.dp),
                color = if (on) accent else Color(Ink.panel), modifier = Modifier.weight(1f),
            ) {
                Text(
                    label.uppercase(), fontSize = 12.sp,
                    color = if (on) Color(Ink.bg) else Color(Ink.txt2),
                    modifier = Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Rewrite `MainActivity.kt` to wire it all + keep-awake**

```kotlin
package com.audiocontrol

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.audiocontrol.ui.ControlScreen
import com.audiocontrol.ui.theme.AppTheme
import com.audiocontrol.ui.theme.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // wall-mounted tablet
        val container = AppContainer(this, lifecycleScope)
        container.vm.start()
        setContent {
            val settings by container.settings.collectAsState()
            AppTheme(accentHue = settings.accentHue) {
                Surface(Modifier.fillMaxSize().background(Color(Ink.bg))) {
                    ControlScreen(
                        vm = container.vm, settings = settings,
                        onHostChange = container::setHost, onHueChange = container::setHue,
                        selectGroup = container.vm::selectGroup,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Build + run the full app against the mock backend**

Run: `cd android && ./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the whole JVM test suite to confirm nothing regressed**

Run: `cd android && ./gradlew test`
Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/audiocontrol
git commit -m "feat(android): adaptive control screen, settings + theme sheets, app wiring"
```

---

### Task 16: On-device verification + screenshots

**Files:** none (verification only).

**Interfaces:** consumes the assembled APK.

- [ ] **Step 1: Ensure a mock backend is reachable on the tailnet**

Run (from the repo root, on the Mac Mini): `cd /Users/bpueschel/Documents/CodeProjects/AudioControl && .venv/bin/python run.py --port 36339 --mock`
Set the app's Server setting to `brians-mac-mini.taildbeee4.ts.net:36339`.

- [ ] **Step 2: Install on the emulator or OnePlus 15 (release APK)**

Per the workspace device workflow (AgentSandbox emulator `cortex` profile, or the OnePlus 15 over Tailscale ADB at its current rotating wireless-debug port):
```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 3: Verify, portrait:** master stepper + tap-to-type; Mains/Subs toggle persists; Gain and Crossover are separate cards; HPF/LPF bypass dims the row and hides the curve marker; filter dropdown changes the curve slope live; two-tap reset; pull-to-refresh flips the connection dot.

- [ ] **Step 4: Verify, landscape (tablet):** Master spans the top; Gain and Crossover sit side by side; level rails appear and drag commits live (watch values update mid-drag, not just on release).

- [ ] **Step 5: Verify theming:** open Theme sheet, drag hue + tap a preset chip; the whole surface (toggle, knobs, curve, dot) recolors.

- [ ] **Step 6: Capture screenshots for the record**

```bash
adb exec-out screencap -p > /private/tmp/.../scratchpad/acc_portrait.png
```

- [ ] **Step 7: Commit any fixes found during verification**, then mark the plan complete.

---

## Self-Review

**Spec coverage:** §2 scope → Tasks 1-16; §3 stack → Task 1; §4 API → Task 5; §5 model/ranges → Tasks 2,5; §6 layout/card-split → Tasks 14,15; §7 controls + live-commit → Tasks 8,9,14; §8 curve → Tasks 4,10; §9 theming → Tasks 7,12; §10 connection/error/pull-to-refresh + validated patterns → Tasks 8,13,15; §11 filter types → Tasks 3,5,11,14; §12 host config → Tasks 6,15; §13 testing → Tasks 2-8; §14 dev/build → Task 1; §15 follow-ups (keep-awake) → Task 15.

**Backend note carried from spec §11:** the `type` field on `FilterState` requires the backend to accept and echo it. The mock backend stores arbitrary fields if its `FilterRequest`/`FilterState` are extended; **a one-line backend change (add `type` to `FilterState` + `FilterRequest`, default `"lr4"`) is required before Task 5's integration works against the live mock.** This is the only backend touch and is tracked as the first step when the laptop/backend comes online; until then, `ignoreUnknownKeys` + the `type` default keep the client working against today's mock (it simply won't persist type changes server-side yet).

**Placeholder scan:** no TBD/TODO; all code shown.

**Type consistency:** `FilterType.wire`/`fromWire`, `Ranges.*.clampStep`, `clampHpf`/`clampLpf`, `curvePoints`, `AudioRepository` method names, and `ControlViewModel` intents are referenced consistently across Tasks 2-15.

## Execution note (parallelism)

Tasks 1-8 are sequential (shared spine). Tasks 9-13 are independent leaves and can be dispatched concurrently. Tasks 14-16 are sequential integration. Dispatch accordingly.
