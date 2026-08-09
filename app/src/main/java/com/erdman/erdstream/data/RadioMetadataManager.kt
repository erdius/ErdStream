package com.erdman.erdstream.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the live ICY "StreamTitle" metadata captured by PlaybackService's
 * raw Player.Listener.onMetadata callback (which runs in the service, with
 * no direct access to ErdStreamViewModel) over to the UI layer, which reads
 * it from ErdStreamApplication instead.
 */
class RadioMetadataManager {

    private val _icyStreamTitle = MutableStateFlow<String?>(null)
    val icyStreamTitle: StateFlow<String?> = _icyStreamTitle.asStateFlow()

    fun updateIcyStreamTitle(title: String?) {
        if (_icyStreamTitle.value == title) return
        _icyStreamTitle.value = title
    }
}
