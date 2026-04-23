package com.axon.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.axon.assistant.models.NotifItem
import java.util.LinkedList

/**
 * Background service that listens to all device notifications.
 * Maintains a ring buffer of last 10 notifications.
 * Requires user to grant Notification Access in device Settings.
 */
class NotificationService : NotificationListenerService() {

    companion object {
        private val buffer = LinkedList<NotifItem>()
        private const val MAX_SIZE = 10

        /**
         * Called by ActionExecutor when user says "read notifications".
         */
        fun getRecentNotifications(): List<NotifItem> {
            return buffer.toList().reversed()
        }

        fun clearBuffer() {
            buffer.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Skip low-priority / ongoing (e.g. music player, system)
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            packageManager
                .getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0))
                .toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val item = NotifItem(
            app   = appName,
            title = title,
            text  = text,
            time  = sbn.postTime
        )

        synchronized(buffer) {
            if (buffer.size >= MAX_SIZE) buffer.removeFirst()
            buffer.add(item)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally empty — we keep history even after dismiss
    }
}
