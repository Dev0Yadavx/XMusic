package com.xmusic.engine.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.xmusic.engine.InnerTuneEngine
import com.xmusic.engine.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerManager(
    private val context: Context,
    private val engine: InnerTuneEngine
) {
    private val renderersFactory = DefaultRenderersFactory(context.applicationContext)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        .setEnableDecoderFallback(true)

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderersFactory)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val queue = mutableListOf<TrackItem>()
    private var currentIndex = -1

    private val _currentTrack = MutableStateFlow<TrackItem?>(null)
    val currentTrack: StateFlow<TrackItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queueFlow = MutableStateFlow<List<TrackItem>>(emptyList())
    val queueFlow: StateFlow<List<TrackItem>> = _queueFlow.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                // Gracefully recover on decoder reclaim or network interruption
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                ) {
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    fun playTrack(track: TrackItem) {
        _currentTrack.value = track
        // Ensure track is in queue
        if (!queue.any { it.id == track.id }) {
            queue.add(0, track)
            currentIndex = 0
            _queueFlow.value = queue.toList()
        } else {
            currentIndex = queue.indexOfFirst { it.id == track.id }
        }

        CoroutineScope(Dispatchers.Main).launch {
            val stream = engine.resolveStream(track.id) ?: return@launch
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("com.google.android.apps.youtube.vr.oculus/1.37")

            val mediaSource = if (stream.isLiveHls) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(stream.url))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(stream.url))
            }

            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()

            // Automatic Radio Queue generation
            loadAutoQueue(track.id)
        }
    }

    private fun loadAutoQueue(videoId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val nextSongs = engine.fetchRadioQueue(videoId)
            val current = _currentTrack.value
            queue.clear()
            if (current != null) {
                queue.add(current)
            }
            queue.addAll(nextSongs.filter { it.id != current?.id })
            currentIndex = 0
            _queueFlow.value = queue.toList()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && _currentTrack.value != null) {
                playTrack(_currentTrack.value!!)
            } else {
                player.play()
            }
        }
    }

    fun playNext() {
        if (queue.isNotEmpty() && currentIndex < queue.size - 1) {
            currentIndex++
            playTrack(queue[currentIndex])
        }
    }

    fun playPrevious() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (queue.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            playTrack(queue[currentIndex])
        } else {
            player.seekTo(0)
        }
    }

    fun seekToFraction(fraction: Float) {
        val dur = player.duration
        if (dur > 0) {
            player.seekTo((dur * fraction).toLong())
        }
    }

    fun addToQueue(track: TrackItem) {
        queue.add(track)
        _queueFlow.value = queue.toList()
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            queue.removeAt(index)
            if (index < currentIndex) {
                currentIndex--
            }
            _queueFlow.value = queue.toList()
        }
    }

    fun clearQueue() {
        val cur = _currentTrack.value
        queue.clear()
        if (cur != null) {
            queue.add(cur)
            currentIndex = 0
        } else {
            currentIndex = -1
        }
        _queueFlow.value = queue.toList()
    }

    fun release() {
        player.release()
    }
}
