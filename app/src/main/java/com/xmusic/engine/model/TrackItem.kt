package com.xmusic.engine.model

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null
)

data class HomeShelf(
    val title: String,
    val items: List<TrackItem>
)

data class StreamAudioResult(
    val url: String,
    val itag: Int,
    val bitrate: Int,
    val isLiveHls: Boolean = false,
    val loudnessDb: Double = 0.0
)
