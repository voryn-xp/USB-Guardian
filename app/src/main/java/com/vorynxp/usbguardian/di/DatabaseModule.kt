package com.vorynxp.usbguardian.di

import android.content.Context
import androidx.room.Room
import com.vorynxp.usbguardian.data.db.AppDatabase
import com.vorynxp.usbguardian.data.db.LogDao
import com.vorynxp.usbguardian.data.db.UsbDeviceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "usb_guardian_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUsbDeviceDao(database: AppDatabase): UsbDeviceDao {
        return database.usbDeviceDao()
    }

    @Provides
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }
}
