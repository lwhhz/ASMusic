package com.kyant.backdrop.catalog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.Song

/** Exposes the current ExoPlayer to Android's native media controls and notification. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var receiver: BroadcastReceiver? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(android.R.drawable.ic_media_play)
            }
        )
        val player = PlaybackHolder.player?.exoPlayer ?: return
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, BridgePlayer(player))
            .setSessionActivity(contentIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    command: Int
                ): Int {
                    if (command == Player.COMMAND_SEEK_TO_NEXT ||
                        command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                    ) {
                        MusicServices.bridge.next()
                        return SessionResult.RESULT_SUCCESS
                    }
                    if (command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                        command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    ) {
                        MusicServices.bridge.previous()
                        return SessionResult.RESULT_SUCCESS
                    }
                    return super.onPlayerCommandRequest(session, controller, command)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    when (event?.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            MusicServices.bridge.next()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            MusicServices.bridge.previous()
                            return true
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()
        player.addListener(listener)
        val filter = IntentFilter().apply {
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_TOGGLE)
            addAction(ACTION_NEXT)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_TOGGLE -> PlaybackHolder.player?.toggle()
                    ACTION_PREVIOUS -> MusicServices.bridge.previous()
                    ACTION_NEXT -> MusicServices.bridge.next()
                }
                updateNotification()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        startMediaForeground()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { receiver?.let { unregisterReceiver(it) } }
        receiver = null
        session?.player?.removeListener(listener)
        session?.release()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startMediaForeground() {
        val notification = buildNotification() ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification() ?: return
        runCatching {
            getSystemService(NotificationManager::class.java).notify(ID, notification)
        }
    }

    private fun buildNotification(): Notification? {
        val player = PlaybackHolder.player?.exoPlayer ?: return null
        val token = session?.platformToken ?: return null
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title ?: lastSong?.name ?: "音乐"
        val artist = metadata?.artist ?: lastSong?.artist ?: ""
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(action: String, icon: Int, request: Int): Notification.Action =
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, icon),
                action,
                PendingIntent.getBroadcast(
                    this,
                    request,
                    Intent(this, MediaActionReceiver::class.java).setAction(action),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).build()
        val style = Notification.MediaStyle()
            .setMediaSession(token)
            .setShowActionsInCompactView(0, 1, 2)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(ACTION_PREVIOUS, android.R.drawable.ic_media_previous, 1))
            .addAction(
                action(
                    ACTION_TOGGLE,
                    if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    2
                )
            )
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, 3))
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "媒体播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "系统媒体控制与播放通知"
                setShowBadge(false)
            }
        )
    }

    companion object {
        @Volatile private var instance: PlaybackService? = null
        @Volatile private var lastSong: Song? = null

        fun refresh(song: Song? = null) {
            if (song != null) lastSong = song
            instance?.updateNotification()
        }

        private const val CHANNEL = "media_playback"
        private const val ID = 3001
        const val ACTION_PREVIOUS = "com.asmusic.app.media.PREVIOUS"
        const val ACTION_TOGGLE = "com.asmusic.app.media.TOGGLE"
        const val ACTION_NEXT = "com.asmusic.app.media.NEXT"
    }
}

@UnstableApi
private class BridgePlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun play() {
        // Route system media play through the retry-aware entry so a network
        // failure can be recovered from the lock screen / notification too.
        PlaybackHolder.player?.resumePlay() ?: super.play()
    }

    override fun pause() {
        PlaybackHolder.player?.pause() ?: super.pause()
    }

    override fun seekToNext() {
        MusicServices.bridge.next()
    }

    override fun seekToPrevious() {
        MusicServices.bridge.previous()
    }
}

/** Broadcast target for the media-style notification buttons. */
class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlaybackService.ACTION_TOGGLE -> PlaybackHolder.player?.toggle()
            PlaybackService.ACTION_PREVIOUS -> MusicServices.bridge.previous()
            PlaybackService.ACTION_NEXT -> MusicServices.bridge.next()
        }
    }
}
