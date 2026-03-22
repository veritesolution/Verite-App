package com.mindsetpro.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized SharedPreferences manager for all app settings.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mindsetpro_prefs", Context.MODE_PRIVATE)

    // ── Onboarding ───────────────────────────────────────────────────────────

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    // ── Notification Times ───────────────────────────────────────────────────

    var habitReminderHour: Int
        get() = prefs.getInt(KEY_HABIT_REMINDER_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_HABIT_REMINDER_HOUR, value).apply()

    var bedtimeReminderHour: Int
        get() = prefs.getInt(KEY_BEDTIME_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_BEDTIME_HOUR, value).apply()

    // ── Feature Toggles ──────────────────────────────────────────────────────

    var firebaseSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIREBASE_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_FIREBASE_SYNC, value).apply()

    var llmFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_LLM_FALLBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_LLM_FALLBACK, value).apply()

    var anthropicApiKey: String
        get() = prefs.getString(KEY_ANTHROPIC_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_KEY, value).apply()

    // ── Demo Data ────────────────────────────────────────────────────────────

    var demoDataSeeded: Boolean
        get() = prefs.getBoolean(KEY_DEMO_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_SEEDED, value).apply()

    // ── Last Sync ────────────────────────────────────────────────────────────

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    // ── Theme ────────────────────────────────────────────────────────────────

    var useDynamicColors: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, value).apply()

    // ── Clear All ────────────────────────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_HABIT_REMINDER_HOUR = "habit_reminder_hour"
        private const val KEY_BEDTIME_HOUR = "bedtime_reminder_hour"
        private const val KEY_FIREBASE_SYNC = "firebase_sync_enabled"
        private const val KEY_LLM_FALLBACK = "llm_fallback_enabled"
        private const val KEY_ANTHROPIC_KEY = "anthropic_api_key"
        private const val KEY_DEMO_SEEDED = "demo_data_seeded"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_DYNAMIC_COLORS = "use_dynamic_colors"
    }
}
