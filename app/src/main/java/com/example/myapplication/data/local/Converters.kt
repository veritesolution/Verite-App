package com.example.myapplication.data.local

import androidx.room.TypeConverter
import com.example.myapplication.data.model.DeviceType
import com.example.myapplication.data.model.NotificationType
import com.example.myapplication.data.model.PowerOffDuration

class Converters {
    @TypeConverter
    fun fromDeviceType(value: DeviceType): String {
        return value.name
    }
    
    @TypeConverter
    fun toDeviceType(value: String): DeviceType {
        return DeviceType.valueOf(value)
    }
    
    @TypeConverter
    fun fromPowerOffDuration(value: PowerOffDuration): String {
        return value.name
    }
    
    @TypeConverter
    fun toPowerOffDuration(value: String): PowerOffDuration {
        return PowerOffDuration.valueOf(value)
    }

    @TypeConverter
    fun fromNotificationType(value: NotificationType): String {
        return value.name
    }

    @TypeConverter
    fun toNotificationType(value: String): NotificationType {
        return try { NotificationType.valueOf(value) } catch (_: Exception) { NotificationType.INFO }
    }
}
