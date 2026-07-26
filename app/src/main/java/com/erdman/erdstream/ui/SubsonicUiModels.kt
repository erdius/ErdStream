package com.erdman.erdstream.ui

data class ArtistUiModel(
    val id: String,
    val name: String,
    val albumCount: Int,
)

data class AlbumUiModel(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val songCount: Int,
    val durationSeconds: Int,
    val year: Int?,
)

data class SongUiModel(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val track: Int?,
    val durationSeconds: Int?,
    val suffix: String?,
)

data class PlaylistUiModel(
    val id: String,
    val name: String,
    val songCount: Int,
    val durationSeconds: Int,
)

data class AlbumDetail(
    val album: AlbumUiModel,
    val songs: List<SongUiModel>,
)

data class PlaylistDetail(
    val playlist: PlaylistUiModel,
    val songs: List<SongUiModel>,
)

data class SearchResults(
    val artists: List<ArtistUiModel>,
    val albums: List<AlbumUiModel>,
    val songs: List<SongUiModel>,
)

fun formatDurationSeconds(totalSeconds: Int?): String? {
    val seconds = totalSeconds ?: return null
    if (seconds <= 0) return null
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}
