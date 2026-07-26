package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    recentlyAdded: List<AlbumUiModel>,
    recentlyPlayed: List<AlbumUiModel>,
    mostPlayed: List<AlbumUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    isBuildingMix: Boolean,
    mixError: String?,
    onAlbumClick: (AlbumUiModel) -> Unit,
    onSeeAllRecentlyAddedClick: () -> Unit,
    onSeeAllRecentlyPlayedClick: () -> Unit,
    onSeeAllMostPlayedClick: () -> Unit,
    onAlbumMixClick: () -> Unit,
    onTrackMixClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicator() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = onAlbumMixClick,
                                enabled = !isBuildingMix,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(imageVector = Icons.Outlined.Album, contentDescription = null, modifier = Modifier.height(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Album Mix")
                            }
                            Button(
                                onClick = onTrackMixClick,
                                enabled = !isBuildingMix,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.height(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Track Mix")
                            }
                        }
                        if (isBuildingMix) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Building mix…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mixError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = mixError, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        SectionHeader(title = "Recently Added", onSeeAllClick = onSeeAllRecentlyAddedClick)
                    }
                    if (recentlyAdded.isEmpty()) {
                        item { EmptySectionMessage("No albums yet") }
                    } else {
                        items(recentlyAdded, key = { "added_${it.id}" }) { album ->
                            AlbumRow(album = album, onClick = { onAlbumClick(album) })
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Recently Played", onSeeAllClick = onSeeAllRecentlyPlayedClick)
                    }
                    if (recentlyPlayed.isEmpty()) {
                        item { EmptySectionMessage("Nothing played yet") }
                    } else {
                        items(recentlyPlayed, key = { "played_${it.id}" }) { album ->
                            AlbumRow(album = album, onClick = { onAlbumClick(album) })
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Most Played", onSeeAllClick = onSeeAllMostPlayedClick)
                    }
                    if (mostPlayed.isEmpty()) {
                        item { EmptySectionMessage("Nothing played yet") }
                    } else {
                        items(mostPlayed, key = { "frequent_${it.id}" }) { album ->
                            AlbumRow(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAllClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (onSeeAllClick != null) {
            OutlinedButton(onClick = onSeeAllClick) {
                Text("See all", fontSize = 13.sp)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EmptySectionMessage(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun AlbumRow(album: AlbumUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
            contentDescription = null,
            modifier = Modifier.height(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!album.artist.isNullOrBlank()) {
                Text(
                    text = album.artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    HorizontalDivider()
}
