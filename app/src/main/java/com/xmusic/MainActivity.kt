package com.xmusic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.xmusic.notification.MediaNotificationManager
import com.xmusic.ui.MainScreen
import com.xmusic.ui.MusicViewModel
import com.xmusic.ui.theme.XMusicTheme

open class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied handled gracefully */ }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MediaNotificationManager.ACTION_PREV -> viewModel.playPrevious()
                MediaNotificationManager.ACTION_PLAY_PAUSE -> viewModel.togglePlayPause()
                MediaNotificationManager.ACTION_NEXT -> viewModel.playNext()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Register broadcast receiver for playback notification actions
        val filter = IntentFilter().apply {
            addAction(MediaNotificationManager.ACTION_PREV)
            addAction(MediaNotificationManager.ACTION_PLAY_PAUSE)
            addAction(MediaNotificationManager.ACTION_NEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackReceiver, filter)
        }

        setContent {
            XMusicTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(playbackReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
    }
}
