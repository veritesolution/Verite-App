package com.example.myapplication.data.repository

import com.example.myapplication.data.local.BedtimeItemDao
import com.example.myapplication.data.model.BedtimeItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class BedtimeRepository(private val dao: BedtimeItemDao) {

    fun getForDateFlow(date: String = LocalDate.now().toString()): Flow<List<BedtimeItem>> =
        dao.getForDateFlow(date)

    suspend fun getForDate(date: String = LocalDate.now().toString()): List<BedtimeItem> =
        dao.getForDate(date)

    suspend fun upsert(item: BedtimeItem) = dao.insert(item)

    suspend fun setChecked(itemId: String, checked: Boolean) = dao.setChecked(itemId, checked)

    suspend fun clearForDate(date: String = LocalDate.now().toString()) = dao.clearForDate(date)

    suspend fun delete(itemId: String) = dao.deleteById(itemId)

    suspend fun initializeDefaultRoutine(date: String = LocalDate.now().toString()) {
        val defaults = listOf(
            "Dim lights & phone away",
            "Brush teeth",
            "Skincare routine",
            "Brief meditation (5 min)",
            "Journaling one highlight of today",
            "Read a book"
        )
        defaults.forEachIndexed { index, name ->
            dao.insert(BedtimeItem(name = name, orderIndex = index, date = date))
        }
    }
}
