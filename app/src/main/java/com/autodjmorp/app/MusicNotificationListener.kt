package com.autodjmorp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent

class MusicNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.lowercase()

        // Detectar notificaciones de YouTube Music y Morphe
        if (!packageName.contains("youtube") &&
            !packageName.contains("morphe")) {
            return
        }

        val extras = sbn.notification.extras

        val title = extras.getCharSequence("android.title")
            ?.toString() ?: return

        val artist = extras.getCharSequence("android.text")
            ?.toString() ?: ""

        val preferences = getSharedPreferences(
            "AutoDJMusic",
            MODE_PRIVATE
        )

        preferences.edit()
            .putString("music_title", title)
            .putString("music_artist", artist)
            .putString("music_package", packageName)
            .apply()

        val intent = Intent("com.autodjmorp.MUSIC_UPDATED")
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
