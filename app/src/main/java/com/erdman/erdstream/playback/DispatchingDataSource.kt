package com.erdman.erdstream.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Picks between two underlying DataSource.Factory implementations per
 * request, based on the request URI. Used to route Subsonic library
 * playback through the existing disk-cached pipeline while routing internet
 * radio streams through a separate, uncached, HTTP/1.1-pinned pipeline (see
 * PlaybackService for why radio needs the latter).
 */
@UnstableApi
class DispatchingDataSourceFactory(
    private val primaryFactory: DataSource.Factory,
    private val secondaryFactory: DataSource.Factory,
    private val usePrimary: (Uri) -> Boolean,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        DispatchingDataSource(primaryFactory, secondaryFactory, usePrimary)
}

@UnstableApi
private class DispatchingDataSource(
    private val primaryFactory: DataSource.Factory,
    private val secondaryFactory: DataSource.Factory,
    private val usePrimary: (Uri) -> Boolean,
) : DataSource {

    private val pendingListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        pendingListeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val chosen = if (usePrimary(dataSpec.uri)) {
            primaryFactory.createDataSource()
        } else {
            secondaryFactory.createDataSource()
        }
        pendingListeners.forEach { chosen.addTransferListener(it) }
        delegate = chosen
        return chosen.open(dataSpec)
    }

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun getUri(): Uri? = delegate?.uri

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val current = delegate ?: throw IllegalStateException("DataSource not opened")
        return current.read(buffer, offset, length)
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
