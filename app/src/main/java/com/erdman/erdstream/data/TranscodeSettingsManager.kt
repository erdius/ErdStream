package com.erdman.erdstream.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bitrate options offered to the user. 0 means "Original" -- no maxBitRate
 * parameter is sent, so the server streams the source file (FLAC) as-is.
 */
enum class TranscodeBitrate(val kbps: Int, val label: String) {
    ORIGINAL(0, "Original (no transcoding)"),
    KBPS_320(320, "320 kbps"),
    KBPS_256(256, "256 kbps"),
    KBPS_192(192, "192 kbps"),
    KBPS_128(128, "128 kbps"),
    KBPS_96(96, "96 kbps"),
    KBPS_64(64, "64 kbps");

    companion object {
        fun fromKbps(kbps: Int): TranscodeBitrate =
            entries.find { it.kbps == kbps } ?: ORIGINAL
    }
}

class TranscodeSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bitrate = MutableStateFlow(TranscodeBitrate.fromKbps(prefs.getInt(KEY_BITRATE, 0)))
    val bitrate: StateFlow<TranscodeBitrate> = _bitrate.asStateFlow()

    fun setBitrate(value: TranscodeBitrate) {
        prefs.edit { putInt(KEY_BITRATE, value.kbps) }
        _bitrate.value = value
    }

    companion object {
        private const val PREFS_NAME = "erdstream_settings"
        private const val KEY_BITRATE = "transcode_bitrate_kbps"

        /** Format requested from the server when transcoding is active. */
        const val TRANSCODE_FORMAT = "mp3"
    }
}
