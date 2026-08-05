package com.erdman.erdstream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shuffle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
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
    var pendingRemoveIndex by remember { mutableStateOf<Int?>(null) }
    val removeSongSheetState = rememberModalBottomSheetMMDState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredMessage { CircularProgressIndicatorMMD() }
            errorMessage != null -> CenteredMessage { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            songs.isEmpty() -> CenteredMessage { Text(text = "This playlist is empty") }
            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    items(count = songs.size) { index ->
                        val song = songs[index]
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            showTrackNumber = false,
                            onClick = { onPlaySongClick(song) },
                            onLongClick = { pendingRemoveIndex = index },
                        )
                        DashedDivider()
                    }
                }

                FloatingActionButtonMMD(
                    onClick = onShuffleClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle playlist")
                }
            }
        }

        val removeIndex = pendingRemoveIndex
        if (removeIndex != null && removeIndex in songs.indices) {
            val song = songs[removeIndex]
            ModalBottomSheetMMD(
                onDismissRequest = { pendingRemoveIndex = null },
                sheetState = removeSongSheetState,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextMMD(text = "Remove song?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { pendingRemoveIndex = null },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cancel song removal")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Remove \"${song.title}\" from this playlist?", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    ButtonMMD(
                        onClick = {
                            onRemoveSongClick(removeIndex)
                            pendingRemoveIndex = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        TextMMD(text = "Remove", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButtonMMD(
                        onClick = { pendingRemoveIndex = null },
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
