package com.xmusic.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.example.R
import com.xmusic.MainActivity
import com.xmusic.engine.model.TrackItem

class MediaNotificationManager(private val context: Context) {

    val channelId = "xmusic_playback_channel"
    val notificationId = 1001
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_PLAY_PAUSE = "com.xmusic.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.xmusic.ACTION_NEXT"
        const val ACTION_PREV = "com.xmusic.ACTION_PREV"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls and dynamic notification art"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        track: TrackItem,
        isPlaying: Boolean,
        mediaSession: MediaSessionCompat
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(ACTION_PREV).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(ACTION_NEXT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(track.title)
            .setContentText(track.artist.ifEmpty { "YouTube Music" })
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                "PlayPause",
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setOngoing(isPlaying)
            .build()
    }

    fun showNotification(track: TrackItem, isPlaying: Boolean, mediaSession: MediaSessionCompat) {
        try {
            manager.notify(notificationId, buildNotification(track, isPlaying, mediaSession))
        } catch (e: Exception) {
            // Handled gracefully if notification permission is not yet granted
        }
    }

    fun cancelNotification() {
        try {
            manager.cancel(notificationId)
        } catch (e: Exception) {
            // Handled gracefully
        }
    }
}
