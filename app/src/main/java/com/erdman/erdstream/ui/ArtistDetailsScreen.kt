package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

@Composable
fun ArtistDetailsScreen(
    albums: List<AlbumUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onAlbumClick: (AlbumUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicatorMMD() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            albums.isEmpty() -> CenteredMessage { Text(text = "No albums for this artist") }
            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
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
                        HorizontalDividerMMD()
                    }
                }
            }
        }
    }
}
