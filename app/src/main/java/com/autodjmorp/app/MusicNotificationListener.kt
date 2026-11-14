package com.autodjmorp

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        // Recupera notificaciones que ya estaban activas al conectar.
        try {
            activeNotifications?.forEach { readNotification(it) }
        } catch (_: Exception) {
        }

        readMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        readNotification(sbn)
        readMediaSession()
    }

    private fun isMusicApp(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name.contains("youtube") || name.contains("morphe")
    }

    private fun readNotification(sbn: StatusBarNotification) {
        if (!isMusicApp(sbn.packageName)) return

        val extras = sbn.notification.extras ?: return
        val title = (
            extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: extras.getCharSequence("android.title")
            )?.toString()?.trim().orEmpty()

        if (title.isBlank()) return

        val artist = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                ?: extras.getCharSequence("android.text")
            )?.toString()?.trim().orEmpty()

        saveTrack(title, artist, sbn.packageName)
    }

    private fun readMediaSession() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(this, MusicNotificationListener::class.java)
            val controller = manager.getActiveSessions(listener)
                .firstOrNull { isMusicApp(it.packageName) }
                ?: return

            val metadata: MediaMetadata = controller.metadata ?: return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.trim().orEmpty()

            if (title.isBlank()) return

            val artist = (
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                )?.trim().orEmpty()

            saveTrack(title, artist, controller.packageName)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun saveTrack(title: String, artist: String, packageName: String) {
        getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
            .edit()
            .putString("music_title", title)
            .putString("music_artist", artist)
            .putString("music_package", packageName)
            .apply()

        sendBroadcast(
            Intent("com.autodjmorp.MUSIC_UPDATED").setPackage(this.packageName)
        )
    }
}
