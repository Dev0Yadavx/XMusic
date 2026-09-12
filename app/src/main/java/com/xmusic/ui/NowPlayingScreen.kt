package com.xmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xmusic.engine.model.TrackItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    track: TrackItem,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val currentPosMs by viewModel.currentPositionMs.collectAsState()
    val totalDurMs by viewModel.durationMs.collectAsState()

    var isUserDragging by remember { mutableStateOf(false) }
    var userSliderPos by remember { mutableFloatStateOf(0f) }

    val currentFraction = remember(currentPosMs, totalDurMs) {
        if (totalDurMs > 0) (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val sliderPos = if (isUserDragging) userSliderPos else currentFraction

    Surface(
        modifier = Modifier.fillMaxSize().testTag("now_playing_screen"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_now_playing_button")) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(32.dp))
                }
                Text(
                    text = "PLAYING FROM QUEUE",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { /* Menu */ }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                }
            }

            // Big Squircle Album Art
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!track.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(110.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Track Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist.ifEmpty { "YouTube Music" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // M3 Dynamic Expressive Seekbar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPos,
                    onValueChange = {
                        isUserDragging = true
                        userSliderPos = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekToFraction(userSliderPos)
                        isUserDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("playback_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentText = formatDuration(if (isUserDragging) (totalDurMs * userSliderPos).toLong() else currentPosMs)
                    val totalText = if (totalDurMs > 0) formatDuration(totalDurMs) else "3:45"
                    Text(currentText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(totalText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Shuffle */ }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                FilledTonalIconButton(
                    onClick = { viewModel.playPrevious() },
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp).testTag("prev_button")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(28.dp))
                }

                // Main Play/Pause Button
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.size(80.dp).testTag("play_pause_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(40.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = { viewModel.playNext() },
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp).testTag("next_button")
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = { /* Repeat */ }) {
                    Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
