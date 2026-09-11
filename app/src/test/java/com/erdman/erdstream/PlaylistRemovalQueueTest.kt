package com.erdman.erdstream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRemovalQueueTest {
    @Test fun independentLaunchesCanRemoveTheWrongSong() = runBlocking {
        val songs = mutableListOf("A", "B", "C", "D")
        val firstMayFinish = CompletableDeferred<Unit>()
        val first = launch { firstMayFinish.await(); songs.removeAt(0) }
        val second = launch { songs.removeAt(1) }
        second.join()
        firstMayFinish.complete(Unit)
        first.join()
        assertEquals(listOf("C", "D"), songs) // Tap-order result would be B, D.
    }

    @Test fun queuedRemovalsWaitAndPreserveTapOrder() = runBlocking {
        withTimeout(5000) {
            val songs = mutableListOf("A", "B", "C", "D", "E")
            val entered = mutableListOf<Int>()
            val gate = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val queue = PlaylistRemovalQueue(this) { index ->
                entered.add(index)
                if (entered.size == 1) gate.await()
                songs.removeAt(index)
                if (entered.size == 3) done.complete(Unit)
                true
            }
            try {
                queue.enqueue(0); queue.enqueue(1); queue.enqueue(2)
                yield()
                assertEquals(listOf(0), entered)
                gate.complete(Unit)
                done.await()
                assertEquals(listOf(0, 1, 2), entered)
                assertEquals(listOf("B", "D"), songs)
            } finally { queue.dispose() }
        }
    }

    @Test fun failedRemovalDropsIndicesQueuedBehindIt() = runBlocking {
        withTimeout(5000) {
            val attempted = mutableListOf<Int>()
            val firstMayFail = CompletableDeferred<Unit>()
            val queue = PlaylistRemovalQueue(this) { index ->
                attempted.add(index)
                firstMayFail.await()
                false
            }
            try {
                queue.enqueue(0); queue.enqueue(1); queue.enqueue(2)
                yield()
                firstMayFail.complete(Unit)
                yield()
                assertEquals(listOf(0), attempted)
            } finally { queue.dispose() }
        }
    }

    @Test fun disposalCancelsOldWorkAndNewPlaylistIsIndependent() = runBlocking {
        withTimeout(5000) {
            val oldCalls = mutableListOf<Int>()
            val gate = CompletableDeferred<Unit>()
            val old = PlaylistRemovalQueue(this) { oldCalls.add(it); gate.await(); true }
            old.enqueue(0); old.enqueue(1)
            yield()
            old.dispose()
            val done = CompletableDeferred<Int>()
            val next = PlaylistRemovalQueue(this) { done.complete(it); true }
            try {
                next.enqueue(3)
                assertEquals(3, done.await())
                gate.complete(Unit)
                yield()
                assertEquals(listOf(0), oldCalls)
            } finally { next.dispose() }
        }
    }
}
