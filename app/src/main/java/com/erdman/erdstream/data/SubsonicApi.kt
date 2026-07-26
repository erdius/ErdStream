package com.erdman.erdstream.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Subsonic REST API (as implemented by
 * Navidrome). Authentication query parameters are added by
 * [SubsonicAuthInterceptor], not here.
 */
interface SubsonicApi {

    @GET("ping.view")
    suspend fun ping(): SubsonicEnvelope

    @GET("getArtists.view")
    suspend fun getArtists(): SubsonicEnvelope

    @GET("getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    @GET("getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @GET("getPlaylists.view")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
    ): SubsonicEnvelope

    /** [type] is one of: random, newest, recent, frequent, highest, starred, alphabeticalByName, ... */
    @GET("getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 10,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope

    @GET("getRandomSongs.view")
    suspend fun getRandomSongs(@Query("size") size: Int = 50): SubsonicEnvelope
}
