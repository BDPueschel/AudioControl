package com.audiocontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Scene(val name: String, val dsp: DspState)

object SceneCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<Scene>): String = json.encodeToString(list)

    fun decode(s: String?): List<Scene> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Scene>>(s) }.getOrElse { emptyList() }
    }
}

private val Context.scenesDataStore: DataStore<Preferences> by preferencesDataStore("scenes")

class ScenesStore(private val context: Context) {
    private val KEY = stringPreferencesKey("scenes")

    val scenes: Flow<List<Scene>> = context.scenesDataStore.data.map { prefs ->
        SceneCodec.decode(prefs[KEY])
    }

    suspend fun save(scene: Scene) {
        context.scenesDataStore.edit { prefs ->
            val list = SceneCodec.decode(prefs[KEY]).toMutableList()
            val idx = list.indexOfFirst { it.name == scene.name }
            if (idx >= 0) list[idx] = scene else list += scene
            prefs[KEY] = SceneCodec.encode(list)
        }
    }

    suspend fun delete(name: String) {
        context.scenesDataStore.edit { prefs ->
            val list = SceneCodec.decode(prefs[KEY]).filter { it.name != name }
            prefs[KEY] = SceneCodec.encode(list)
        }
    }

    /**
     * Rename [old] to [new].  No-op if [old] is absent.
     * If [new] already exists, it is overwritten (removed from its current position).
     */
    suspend fun rename(old: String, new: String) {
        context.scenesDataStore.edit { prefs ->
            val list = SceneCodec.decode(prefs[KEY])
            if (list.none { it.name == old }) return@edit
            val result = list.mapNotNull { s ->
                when (s.name) {
                    old -> s.copy(name = new)  // rename the target
                    new -> null                 // drop any pre-existing scene with the new name
                    else -> s
                }
            }
            prefs[KEY] = SceneCodec.encode(result)
        }
    }
}
