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
fun ArtistDetailsScreen(
    albums: List<AlbumUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onAlbumClick: (AlbumUiModel) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicator() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            albums.isEmpty() -> CenteredMessage { Text(text = "No albums for this artist") }
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
                        items(items = albums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlbumClick(album) }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = album.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                val yearText = album.year?.toString()?.plus(" • ") ?: ""
                                Text(
                                    text = "$yearText${album.songCount} songs",
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
