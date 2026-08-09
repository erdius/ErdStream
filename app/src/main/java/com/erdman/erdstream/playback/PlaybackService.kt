package com.erdman.erdstream.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
        val subsonicDataSourceFactory = CacheDataSource.Factory()
            .setCache(app.mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Internet radio (Icecast/Shoutcast) streams are live and must never
        // be disk-cached, and need their own connection pinned to HTTP/1.1 --
        // see the comment on radioDataSourceFactory below for why. Dispatch
        // by comparing each request's host against the configured Subsonic
        // server's host, re-read per request in case the user switches
        // servers without restarting this long-lived service.
        val radioDataSourceFactory = buildRadioDataSourceFactory()
        val mediaDataSourceFactory = DispatchingDataSourceFactory(
            primaryFactory = subsonicDataSourceFactory,
            secondaryFactory = radioDataSourceFactory,
            usePrimary = { uri ->
                val subsonicHost = app.credentialsManager.credentials.value?.serverUrl
                    ?.let { android.net.Uri.parse(it).host }
                subsonicHost != null && uri.host == subsonicHost
            },
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(mediaDataSourceFactory)

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

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // A fresh MediaItem means any previous station's live
                // metadata is stale until new ICY data arrives.
                app.radioMetadataManager.updateIcyStreamTitle(null)
            }

            // Icecast/Shoutcast in-band "StreamTitle" metadata arrives as an
            // IcyInfo entry here. It does NOT get merged into
            // Player.mediaMetadata / MediaController.mediaMetadata, so it
            // must be read from this raw callback rather than polled from
            // the controller.
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                super.onMetadata(metadata)
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        app.radioMetadataManager.updateIcyStreamTitle(entry.title)
                    }
                }
            }
        })

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

    /**
     * Icecast/Shoutcast in-band metadata ("StreamTitle") is interleaved into
     * the raw HTTP/1.1 response body at a fixed byte interval (icy-metaint).
     * That convention doesn't survive HTTP/2 framing, and Android's
     * HttpURLConnection (what DefaultHttpDataSource wraps) will silently
     * negotiate HTTP/2 with any server that offers it via ALPN -- which most
     * modern reverse proxies (Caddy, nginx) do by default, even for a plain
     * Icecast mount. That leaves the Icy-Metadata header honored
     * (icy-metaint still arrives) but the body byte-alignment broken, so no
     * IcyInfo/StreamTitle is ever parsed. Pin this OkHttp client to HTTP/1.1
     * to guarantee ICY parsing works regardless of what the origin/proxy
     * would otherwise offer.
     */
    @OptIn(UnstableApi::class)
    private fun buildRadioDataSourceFactory(): DataSource.Factory {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
        return OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf("Icy-Metadata" to "1"))
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
