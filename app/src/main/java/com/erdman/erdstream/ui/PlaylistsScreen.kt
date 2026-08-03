package com.erdman.erdstream.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onDeletePlaylistClick: (PlaylistUiModel) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    var pendingDeletePlaylist by remember { mutableStateOf<PlaylistUiModel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicator() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            playlists.isEmpty() -> CenteredMessage { Text(text = "No playlists on your server") }
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
                        items(items = playlists, key = { it.id }) { playlist ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onPlaylistClick(playlist) },
                                        onLongClick = { pendingDeletePlaylist = playlist },
                                    )
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = playlist.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                    if (isScrollable) {
                        EInkScrollbar(state = listState, scope = scope)
                    }
                }
            }
        }

        val playlistToDelete = pendingDeletePlaylist
        if (playlistToDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDeletePlaylist = null },
                title = { Text("Delete playlist?") },
                text = { Text("Delete \"${playlistToDelete.name}\"? This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeletePlaylistClick(playlistToDelete)
                        pendingDeletePlaylist = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletePlaylist = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
