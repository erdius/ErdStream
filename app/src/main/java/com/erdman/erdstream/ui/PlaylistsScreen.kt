package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

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
                                    .clickable { onPlaylistClick(playlist) }
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
    }
}
