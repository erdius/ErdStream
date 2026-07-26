package com.erdman.erdstream.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One entry of the bottom navigation configuration: which tab (by route) and
 * whether it is currently shown. Order in the list is the display order.
 */
data class TabSetting(
    val route: String,
    val visible: Boolean,
)

class TabSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _tabSettings = MutableStateFlow(getTabSettingsSync())
    val tabSettings: StateFlow<List<TabSetting>> = _tabSettings.asStateFlow()

    fun getTabSettingsSync(): List<TabSetting> {
        val raw = prefs.getString(KEY_TAB_SETTINGS, null)
        val parsed = raw?.split(',')?.mapNotNull { part ->
            val pieces = part.split(':')
            if (pieces.size != 2) return@mapNotNull null
            val route = pieces[0].trim()
            if (route.isEmpty()) return@mapNotNull null
            TabSetting(route = route, visible = pieces[1].trim() == "1")
        } ?: emptyList()
        return normalizeTabSettings(parsed)
    }

    fun setTabSettings(settings: List<TabSetting>) {
        val normalized = normalizeTabSettings(settings)
        val serialized = normalized.joinToString(",") { setting ->
            "${setting.route}:${if (setting.visible) 1 else 0}"
        }
        prefs.edit { putString(KEY_TAB_SETTINGS, serialized) }
        _tabSettings.value = normalized
    }

    /**
     * Keep stored tab config valid: drop unknown routes, append any tabs the
     * app has gained since the config was saved, and never allow Settings to
     * be hidden (it's the only path back to this screen to un-hide others).
     */
    private fun normalizeTabSettings(settings: List<TabSetting>): List<TabSetting> {
        val known = settings.filter { it.route in DEFAULT_TAB_ROUTES }.distinctBy { it.route }
        val missing = DEFAULT_TAB_ROUTES
            .filter { route -> known.none { it.route == route } }
            .map { TabSetting(route = it, visible = true) }
        return (known + missing).map { setting ->
            if (setting.route == TAB_ROUTE_SETTINGS) setting.copy(visible = true) else setting
        }
    }

    companion object {
        private const val PREFS_NAME = "erdstream_tab_settings"
        private const val KEY_TAB_SETTINGS = "bottom_tab_settings"

        const val TAB_ROUTE_SETTINGS = "settings"

        /** Canonical bottom-tab routes in default display order. */
        val DEFAULT_TAB_ROUTES = listOf("home", "artists", "playlists", "search", TAB_ROUTE_SETTINGS)
    }
}
