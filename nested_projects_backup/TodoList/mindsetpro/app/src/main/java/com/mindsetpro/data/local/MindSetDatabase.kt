package com.mindsetpro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mindsetpro.data.model.*

@Database(
    entities = [
        Task::class,
        Habit::class,
        HabitCompletion::class,
        MoodEntry::class,
        BedtimeItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MindSetDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun bedtimeItemDao(): BedtimeItemDao

    companion object {
        @Volatile
        private var INSTANCE: MindSetDatabase? = null

        fun getInstance(context: Context): MindSetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MindSetDatabase::class.java,
                    "mindset_pro.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
