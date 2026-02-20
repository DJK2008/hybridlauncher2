package com.samsung.hybridlauncher.nowbar

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Highly optimized NotificationListenerService for the Now Bar.
 * Strictly filters for Media and Timer notifications to prevent CPU wakelocks
 * and keep the launcher's background footprint under the 200MB threshold.
 */
@RequiresApi(Build.VERSION_CODES.S)
class NowBarNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private var mediaController: MediaController? = null

    // Callbacks for Media Controller
    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaState()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Run an initial sweep of existing notifications upon launcher boot
        activeNotifications?.forEach { processNotification(it, isRemoved = false) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn, isRemoved = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        processNotification(sbn, isRemoved = true)
    }

    /**
     * Aggressive filtering pipeline. We offload processing to the Default dispatcher
     * to keep the main thread pristine for 60fps animations.
     */
    private fun processNotification(sbn: StatusBarNotification, isRemoved: Boolean) {
        scope.launch {
            val notification = sbn.notification
            val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0

            // Instantly drop standard transient notifications (texts, emails, etc.)
            if (!isOngoing) return@launch

            when {
                notification.isMediaNotification() -> handleMediaNotification(sbn, isRemoved)
                notification.isTimerNotification() -> handleTimerNotification(sbn, isRemoved)
            }
        }
    }

    private fun Notification.isMediaNotification(): Boolean {
        // Checking for MediaStyle guarantees it's a valid media player
        return this.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    }

    private fun Notification.isTimerNotification(): Boolean {
        // Look for chronometer flags which indicate an active timer/stopwatch
        return this.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
    }

    private fun handleMediaNotification(sbn: StatusBarNotification, isRemoved: Boolean) {
        if (isRemoved) {
            mediaController?.unregisterCallback(mediaControllerCallback)
            mediaController = null
            _nowBarState.value = NowBarState.Hidden
            return
        }

        val sessionToken = sbn.notification.extras.getParcelable<MediaSession.Token>(
            Notification.EXTRA_MEDIA_SESSION
        ) ?: return

        if (mediaController?.sessionToken != sessionToken) {
            mediaController?.unregisterCallback(mediaControllerCallback)
            mediaController = MediaController(this, sessionToken).apply {
                registerCallback(mediaControllerCallback)
            }
        }
        updateMediaState()
    }

    private fun handleTimerNotification(sbn: StatusBarNotification, isRemoved: Boolean) {
        if (isRemoved) {
            // Only hide if we aren't currently showing media
            if (_nowBarState.value is NowBarState.Timer) {
                _nowBarState.value = NowBarState.Hidden
            }
            return
        }

        val baseTime = sbn.notification.extras.getLong(Notification.EXTRA_CHRONOMETER_BASE, 0L)
        val isDown = sbn.notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_DOWN, false)

        _nowBarState.value = NowBarState.Timer(
            baseTimeMs = baseTime,
            isCountDown = isDown,
            appName = sbn.packageName
        )
    }

    private fun updateMediaState() {
        val controller = mediaController ?: return
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState ?: return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
        val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING

        _nowBarState.value = NowBarState.Media(
            trackTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            appPackage = controller.packageName
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaController?.unregisterCallback(mediaControllerCallback)
        job.cancel()
    }

    companion object {
        // StateFlow allows Jetpack Compose to reactively morph the Now Bar
        // without constantly querying the system.
        private val _nowBarState = MutableStateFlow<NowBarState>(NowBarState.Hidden)
        val nowBarState: StateFlow<NowBarState> = _nowBarState.asStateFlow()

        // Helper to check if the service is granted permission by the user
        fun requestRebind(componentName: ComponentName) {
            requestRebind(componentName)
        }
    }
}

/**
 * Represents the current visual state of the Morphing Now Bar.
 */
sealed class NowBarState {
    object Hidden : NowBarState()
    data class Media(
        val trackTitle: String,
        val artistName: String,
        val isPlaying: Boolean,
        val appPackage: String
    ) : NowBarState()

    data class Timer(
        val baseTimeMs: Long,
        val isCountDown: Boolean,
        val appName: String
    ) : NowBarState()
}