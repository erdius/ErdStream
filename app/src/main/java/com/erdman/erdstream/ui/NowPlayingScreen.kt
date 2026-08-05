package com.erdman.erdstream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erdman.erdstream.RepeatMode
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.slider.SliderMMD

@Composable
fun NowPlayingScreen(
    title: String,
    artist: String?,
    album: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffleOn: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Now Playing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = artist, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!album.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = album, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            SliderMMD(
                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                onValueChange = { value ->
                    if (durationMs > 0) {
                        onSeek((value * durationMs).toLong().coerceIn(0L, durationMs))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatMillis(currentPositionMs), fontSize = 14.sp)
                Text(text = formatMillis(durationMs), fontSize = 14.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(onClick = onPreviousClick, modifier = Modifier.size(64.dp)) {
                Icon(imageVector = Icons.Outlined.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }

            if (isBuffering) {
                CircularProgressIndicatorMMD(modifier = Modifier.size(56.dp))
            } else {
                IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            OutlinedIconButton(onClick = onNextClick, modifier = Modifier.size(64.dp)) {
                Icon(imageVector = Icons.Outlined.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onShuffleClick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle")
                    if (isShuffleOn) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.onBackground, CircleShape),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(48.dp))
            IconButton(onClick = onRepeatClick) {
                val icon = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Outlined.RepeatOne
                    else -> Icons.Outlined.Repeat
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = icon, contentDescription = "Repeat")
                    if (repeatMode != RepeatMode.OFF) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.onBackground, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
