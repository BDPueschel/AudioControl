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
