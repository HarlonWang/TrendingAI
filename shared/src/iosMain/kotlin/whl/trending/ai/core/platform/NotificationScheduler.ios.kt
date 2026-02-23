package whl.trending.ai.core.platform

import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import org.jetbrains.compose.resources.getString
import trending.shared.generated.resources.Res
import trending.shared.generated.resources.notification_title
import trending.shared.generated.resources.notification_content
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.localTimeZone

actual object NotificationScheduler {
    private val scope = MainScope()

    actual fun update(enabled: Boolean) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        if (enabled) {
            scheduleNotifications()
        } else {
            center.removeAllPendingNotificationRequests()
        }
    }

    actual fun requestPermission(onGranted: () -> Unit) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, error ->
            if (granted) {
                onGranted()
            }
        }
    }

    private fun scheduleNotifications() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()

        scope.launch {
            val title = getString(Res.string.notification_title)
            val body = getString(Res.string.notification_content)

            // 00:30 UTC
            scheduleAt(0, 30, "daily_0030", title, body)
            // 12:30 UTC
            scheduleAt(12, 30, "daily_1230", title, body)
        }
    }

    private fun scheduleAt(hour: Int, minute: Int, identifier: String, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
        }

        val components = NSDateComponents().apply {
            this.hour = hour.toLong()
            this.minute = minute.toLong()
            this.timeZone = NSTimeZone.timeZoneWithName("UTC") ?: NSTimeZone.localTimeZone
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)

        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            // Handle error if needed
        }
    }
}
