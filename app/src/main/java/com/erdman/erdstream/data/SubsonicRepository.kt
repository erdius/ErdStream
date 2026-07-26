package com.erdman.erdstream.data

import com.erdman.erdstream.ui.AlbumDetail
import com.erdman.erdstream.ui.AlbumUiModel
import com.erdman.erdstream.ui.ArtistUiModel
import com.erdman.erdstream.ui.PlaylistDetail
import com.erdman.erdstream.ui.PlaylistUiModel
import com.erdman.erdstream.ui.SearchResults
import com.erdman.erdstream.ui.SongUiModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class SubsonicException(message: String) : Exception(message)

/**
 * Wraps the Subsonic REST API behind clean suspend functions and UI models.
 * Rebuilds its Retrofit client whenever the configured server URL changes.
 */
class SubsonicRepository(
    private val credentialsManager: CredentialsManager,
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var cachedBaseUrl: String? = null
    private var cachedApi: SubsonicApi? = null

    private fun api(): SubsonicApi {
        val credentials = credentialsManager.credentials.value
            ?: throw SubsonicException("Not connected to a server")

        val baseUrl = "${credentials.serverUrl}/rest/"
        cachedApi?.let { if (cachedBaseUrl == baseUrl) return it }

        val client = OkHttpClient.Builder()
            .addInterceptor(SubsonicAuthInterceptor(credentialsManager))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val api = retrofit.create(SubsonicApi::class.java)
        cachedBaseUrl = baseUrl
        cachedApi = api
        return api
    }

    private fun SubsonicResponse.requireOk(): SubsonicResponse {
        if (status != "ok") {
            throw SubsonicException(error?.message ?: "Server returned an error")
        }
        return this
    }

    /**
     * Tests a server/username/password combination directly, without
     * touching stored credentials. Used by the server setup screen before
     * the user's credentials are saved.
     */
    suspend fun testConnection(serverUrl: String, username: String, password: String) {
        withContext(Dispatchers.IO) {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            val salt = randomSalt()
            val token = md5Hex(password + salt)

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val httpUrl = "$normalizedUrl/rest/ping.view".toHttpUrlOrThrow()
                .newBuilder()
                .addQueryParameter("u", username)
                .addQueryParameter("t", token)
                .addQueryParameter("s", salt)
                .addQueryParameter("v", SubsonicAuthInterceptor.API_VERSION)
                .addQueryParameter("c", SubsonicAuthInterceptor.CLIENT_NAME)
                .addQueryParameter("f", "json")
                .build()

            val request = Request.Builder().url(httpUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SubsonicException("Server returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val adapter = moshi.adapter(SubsonicEnvelope::class.java)
                val envelope = adapter.fromJson(body)
                    ?: throw SubsonicException("Unexpected response from server")
                envelope.response.requireOk()
            }
        }
    }

    suspend fun getArtists(): List<ArtistUiModel> = withContext(Dispatchers.IO) {
        val response = api().getArtists().response.requireOk()
        response.artists?.index.orEmpty()
            .flatMap { it.artist }
            .map { it.toUiModel() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getArtistAlbums(artistId: String): List<AlbumUiModel> = withContext(Dispatchers.IO) {
        val response = api().getArtist(artistId).response.requireOk()
        val artist = response.artist ?: throw SubsonicException("Artist not found")
        artist.album.map { it.toUiModel() }
    }

    suspend fun getAlbumDetail(albumId: String): AlbumDetail = withContext(Dispatchers.IO) {
        val response = api().getAlbum(albumId).response.requireOk()
        val album = response.album ?: throw SubsonicException("Album not found")
        AlbumDetail(
            album = album.toUiModel(),
            songs = album.song.map { it.toUiModel() },
        )
    }

    suspend fun getPlaylists(): List<PlaylistUiModel> = withContext(Dispatchers.IO) {
        val response = api().getPlaylists().response.requireOk()
        response.playlists?.playlist.orEmpty().map { it.toUiModel() }
    }

    suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail = withContext(Dispatchers.IO) {
        val response = api().getPlaylist(playlistId).response.requireOk()
        val playlist = response.playlist ?: throw SubsonicException("Playlist not found")
        PlaylistDetail(
            playlist = PlaylistUiModel(
                id = playlist.id,
                name = playlist.name,
                songCount = playlist.songCount ?: playlist.entry.size,
                durationSeconds = playlist.duration ?: 0,
            ),
            songs = playlist.entry.map { it.toUiModel() },
        )
    }

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val response = api().search3(query).response.requireOk()
        val result = response.searchResult3
        SearchResults(
            artists = result?.artist.orEmpty().map { it.toUiModel() },
            albums = result?.album.orEmpty().map { it.toUiModel() },
            songs = result?.song.orEmpty().map { it.toUiModel() },
        )
    }

    /** [type]: "newest" (recently added), "recent" (recently played), "frequent" (most played), "random". */
    suspend fun getAlbumList(type: String, size: Int = 10, offset: Int = 0): List<AlbumUiModel> =
        withContext(Dispatchers.IO) {
            val response = api().getAlbumList2(type, size, offset).response.requireOk()
            response.albumList2?.album.orEmpty().map { it.toUiModel() }
        }

    suspend fun getRandomSongs(size: Int = 50): List<SongUiModel> = withContext(Dispatchers.IO) {
        val response = api().getRandomSongs(size).response.requireOk()
        response.randomSongs?.song.orEmpty().map { it.toUiModel() }
    }

    /**
     * Builds a queue for "Album Mix": a handful of random albums, each
     * played through in track order, back to back.
     */
    suspend fun buildAlbumMixQueue(albumCount: Int = 5): List<SongUiModel> = withContext(Dispatchers.IO) {
        val albums = getAlbumList("random", size = albumCount)
        albums.flatMap { album -> getAlbumDetail(album.id).songs }
    }

    /**
     * Builds a direct, self-authenticated stream URL for a song, suitable for
     * handing straight to ExoPlayer. When [maxBitRateKbps] is 0 ("Original"),
     * no maxBitRate/format params are sent and the server streams the source
     * file as-is; otherwise the server transcodes on the fly.
     */
    fun buildStreamUrl(songId: String, maxBitRateKbps: Int): String {
        val credentials = credentialsManager.credentials.value
            ?: throw SubsonicException("Not connected to a server")

        val salt = randomSalt()
        val token = md5Hex(credentials.password + salt)

        val builder = "${credentials.serverUrl}/rest/stream.view".toHttpUrlOrThrow()
            .newBuilder()
            .addQueryParameter("id", songId)
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", SubsonicAuthInterceptor.API_VERSION)
            .addQueryParameter("c", SubsonicAuthInterceptor.CLIENT_NAME)

        if (maxBitRateKbps > 0) {
            builder.addQueryParameter("maxBitRate", maxBitRateKbps.toString())
            builder.addQueryParameter("format", TranscodeSettingsManager.TRANSCODE_FORMAT)
        }

        return builder.build().toString()
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private fun String.toHttpUrlOrThrow(): okhttp3.HttpUrl =
    try {
        toHttpUrl()
    } catch (_: IllegalArgumentException) {
        throw SubsonicException("Invalid server URL")
    }

private fun ArtistID3.toUiModel() = ArtistUiModel(
    id = id,
    name = name,
    albumCount = albumCount ?: album.size,
)

private fun AlbumID3.toUiModel() = AlbumUiModel(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    songCount = songCount ?: song.size,
    durationSeconds = duration ?: 0,
    year = year,
)

private fun SubsonicSong.toUiModel() = SongUiModel(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    track = track,
    durationSeconds = duration,
    suffix = suffix,
)

private fun PlaylistSummary.toUiModel() = PlaylistUiModel(
    id = id,
    name = name,
    songCount = songCount ?: 0,
    durationSeconds = duration ?: 0,
)
