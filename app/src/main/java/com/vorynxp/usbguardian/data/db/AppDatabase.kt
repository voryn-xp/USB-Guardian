package com.vorynxp.usbguardian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UsbDeviceEntity::class, LogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usbDeviceDao(): UsbDeviceDao
    abstract fun logDao(): LogDao
}
