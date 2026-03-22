package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app-wide persistent settings using SharedPreferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var habitReminderHour: Int
        get() = prefs.getInt(KEY_HABIT_REMINDER_HOUR, 8)
        set(value) = prefs.edit().putInt(KEY_HABIT_REMINDER_HOUR, value).apply()

    var bedtimeHour: Int
        get() = prefs.getInt(KEY_BEDTIME_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_BEDTIME_HOUR, value).apply()

    var firebaseSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIREBASE_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_FIREBASE_SYNC, value).apply()

    var llmFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_LLM_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_LLM_FALLBACK, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD, value).apply()

    // --- Appearance Settings ---
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    // --- Language Settings ---
    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    // --- Notifications Settings ---
    var allNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALL_NOTIFICATIONS, value).apply()

    var dailyRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_REMINDERS, value).apply()

    // --- Privacy & Security Settings ---
    var shareAnalytics: Boolean
        get() = prefs.getBoolean(KEY_SHARE_ANALYTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_ANALYTICS, value).apply()

    var biometricLogin: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOGIN, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOGIN, value).apply()

    companion object {
        private const val PREFS_NAME = "verite_settings"
        private const val KEY_HABIT_REMINDER_HOUR = "habit_reminder_hour"
        private const val KEY_BEDTIME_HOUR = "bedtime_hour"
        private const val KEY_FIREBASE_SYNC = "firebase_sync"
        private const val KEY_LLM_FALLBACK = "llm_fallback"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        
        // New Keys
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_ALL_NOTIFICATIONS = "all_notifications_enabled"
        private const val KEY_DAILY_REMINDERS = "daily_reminders_enabled"
        private const val KEY_SHARE_ANALYTICS = "share_analytics"
        private const val KEY_BIOMETRIC_LOGIN = "biometric_login"
    }
}
