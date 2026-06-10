package com.vorynxp.usbguardian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usb_devices", primaryKeys = ["vendorId", "productId"])
data class UsbDeviceEntity(
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val lastSeen: Long,
    val status: String // "Allowed", "Blocked", or "Unknown"
) {
    val vidPidString: String
        get() = String.format("%04X:%04X", vendorId, productId)
}

@Entity(tableName = "event_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceName: String,
    val vidPid: String,
    val action: String // "Blocked", "Allowed", or "User Prompted"
)
