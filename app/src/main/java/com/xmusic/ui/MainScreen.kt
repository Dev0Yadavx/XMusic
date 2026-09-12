package com.xmusic.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmusic.engine.model.TrackItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                // Persistent M3 Mini Player
                currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onClick = { isPlayerExpanded = true }
                    )
                }

                // M3 Expressive Navigation Bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                        label = { Text("Home", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("tab_home")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                        label = { Text("Search", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("tab_search")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue") },
                        label = { Text("Queue", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("tab_queue")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel) { isPlayerExpanded = true }
                1 -> SearchScreen(viewModel) { isPlayerExpanded = true }
                2 -> QueueScreen(viewModel)
            }

            // Expandable Fullscreen Now Playing Dialog
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentTrack?.let { track ->
                    NowPlayingScreen(
                        track = track,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        onDismiss = { isPlayerExpanded = false }
                    )
                }
            }
        }
    }
}
