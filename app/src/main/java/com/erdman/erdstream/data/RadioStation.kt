package com.erdman.erdstream.data

/**
 * A user-added internet radio station (Icecast/Shoutcast stream URL).
 */
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
)
