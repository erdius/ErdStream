package com.erdman.erdstream.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.erdman.erdstream.ErdStreamApplication
import com.erdman.erdstream.MainActivity

/**
 * Media3-based playback service. Streams directly from self-authenticated
 * Subsonic stream URLs (built by SubsonicRepository.buildStreamUrl), which
 * already carry auth params and any server-side transcoding request -- the
 * player itself does no decoding/encoding beyond normal playback.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "erdstream_playback_channel"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val loadControl = DefaultLoadControl.Builder()
            // Buffer much further ahead than ExoPlayer's own defaults (which
            // are 50s/50s/2.5s/5s) to smooth out stutter on cellular, where
            // throughput can dip well below what server-side transcoding
            // needs for a moment. Audio is cheap to buffer, so there's little
            // downside to buffering minutes ahead on a good connection, and
            // it means more headroom to coast through a bad patch. The
            // post-rebuffer threshold in particular was too low before (5s)
            // and could cause a stutter-loop: resume, stall again, resume,
            // stall again.
            .setBufferDurationsMs(60_000, 180_000, 2_500, 15_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val app = application as ErdStreamApplication

        // Longer HTTP timeouts than ExoPlayer's default (8s/8s), which can be
        // tight on degraded cellular combined with server-side transcoding
        // startup latency.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        // Cache streamed audio to disk under a stable "songId:bitrate" key
        // (not the request URL, which re-signs its auth token every play) so
        // repeat plays and seeks don't re-fetch over the network.
        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val songId = dataSpec.uri.getQueryParameter("id")
            val maxBitRate = dataSpec.uri.getQueryParameter("maxBitRate") ?: "original"
            val stableKey = if (songId != null) "$songId:$maxBitRate" else dataSpec.uri.toString()
            dataSpec.buildUpon().setKey(stableKey).build()
        }
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(app.mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()

        setMediaNotificationProvider(notificationProvider)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ErdStream playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Music playback controls"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
