package com.erdman.erdstream.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD

@Composable
fun AlbumDetailsScreen(
    songs: List<SongUiModel>,
    currentSongId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onPlaySongClick: (SongUiModel) -> Unit,
    onShuffleClick: () -> Unit,
    onAddToPlaylistClick: (SongUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicatorMMD() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            songs.isEmpty() -> CenteredMessage { Text(text = "No songs in this album") }
            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    items(items = songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            showTrackNumber = true,
                            onClick = { onPlaySongClick(song) },
                            onAddToPlaylistClick = { onAddToPlaylistClick(song) },
                        )
                        DashedDivider()
                    }
                }

                FloatingActionButtonMMD(
                    onClick = onShuffleClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle album")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongUiModel,
    isCurrentlyPlaying: Boolean,
    showTrackNumber: Boolean,
    onClick: () -> Unit,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onRemoveFromPlaylistClick: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenu = onAddToPlaylistClick != null || onRemoveFromPlaylistClick != null

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasMenu) {
                        { showMenu = true }
                    } else {
                        null
                    },
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTrackNumber && song.track != null) {
                Text(
                    text = song.track.toString(),
                    fontSize = 14.sp,
                    modifier = Modifier.width(28.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 18.sp,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleParts = listOfNotNull(
                    song.artist?.takeIf { it.isNotBlank() },
                    formatDurationSeconds(song.durationSeconds),
                )
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showMenu) {
            DropdownMenuMMD(expanded = true, onDismissRequest = { showMenu = false }) {
                onAddToPlaylistClick?.let { action ->
                    DropdownMenuItemMMD(
                        text = { Text(text = "Add to Playlist") },
                        onClick = {
                            showMenu = false
                            action()
                        },
                    )
                }
                onRemoveFromPlaylistClick?.let { action ->
                    DropdownMenuItemMMD(
                        text = { Text(text = "Remove from Playlist") },
                        onClick = {
                            showMenu = false
                            action()
                        },
                    )
                }
            }
        }
    }
}
