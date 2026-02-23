package whl.trending.ai.core.platform

/**
 * Platform-specific notification scheduler.
 */
expect object NotificationScheduler {
    /**
     * Updates the notification schedule based on the enabled state.
     */
    fun update(enabled: Boolean)

    /**
     * Requests notification permission if needed.
     */
    fun requestPermission(onGranted: () -> Unit)
}
