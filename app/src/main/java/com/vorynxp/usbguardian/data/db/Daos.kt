package com.vorynxp.usbguardian.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsbDeviceDao {
    @Query("SELECT * FROM usb_devices ORDER BY lastSeen DESC")
    fun getAllDevicesFlow(): Flow<List<UsbDeviceEntity>>

    @Query("SELECT * FROM usb_devices WHERE vendorId = :vendorId AND productId = :productId LIMIT 1")
    suspend fun getDevice(vendorId: Int, productId: Int): UsbDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: UsbDeviceEntity)

    @Query("SELECT COUNT(*) FROM usb_devices")
    fun getDevicesCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM usb_devices WHERE status = 'Allowed'")
    fun getAllowedCountFlow(): Flow<Int>
}

@Dao
interface LogDao {
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntity>>

    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM event_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM event_logs WHERE action = 'Blocked' AND timestamp >= :startTimestamp")
    fun getBlockedCountSinceFlow(startTimestamp: Long): Flow<Int>
}
