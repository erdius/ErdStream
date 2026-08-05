package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD
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

    // fillMaxSize() with no padding at the root, same as every other
    // screen -- padding lives on individual elements/contentPadding below
    // instead. A padded root leaves a permanent unpainted border in every
    // state this screen can be in, which is exactly the kind of gap that
    // lets the previous tab's content keep showing through on e-ink.
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
            // CenteredMessage's default modifier is fillMaxSize(), which
            // only fills correctly when it's the sole content of a Box
            // (every other tab). Here it's a sibling of the search field
            // inside a Column, so it needs weight(1f) to claim the actual
            // remaining space instead of stacking a full-screen-tall box
            // below the field -- otherwise its content renders far past
            // the visible area instead of centered in the remaining space,
            // which is what was leaving the previous tab's content as the
            // only thing visibly occupying that region.
            CenteredMessage(modifier = Modifier.weight(1f).fillMaxSize()) { CircularProgressIndicatorMMD() }
        } else if (errorMessage != null) {
            CenteredMessage(modifier = Modifier.weight(1f).fillMaxSize()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else if (results == null) {
            CenteredMessage(modifier = Modifier.weight(1f).fillMaxSize()) {
                Text(
                    text = "Search your library",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            LazyColumnMMD(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
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
