package com.erdman.erdstream.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onDeletePlaylistClick: (PlaylistUiModel) -> Unit,
) {
    var pendingDeletePlaylist by remember { mutableStateOf<PlaylistUiModel?>(null) }
    val deletePlaylistSheetState = rememberModalBottomSheetMMDState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicatorMMD() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            playlists.isEmpty() -> CenteredMessage { Text(text = "No playlists on your server") }
            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
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
                        DashedDivider()
                    }
                }
            }
        }

        val playlistToDelete = pendingDeletePlaylist
        if (playlistToDelete != null) {
            ModalBottomSheetMMD(
                onDismissRequest = { pendingDeletePlaylist = null },
                sheetState = deletePlaylistSheetState,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextMMD(text = "Delete playlist?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { pendingDeletePlaylist = null },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cancel playlist deletion")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Delete \"${playlistToDelete.name}\"? This can't be undone.", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    ButtonMMD(
                        onClick = {
                            onDeletePlaylistClick(playlistToDelete)
                            pendingDeletePlaylist = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        TextMMD(text = "Delete", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButtonMMD(
                        onClick = { pendingDeletePlaylist = null },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        TextMMD(text = "Cancel", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
