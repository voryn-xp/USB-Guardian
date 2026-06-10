package com.vorynxp.usbguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.vorynxp.usbguardian.data.db.AppDatabase
import com.vorynxp.usbguardian.data.db.LogEntity
import com.vorynxp.usbguardian.data.db.UsbDeviceEntity
import com.vorynxp.usbguardian.domain.UsbAlertNotificationManager
import com.vorynxp.usbguardian.shizuku.ShizukuUsbManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbEventReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun shizukuUsbManager(): ShizukuUsbManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        } ?: return

        Log.d("UsbEventReceiver", "USB event: $action — ${device.deviceName} VID:${device.vendorId} PID:${device.productId}")

        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val db = AppDatabase.getInstance(context)
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReceiverEntryPoint::class.java
                )
                val shizukuUsbManager = entryPoint.shizukuUsbManager()

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val deviceName = device.productName ?: device.deviceName ?: "Unknown Device"
                        val vidPidStr = String.format("%04X:%04X", device.vendorId, device.productId)

                        // 1. Check existing device / rule in DB
                        val existingDevice = db.usbDeviceDao().getDevice(device.vendorId, device.productId)
                        val rule = existingDevice?.status ?: "Unknown"

                        // Update lastSeen and details
                        db.usbDeviceDao().insertOrUpdate(
                            UsbDeviceEntity(
                                vendorId = device.vendorId,
                                productId = device.productId,
                                name = deviceName,
                                lastSeen = System.currentTimeMillis(),
                                status = rule
                            )
                        )

                        if (rule == "Allowed") {
                            db.logDao().insertLog(
                                LogEntity(
                                    timestamp = System.currentTimeMillis(),
                                    deviceName = deviceName,
                                    vidPid = vidPidStr,
                                    action = "Allowed"
                                )
                            )
                            Log.d("UsbEventReceiver", "Device is in allow list, passing through")
                            return@launch
                        }

                        // 3. Block via Shizuku
                        val userId = try {
                            val myUserIdMethod = android.os.UserHandle::class.java.getDeclaredMethod("myUserId")
                            myUserIdMethod.isAccessible = true
                            myUserIdMethod.invoke(null) as Int
                        } catch (e: Exception) {
                            0
                        }
                        shizukuUsbManager.setDevicePackage(device, null, userId)

                        // 4. Log the event
                        db.logDao().insertLog(
                            LogEntity(
                                timestamp = System.currentTimeMillis(),
                                deviceName = deviceName,
                                vidPid = vidPidStr,
                                action = "Blocked"
                            )
                        )

                        // 5. Fire alert notification
                        withContext(Dispatchers.Main) {
                            UsbAlertNotificationManager.showBlockedAlert(context, device)
                        }
                    } catch (e: Exception) {
                        Log.e("UsbEventReceiver", "Error processing USB device attachment", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val db = AppDatabase.getInstance(context)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val deviceName = device.productName ?: device.deviceName ?: "Unknown Device"
                        val vidPidStr = String.format("%04X:%04X", device.vendorId, device.productId)
                        db.logDao().insertLog(
                            LogEntity(
                                timestamp = System.currentTimeMillis(),
                                deviceName = deviceName,
                                vidPid = vidPidStr,
                                action = "Detached"
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("UsbEventReceiver", "Error processing USB device detachment", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
                Log.d("UsbEventReceiver", "Device detached: ${device.deviceName}")
            }
        }
    }
}
