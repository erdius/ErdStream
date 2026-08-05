package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isSearching: Boolean,
    errorMessage: String?,
    results: SearchResults?,
    currentSongId: String?,
    onArtistClick: (ArtistUiModel) -> Unit,
    onAlbumClick: (AlbumUiModel) -> Unit,
    onSongClick: (SongUiModel) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search artists, albums, songs") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearchClick()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    onSearchClick()
                }) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                }
            },
        )

        if (isSearching) {
            CenteredMessage { CircularProgressIndicatorMMD() }
        } else if (errorMessage != null) {
            CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
        } else if (results != null) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .eInkVerticalScroll(listState, scope, isScrollable),
                userScrollEnabled = false,
            ) {
                if (results.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(results.artists) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArtistClick(artist) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(text = artist.name, fontSize = 16.sp)
                        }
                        DashedDivider()
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(results.albums) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAlbumClick(album) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(text = "${album.name} — ${album.artist ?: ""}", fontSize = 16.sp)
                        }
                        DashedDivider()
                    }
                }

                if (results.songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(results.songs) { song ->
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            showTrackNumber = false,
                            onClick = { onSongClick(song) },
                        )
                        DashedDivider()
                    }
                }

                if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
                    item {
                        Text(
                            text = "No results.",
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
            if (isScrollable) {
                EInkScrollbar(state = listState, scope = scope)
            }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
    )
}
