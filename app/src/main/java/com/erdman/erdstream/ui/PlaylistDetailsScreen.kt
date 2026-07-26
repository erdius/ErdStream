package com.erdman.erdstream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistDetailsScreen(
    songs: List<SongUiModel>,
    currentSongId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onPlaySongClick: (SongUiModel) -> Unit,
    onShuffleClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicator() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            songs.isEmpty() -> CenteredMessage { Text(text = "This playlist is empty") }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(items = songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            showTrackNumber = false,
                            onClick = { onPlaySongClick(song) },
                        )
                        HorizontalDivider()
                    }
                }

                FloatingActionButton(
                    onClick = onShuffleClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle playlist")
                }
            }
        }
    }
}
