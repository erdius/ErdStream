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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

@Composable
fun ArtistsScreen(
    artists: List<ArtistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onArtistClick: (ArtistUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicatorMMD() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            artists.isEmpty() -> CenteredMessage { Text(text = "No artists found on your server") }
            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    items(items = artists, key = { it.id }) { artist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArtistClick(artist) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = artist.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
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

@Composable
fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
