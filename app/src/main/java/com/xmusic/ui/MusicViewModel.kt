package com.xmusic.ui

import android.app.Application
import android.support.v4.media.session.MediaSessionCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmusic.engine.InnerTuneEngine
import com.xmusic.engine.model.HomeShelf
import com.xmusic.engine.model.TrackItem
import com.xmusic.engine.playback.PlayerManager
import com.xmusic.notification.MediaNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val engine = InnerTuneEngine()
    val playerManager = PlayerManager(application, engine)
    private val notificationManager = MediaNotificationManager(application)
    val mediaSession = MediaSessionCompat(application, "XMusicMediaSession")

    val currentTrack: StateFlow<TrackItem?> = playerManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val queue: StateFlow<List<TrackItem>> = playerManager.queueFlow

    private val _homeShelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    val homeShelves: StateFlow<List<HomeShelf>> = _homeShelves.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val searchResults: StateFlow<List<TrackItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var progressJob: Job? = null
    private var searchJob: Job? = null

    init {
        mediaSession.isActive = true
        loadHomeFeed()

        // Sync playback state with notification
        viewModelScope.launch {
            playerManager.currentTrack.collect { track ->
                if (track != null) {
                    notificationManager.showNotification(track, playerManager.isPlaying.value, mediaSession)
                } else {
                    notificationManager.cancelNotification()
                }
            }
        }

        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                val track = playerManager.currentTrack.value
                if (track != null) {
                    notificationManager.showNotification(track, playing, mediaSession)
                }
                if (playing) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val cur = playerManager.player.currentPosition
                val dur = playerManager.player.duration
                _currentPositionMs.value = if (cur >= 0) cur else 0L
                _durationMs.value = if (dur > 0) dur else 0L
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        val cur = playerManager.player.currentPosition
        val dur = playerManager.player.duration
        _currentPositionMs.value = if (cur >= 0) cur else 0L
        _durationMs.value = if (dur > 0) dur else 0L
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _isLoadingHome.value = true
            try {
                val feed = engine.fetchHomeFeed()
                if (feed.isNotEmpty()) {
                    _homeShelves.value = feed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingHome.value = false
            }
        }
    }

    fun playTrack(track: TrackItem) {
        playerManager.playTrack(track)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun seekToFraction(fraction: Float) {
        playerManager.seekToFraction(fraction)
        val dur = playerManager.player.duration
        if (dur > 0) {
            _currentPositionMs.value = (dur * fraction).toLong()
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _isSearching.value = true
            try {
                val results = engine.search(query)
                _searchResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addToQueue(track: TrackItem) {
        playerManager.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun clearQueue() {
        playerManager.clearQueue()
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        searchJob?.cancel()
        notificationManager.cancelNotification()
        mediaSession.release()
        playerManager.release()
    }
}
