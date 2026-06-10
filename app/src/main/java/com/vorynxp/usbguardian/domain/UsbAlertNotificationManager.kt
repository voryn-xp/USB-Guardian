package com.vorynxp.usbguardian.domain

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.core.app.NotificationCompat
import com.vorynxp.usbguardian.MainActivity

object UsbAlertNotificationManager {
    private const val CHANNEL_ID = "usb_guardian_channel"
    private const val NOTIFICATION_ID = 2002

    fun showBlockedAlert(context: Context, device: UsbDevice) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deviceName = device.productName ?: device.deviceName ?: "USB Device"
        val vidPidStr = String.format("%04X:%04X", device.vendorId, device.productId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("USB Connection Blocked")
            .setContentText("Blocked unauthorized device: $deviceName ($vidPidStr)")
            .setSmallIcon(com.vorynxp.usbguardian.R.drawable.app_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showPcBlockedAlert(context: Context, blockedSuccessfully: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title: String
        val text: String
        if (blockedSuccessfully) {
            title = "PC Connection Blocked"
            text = "Blocked USB debugging/file transfer access."
        } else {
            title = "PC Connection Detected"
            text = "Block failed. Grant Shizuku permission to secure connection."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.vorynxp.usbguardian.R.drawable.app_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
