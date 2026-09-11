package com.erdman.erdstream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One consumer processes synchronously enqueued tap indices in FIFO order. */
internal class PlaylistRemovalQueue(
    scope: CoroutineScope,
    remove: suspend (Int) -> Boolean,
) {
    private val indices = Channel<Int>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for (index in indices) {
            if (!remove(index)) {
                while (indices.tryReceive().isSuccess) {
                    // A failed removal invalidates every index queued behind it.
                }
            }
        }
    }

    fun enqueue(index: Int) {
        indices.trySend(index)
    }

    fun dispose() {
        indices.cancel()
        worker.cancel()
    }
}
