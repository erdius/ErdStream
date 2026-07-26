package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlbumDetailsScreen(
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
            songs.isEmpty() -> CenteredMessage { Text(text = "No songs in this album") }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(items = songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            showTrackNumber = true,
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
                    Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle album")
                }
            }
        }
    }
}

@Composable
fun SongRow(
    song: SongUiModel,
    isCurrentlyPlaying: Boolean,
    showTrackNumber: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
}
