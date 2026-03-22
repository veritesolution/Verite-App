package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DreamEntry
import com.example.myapplication.data.model.PowerOffSettings
import com.example.myapplication.data.model.RecoveryPlan
import com.example.myapplication.data.model.SensorReading
import com.example.myapplication.data.model.Task
import com.example.myapplication.data.model.User
import com.example.myapplication.data.model.Habit
import com.example.myapplication.data.model.HabitCompletion
import com.example.myapplication.data.model.MoodEntry
import com.example.myapplication.data.model.BedtimeItem
import androidx.room.TypeConverters

@Database(
    entities = [
        RecoveryPlan::class, Task::class, DreamEntry::class, Device::class, 
        PowerOffSettings::class, User::class, SensorReading::class,
        Habit::class, HabitCompletion::class, MoodEntry::class, BedtimeItem::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recoveryPlanDao(): RecoveryPlanDao
    abstract fun taskDao(): TaskDao
    abstract fun dreamDao(): DreamDao
    abstract fun deviceDao(): DeviceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun userDao(): UserDao
    abstract fun sensorDao(): SensorDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun bedtimeItemDao(): BedtimeItemDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verite_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
