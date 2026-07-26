package com.erdman.erdstream.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubsonicEnvelope(
    @Json(name = "subsonic-response") val response: SubsonicResponse,
)

@JsonClass(generateAdapter = true)
data class SubsonicResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val artists: ArtistsIndexResult? = null,
    val artist: ArtistID3? = null,
    val album: AlbumID3? = null,
    val playlists: PlaylistsResult? = null,
    val playlist: PlaylistWithEntries? = null,
    val searchResult3: SearchResult3? = null,
    val albumList2: AlbumListResult? = null,
    val randomSongs: RandomSongsResult? = null,
)

@JsonClass(generateAdapter = true)
data class SubsonicError(
    val code: Int,
    val message: String?,
)

@JsonClass(generateAdapter = true)
data class ArtistsIndexResult(
    val index: List<ArtistIndexBucket> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArtistIndexBucket(
    val name: String,
    val artist: List<ArtistID3> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArtistID3(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val album: List<AlbumID3> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumID3(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val playCount: Int? = null,
    val song: List<SubsonicSong> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SubsonicSong(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlaylistsResult(
    val playlist: List<PlaylistSummary> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaylistSummary(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val duration: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PlaylistWithEntries(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val duration: Int? = null,
    val entry: List<SubsonicSong> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SearchResult3(
    val artist: List<ArtistID3> = emptyList(),
    val album: List<AlbumID3> = emptyList(),
    val song: List<SubsonicSong> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumListResult(
    val album: List<AlbumID3> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RandomSongsResult(
    val song: List<SubsonicSong> = emptyList(),
)
