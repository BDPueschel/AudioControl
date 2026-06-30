package com.audiocontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SettingsDefaults {
    const val HOST = "poolroom-syn.taildbeee4.ts.net:8080"
    const val ACCENT_HUE = 189f
    const val ACTIVE_GROUP = "subs"
    const val OLED_BLACK = false
    const val STEP_MASTER = 1.0
    const val STEP_GAIN = 0.5
    const val STEP_HPF = 5
    const val STEP_LPF = 5
    const val MASTER_CAP = -20.0
    const val KEEP_AWAKE = true
    const val ORIENTATION = "auto"
    const val DEFAULT_FILTER_TYPE = "lr4"
    const val HAPTICS = true
    const val DRAG_SENSITIVITY = 0.5f
}

data class Settings(
    val host: String,
    val accentHue: Float,
    val activeGroup: String,
    val oledBlack: Boolean = false,
    val stepMaster: Double = SettingsDefaults.STEP_MASTER,
    val stepGain: Double = SettingsDefaults.STEP_GAIN,
    val stepHpf: Int = SettingsDefaults.STEP_HPF,
    val stepLpf: Int = SettingsDefaults.STEP_LPF,
    val masterCap: Double = SettingsDefaults.MASTER_CAP,
    val keepAwake: Boolean = SettingsDefaults.KEEP_AWAKE,
    val orientation: String = SettingsDefaults.ORIENTATION,
    val defaultFilterType: String = SettingsDefaults.DEFAULT_FILTER_TYPE,
    val haptics: Boolean = SettingsDefaults.HAPTICS,
    val dragSensitivity: Float = SettingsDefaults.DRAG_SENSITIVITY,
)

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
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val STEP_MASTER = doublePreferencesKey("step_master")
        val STEP_GAIN = doublePreferencesKey("step_gain")
        val STEP_HPF = intPreferencesKey("step_hpf")
        val STEP_LPF = intPreferencesKey("step_lpf")
        val MASTER_CAP = doublePreferencesKey("master_cap")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val ORIENTATION = stringPreferencesKey("orientation")
        val DEFAULT_FILTER_TYPE = stringPreferencesKey("default_filter_type")
        val HAPTICS = booleanPreferencesKey("haptics")
        val DRAG_SENSITIVITY = floatPreferencesKey("drag_sensitivity")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            host = p[Keys.HOST] ?: SettingsDefaults.HOST,
            accentHue = p[Keys.HUE] ?: SettingsDefaults.ACCENT_HUE,
            activeGroup = p[Keys.GROUP] ?: SettingsDefaults.ACTIVE_GROUP,
            oledBlack = p[Keys.OLED_BLACK] ?: SettingsDefaults.OLED_BLACK,
            stepMaster = p[Keys.STEP_MASTER] ?: SettingsDefaults.STEP_MASTER,
            stepGain = p[Keys.STEP_GAIN] ?: SettingsDefaults.STEP_GAIN,
            stepHpf = p[Keys.STEP_HPF] ?: SettingsDefaults.STEP_HPF,
            stepLpf = p[Keys.STEP_LPF] ?: SettingsDefaults.STEP_LPF,
            masterCap = p[Keys.MASTER_CAP] ?: SettingsDefaults.MASTER_CAP,
            keepAwake = p[Keys.KEEP_AWAKE] ?: SettingsDefaults.KEEP_AWAKE,
            orientation = p[Keys.ORIENTATION] ?: SettingsDefaults.ORIENTATION,
            defaultFilterType = p[Keys.DEFAULT_FILTER_TYPE] ?: SettingsDefaults.DEFAULT_FILTER_TYPE,
            haptics = p[Keys.HAPTICS] ?: SettingsDefaults.HAPTICS,
            dragSensitivity = p[Keys.DRAG_SENSITIVITY] ?: SettingsDefaults.DRAG_SENSITIVITY,
        )
    }

    suspend fun setHost(host: String) { context.dataStore.edit { it[Keys.HOST] = normalizeHost(host) } }
    suspend fun setAccentHue(hue: Float) { context.dataStore.edit { it[Keys.HUE] = hue } }
    suspend fun setActiveGroup(group: String) { context.dataStore.edit { it[Keys.GROUP] = group } }
    suspend fun setOledBlack(v: Boolean) { context.dataStore.edit { it[Keys.OLED_BLACK] = v } }
    suspend fun setStepMaster(v: Double) { context.dataStore.edit { it[Keys.STEP_MASTER] = v } }
    suspend fun setStepGain(v: Double) { context.dataStore.edit { it[Keys.STEP_GAIN] = v } }
    suspend fun setStepHpf(v: Int) { context.dataStore.edit { it[Keys.STEP_HPF] = v } }
    suspend fun setStepLpf(v: Int) { context.dataStore.edit { it[Keys.STEP_LPF] = v } }
    suspend fun setMasterCap(v: Double) { context.dataStore.edit { it[Keys.MASTER_CAP] = v } }
    suspend fun setKeepAwake(v: Boolean) { context.dataStore.edit { it[Keys.KEEP_AWAKE] = v } }
    suspend fun setOrientation(v: String) { context.dataStore.edit { it[Keys.ORIENTATION] = v } }
    suspend fun setDefaultFilterType(v: String) { context.dataStore.edit { it[Keys.DEFAULT_FILTER_TYPE] = v } }
    suspend fun setHaptics(v: Boolean) { context.dataStore.edit { it[Keys.HAPTICS] = v } }
    suspend fun setDragSensitivity(v: Float) { context.dataStore.edit { it[Keys.DRAG_SENSITIVITY] = v } }
}
