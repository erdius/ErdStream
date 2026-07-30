package com.erdman.erdstream

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.erdman.erdstream.data.CredentialsManager
import com.erdman.erdstream.data.DuraSpeedSettingsManager
import com.erdman.erdstream.data.SubsonicRepository
import com.erdman.erdstream.data.TabSettingsManager
import com.erdman.erdstream.data.TranscodeSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class ErdStreamApplication : Application() {

    val credentialsManager: CredentialsManager by lazy { CredentialsManager(this) }
    val transcodeSettingsManager: TranscodeSettingsManager by lazy { TranscodeSettingsManager(this) }
    val tabSettingsManager: TabSettingsManager by lazy { TabSettingsManager(this) }
    val duraspeedSettingsManager: DuraSpeedSettingsManager by lazy { DuraSpeedSettingsManager(this) }
    val subsonicRepository: SubsonicRepository by lazy { SubsonicRepository(credentialsManager) }

    /**
     * On-disk cache for streamed audio, keyed by a stable "songId:bitrate"
     * string (see PlaybackService) rather than the request URL -- the URL
     * itself changes every play since auth tokens are re-signed per request.
     * Lets repeat plays and seeking within a song avoid re-fetching from the
     * network, and smooths out stutter caused by marginal throughput.
     *
     * SimpleCache's constructor does synchronous disk I/O to load/rebuild its
     * index, and PlaybackService.onCreate() (main thread) is the first thing
     * that touches this property when the app connects to it at launch. Using
     * the SQLite-backed index (StandaloneDatabaseProvider) instead of the
     * legacy file-based one is faster, and onCreate() below pre-warms this
     * property from a background thread so that first touch is already done
     * by the time PlaybackService needs it.
     */
    val mediaCache: SimpleCache by lazy {
        val cacheDirectory = File(cacheDir, "media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L) // 512 MB
        val databaseProvider = StandaloneDatabaseProvider(this)
        SimpleCache(cacheDirectory, evictor, databaseProvider)
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            mediaCache
        }
    }
}
