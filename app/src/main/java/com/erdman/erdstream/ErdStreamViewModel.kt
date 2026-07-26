package com.erdman.erdstream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.erdman.erdstream.data.SubsonicRepository
import com.erdman.erdstream.data.TranscodeSettingsManager
import com.erdman.erdstream.ui.SongUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, QUEUE, ONE }

data class PlaybackState(
    val queue: List<SongUiModel> = emptyList(),
    val queueIndex: Int? = null,
    val originalQueue: List<SongUiModel> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val nowPlayingSong: SongUiModel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class ErdStreamViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: ErdStreamApplication
        get() = getApplication()

    private val repository: SubsonicRepository get() = app.subsonicRepository
    private val transcodeSettingsManager: TranscodeSettingsManager get() = app.transcodeSettingsManager

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private var monitorJob: Job? = null
    private var lastCompletedSongId: String? = null

    private fun buildMediaItem(song: SongUiModel): MediaItem {
        val bitrate = transcodeSettingsManager.bitrate.value.kbps
        val url = repository.buildStreamUrl(song.id, bitrate)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        controller: MediaController?,
        isNewQueue: Boolean = true,
    ) {
        if (controller == null || queue.isEmpty() || startIndex !in queue.indices) return

        // Dedupe by song id: a song appearing twice in a source list (e.g. a
        // playlist with the same track added more than once) would otherwise
        // make shuffling look like it's replaying songs, and confuses
        // index-by-id lookups elsewhere. Each song should play at most once
        // per queue.
        val targetSongId = queue[startIndex].id
        val dedupedQueue = queue.distinctBy { it.id }
        val dedupedStartIndex = dedupedQueue.indexOfFirst { it.id == targetSongId }.coerceAtLeast(0)

        val previous = _playbackState.value
        val originalQueue = if (isNewQueue) dedupedQueue else previous.originalQueue
        val shuffle = if (isNewQueue) false else previous.isShuffleOn

        val mediaItems = dedupedQueue.map { buildMediaItem(it) }
        controller.setMediaItems(mediaItems, dedupedStartIndex, 0L)
        controller.repeatMode = when (previous.repeatMode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.prepare()
        controller.playWhenReady = true

        _playbackState.value = previous.copy(
            queue = dedupedQueue,
            queueIndex = dedupedStartIndex,
            originalQueue = originalQueue,
            isShuffleOn = shuffle,
            nowPlayingSong = dedupedQueue[dedupedStartIndex],
            isPlaying = true,
            isBuffering = true,
            positionMs = 0L,
            durationMs = dedupedQueue[dedupedStartIndex].durationSeconds?.times(1000L) ?: 0L,
        )
    }

    fun startShuffledPlaybackFromQueue(queue: List<SongUiModel>, controller: MediaController?) {
        if (queue.isEmpty()) return
        val shuffled = queue.shuffled()
        _playbackState.value = _playbackState.value.copy(originalQueue = queue, isShuffleOn = true)
        startPlaybackFromQueue(shuffled, 0, controller, isNewQueue = false)
    }

    fun togglePlayback(controller: MediaController?) {
        controller ?: return
        val state = _playbackState.value
        if (state.nowPlayingSong == null) return

        if (state.isPlaying) {
            controller.pause()
        } else {
            controller.playWhenReady = true
        }
        _playbackState.value = state.copy(isPlaying = !state.isPlaying)
    }

    fun playNext(controller: MediaController?) {
        controller ?: return
        val state = _playbackState.value
        val index = state.queueIndex ?: return
        if (index !in state.queue.indices) return

        val target = when {
            index < state.queue.lastIndex -> index + 1
            state.repeatMode == RepeatMode.QUEUE -> 0
            else -> return
        }
        controller.seekTo(target, 0L)
        controller.playWhenReady = true
        _playbackState.value = state.copy(
            queueIndex = target,
            nowPlayingSong = state.queue[target],
            positionMs = 0L,
            durationMs = state.queue[target].durationSeconds?.times(1000L) ?: 0L,
            isPlaying = true,
            isBuffering = true,
        )
    }

    fun playPrevious(controller: MediaController?) {
        controller ?: return
        val state = _playbackState.value
        val index = state.queueIndex ?: return
        if (index !in state.queue.indices) return

        val target = when {
            index > 0 -> index - 1
            state.repeatMode == RepeatMode.QUEUE -> state.queue.lastIndex
            else -> return
        }
        controller.seekTo(target, 0L)
        controller.playWhenReady = true
        _playbackState.value = state.copy(
            queueIndex = target,
            nowPlayingSong = state.queue[target],
            positionMs = 0L,
            durationMs = state.queue[target].durationSeconds?.times(1000L) ?: 0L,
            isPlaying = true,
            isBuffering = true,
        )
    }

    fun seekTo(positionMs: Long, controller: MediaController?) {
        controller?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
    }

    fun toggleShuffle(controller: MediaController?) {
        val state = _playbackState.value
        val index = state.queueIndex ?: return
        val current = state.nowPlayingSong ?: return
        if (state.queue.isEmpty() || index !in state.queue.indices) return

        if (!state.isShuffleOn) {
            val remaining = (state.queue.take(index) + state.queue.drop(index + 1)).shuffled()
            val newQueue = listOf(current) + remaining
            _playbackState.value = state.copy(
                originalQueue = state.queue,
                isShuffleOn = true,
            )
            startPlaybackFromQueue(newQueue, 0, controller, isNewQueue = false)
            seekTo(state.positionMs, controller)
        } else {
            if (state.originalQueue.isEmpty()) {
                _playbackState.value = state.copy(isShuffleOn = false)
                return
            }
            val restoreIndex = state.originalQueue.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
            _playbackState.value = state.copy(isShuffleOn = false)
            startPlaybackFromQueue(state.originalQueue, restoreIndex, controller, isNewQueue = false)
            seekTo(state.positionMs, controller)
        }
    }

    fun cycleRepeatMode(controller: MediaController?) {
        val state = _playbackState.value
        val newMode = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.QUEUE
            RepeatMode.QUEUE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.value = state.copy(repeatMode = newMode)
        controller?.repeatMode = when (newMode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun startPlaybackMonitoring(controller: MediaController) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (true) {
                val state = _playbackState.value
                if (state.nowPlayingSong == null) {
                    delay(1000L)
                    continue
                }

                val isPlaying = controller.playWhenReady
                val position = controller.currentPosition
                val duration = controller.duration
                val playerState = controller.playbackState
                val isBuffering = playerState == Player.STATE_BUFFERING

                var newState = state.copy(
                    isPlaying = isPlaying,
                    positionMs = position,
                    durationMs = if (duration > 0) duration else state.durationMs,
                    isBuffering = isBuffering,
                )

                val currentMediaId = controller.currentMediaItem?.mediaId
                if (currentMediaId != null) {
                    val targetIndex = state.queue.indexOfFirst { it.id == currentMediaId }
                    if (targetIndex >= 0 && targetIndex != state.queueIndex) {
                        newState = newState.copy(
                            queueIndex = targetIndex,
                            nowPlayingSong = state.queue[targetIndex],
                        )
                    }
                }

                var didAutoAdvance = false
                if (playerState == Player.STATE_ENDED) {
                    val songId = state.nowPlayingSong.id
                    if (songId != lastCompletedSongId) {
                        lastCompletedSongId = songId
                        if (state.repeatMode == RepeatMode.ONE) {
                            didAutoAdvance = true
                            controller.seekTo(state.queueIndex ?: 0, 0L)
                            controller.playWhenReady = true
                        }
                    }
                } else if (playerState == Player.STATE_READY && isPlaying) {
                    lastCompletedSongId = null
                }

                if (!didAutoAdvance) {
                    _playbackState.value = newState
                }

                delay(if (isPlaying) 200L else 1000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ErdStreamViewModel::class.java)) {
                        return ErdStreamViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class $modelClass")
                }
            }
    }
}
