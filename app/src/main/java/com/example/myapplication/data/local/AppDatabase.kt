package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DreamEntry
import com.example.myapplication.data.model.PowerOffSettings
import com.example.myapplication.data.model.RecoveryPlan
import com.example.myapplication.data.model.TodoItem
import com.example.myapplication.data.model.User
import androidx.room.TypeConverters

@Database(
<<<<<<< Updated upstream
    entities = [RecoveryPlan::class, TodoItem::class, DreamEntry::class, Device::class, PowerOffSettings::class, User::class],
    version = 8,
=======
    entities = [RecoveryPlan::class, TodoItem::class, DreamEntry::class, Device::class, PowerOffSettings::class],
    version = 6,
>>>>>>> Stashed changes
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recoveryPlanDao(): RecoveryPlanDao
    abstract fun todoDao(): TodoDao
    abstract fun dreamDao(): DreamDao
    abstract fun deviceDao(): DeviceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun userDao(): UserDao
    
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
