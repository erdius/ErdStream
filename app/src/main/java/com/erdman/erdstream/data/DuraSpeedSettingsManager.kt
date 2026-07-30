package com.erdman.erdstream.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user has confirmed ErdStream is whitelisted in MediaTek
 * DuraSpeed (a background-app killer present on some devices, including
 * the Mudita Kompakt, that can silently stop playback). Self-reported,
 * since Android has no public API for a third-party app to query
 * DuraSpeed's per-app whitelist state.
 */
class DuraSpeedSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _confirmed = MutableStateFlow(prefs.getBoolean(KEY_CONFIRMED, false))
    val confirmed: StateFlow<Boolean> = _confirmed.asStateFlow()

    fun setConfirmed(value: Boolean) {
        prefs.edit { putBoolean(KEY_CONFIRMED, value) }
        _confirmed.value = value
    }

    companion object {
        private const val PREFS_NAME = "erdstream_duraspeed_settings"
        private const val KEY_CONFIRMED = "duraspeed_confirmed"
    }
}
