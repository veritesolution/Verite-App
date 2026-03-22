package com.mindsetpro.utils

import com.mindsetpro.data.local.BedtimeItemDao
import com.mindsetpro.data.model.BedtimeItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Bedtime Routine Manager.
 *
 * Provides a customizable checklist of nightly wind-down tasks.
 * Items are tracked per-day so users can see completion history.
 */
class BedtimeRoutineManager(private val dao: BedtimeItemDao) {

    companion object {
        val DEFAULT_ROUTINE = listOf(
            "Brush teeth",
            "Review tomorrow's tasks",
            "Set alarm",
            "Prepare clothes for tomorrow",
            "Read a book for 15 minutes",
            "Meditate for 10 minutes",
            "Lights out — goodnight!"
        )
    }

    /**
     * Initialize today's bedtime checklist (creates items if none exist).
     */
    suspend fun initializeToday() {
        val today = LocalDate.now().toString()
        val existing = dao.getForDate(today)
        if (existing.isEmpty()) {
            DEFAULT_ROUTINE.forEachIndexed { index, name ->
                dao.insert(
                    BedtimeItem(
                        name = name,
                        orderIndex = index,
                        isChecked = false,
                        date = today
                    )
                )
            }
        }
    }

    /**
     * Get today's bedtime checklist as a Flow for UI observation.
     */
    fun getTodayFlow(): Flow<List<BedtimeItem>> =
        dao.getForDateFlow(LocalDate.now().toString())

    /**
     * Toggle a checklist item.
     */
    suspend fun toggleItem(itemId: String, checked: Boolean) {
        dao.setChecked(itemId, checked)
    }

    /**
     * Check if the entire routine is complete.
     */
    suspend fun isRoutineComplete(): Boolean {
        val items = dao.getForDate(LocalDate.now().toString())
        return items.isNotEmpty() && items.all { it.isChecked }
    }

    /**
     * Get completion percentage for today.
     */
    suspend fun completionPercent(): Float {
        val items = dao.getForDate(LocalDate.now().toString())
        if (items.isEmpty()) return 0f
        return items.count { it.isChecked }.toFloat() / items.size
    }

    /**
     * Reset today's routine (uncheck all).
     */
    suspend fun resetToday() {
        val items = dao.getForDate(LocalDate.now().toString())
        for (item in items) {
            dao.setChecked(item.id, false)
        }
    }
}
