package com.erdman.erdstream.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stores user-added internet radio stations as a JSON array in
 * SharedPreferences. ErdStream has no local database (unlike ErdMusic, which
 * uses Room for this same feature); station names/URLs can contain arbitrary
 * characters, so JSON is used instead of the delimited-string format
 * TabSettingsManager uses for its fixed, delimiter-safe route names.
 */
class RadioStationsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _stations = MutableStateFlow(readStations())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private fun readStations(): List<RadioStation> {
        val raw = prefs.getString(KEY_STATIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name")
                val url = obj.optString("url")
                if (url.isBlank()) return@mapNotNull null
                RadioStation(id = id, name = name, url = url)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(stations: List<RadioStation>) {
        val array = JSONArray()
        stations.forEach { station ->
            array.put(
                JSONObject()
                    .put("id", station.id)
                    .put("name", station.name)
                    .put("url", station.url)
            )
        }
        prefs.edit { putString(KEY_STATIONS, array.toString()) }
        _stations.value = stations
    }

    fun addStation(name: String, url: String) {
        val station = RadioStation(id = UUID.randomUUID().toString(), name = name, url = url)
        persist(_stations.value + station)
    }

    fun updateStation(id: String, name: String, url: String) {
        val updated = _stations.value.map { station ->
            if (station.id == id) station.copy(name = name, url = url) else station
        }
        persist(updated)
    }

    fun deleteStations(ids: Set<String>) {
        persist(_stations.value.filterNot { it.id in ids })
    }

    companion object {
        private const val PREFS_NAME = "erdstream_radio_stations"
        private const val KEY_STATIONS = "stations"
    }
}
