package com.example.myapplication.data.repository

import android.content.Context
import com.example.myapplication.data.model.NotificationType

/**
 * Static helper for pushing app-wide notifications from any Activity, Service, or ViewModel.
 * All methods are fire-and-forget (coroutine-backed via the repository).
 *
 * Usage:
 *   NotificationHelper.onDeviceConnected(context, "Vérité Headband")
 *   NotificationHelper.onApiError(context, "Connection to Oryn failed")
 */
object NotificationHelper {

    private fun repo(context: Context) = NotificationRepository.getInstance(context)

    // ── Device Events ───────────────────────────────────────

    fun onDeviceConnected(context: Context, deviceName: String) {
        repo(context).pushSuccess(
            title = "Device Connected",
            message = "$deviceName is paired and streaming data.",
            actionRoute = "dashboard"
        )
    }

    fun onDeviceDisconnected(context: Context, deviceName: String) {
        repo(context).pushWarning(
            title = "Device Disconnected",
            message = "$deviceName lost connection. Reconnect to resume tracking.",
            actionRoute = "dashboard"
        )
    }

    fun onLowBattery(context: Context, deviceName: String, level: Int) {
        repo(context).pushWarning(
            title = "Low Battery",
            message = "$deviceName is at $level%. Charge soon to avoid interruptions."
        )
    }

    // ── AI / Oryn Events ────────────────────────────────────

    fun onAiSessionComplete(context: Context, sessionTitle: String) {
        repo(context).pushAiInsight(
            title = "Session Summary Ready",
            message = "Your conversation \"$sessionTitle\" has been summarized.",
            actionRoute = "analytics"
        )
    }

    fun onCrisisDetected(context: Context) {
        repo(context).pushNotification(
            title = "We're Here For You",
            message = "Oryn detected you might be going through a tough time. Support resources are available.",
            type = NotificationType.AI_INSIGHT,
            actionRoute = "dashboard"
        )
    }

    // ── Error Events ────────────────────────────────────────

    fun onApiError(context: Context, detail: String) {
        repo(context).pushError(
            title = "Connection Error",
            message = detail
        )
    }

    fun onServerDown(context: Context) {
        repo(context).pushError(
            title = "Server Unavailable",
            message = "Vérité server is not responding. Some features may be limited."
        )
    }

    fun onAuthFailed(context: Context) {
        repo(context).pushError(
            title = "Authentication Failed",
            message = "Unable to verify your session. Please restart the app if this persists."
        )
    }

    fun onNetworkLost(context: Context) {
        repo(context).pushWarning(
            title = "No Internet",
            message = "You're offline. AI features and cloud sync are paused."
        )
    }

    fun onNetworkRestored(context: Context) {
        repo(context).pushSuccess(
            title = "Back Online",
            message = "Internet connection restored. All features are active."
        )
    }

    // ── Sleep Events ────────────────────────────────────────

    fun onSleepSessionComplete(context: Context, quality: String) {
        repo(context).pushSleepInsight(
            title = "Sleep Report Ready",
            message = "Your sleep quality was $quality. View detailed insights."
        )
    }

    fun onBedtimeReminder(context: Context) {
        repo(context).pushSleepInsight(
            title = "Bedtime Approaching",
            message = "Time to start winding down for better sleep quality."
        )
    }

    // ── Achievement Events ──────────────────────────────────

    fun onStreakMilestone(context: Context, habit: String, days: Int) {
        repo(context).pushAchievement(
            title = "Streak Milestone!",
            message = "You've kept \"$habit\" going for $days days straight!"
        )
    }

    fun onTaskGoalReached(context: Context, count: Int) {
        repo(context).pushAchievement(
            title = "Daily Goal Reached",
            message = "You completed $count tasks today. Great work!"
        )
    }

    // ── System Events ───────────────────────────────────────

    fun onAppUpdated(context: Context, version: String) {
        repo(context).pushNotification(
            title = "App Updated",
            message = "Vérité $version is ready with new features and improvements.",
            type = NotificationType.SYSTEM
        )
    }

    fun onWelcome(context: Context) {
        repo(context).pushNotification(
            title = "Welcome to Vérité",
            message = "Your wellness journey starts here. Explore the dashboard to get started.",
            type = NotificationType.INFO,
            actionRoute = "dashboard"
        )
    }
}
