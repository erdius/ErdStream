package com.erdman.erdstream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    onRemoveSongClick: (index: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicator() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            songs.isEmpty() -> CenteredMessage { Text(text = "This playlist is empty") }
            else -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .eInkVerticalScroll(listState, scope, isScrollable),
                        userScrollEnabled = false,
                    ) {
                        items(count = songs.size) { index ->
                            val song = songs[index]
                            SongRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                showTrackNumber = false,
                                onClick = { onPlaySongClick(song) },
                                onRemoveClick = { onRemoveSongClick(index) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (isScrollable) {
                        EInkScrollbar(state = listState, scope = scope)
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
