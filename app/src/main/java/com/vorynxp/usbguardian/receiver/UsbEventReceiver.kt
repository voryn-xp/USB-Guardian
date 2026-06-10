package com.vorynxp.usbguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import com.vorynxp.usbguardian.domain.UsbBlockingService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UsbEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbEventReceiver"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun userPreferences(): UserPreferences
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action || UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (device != null) {
                Log.d(TAG, "USB Device event: $action for ${device.deviceName} (VID: ${device.vendorId}, PID: ${device.productId})")
                
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            ReceiverEntryPoint::class.java
                        )
                        val userPreferences = entryPoint.userPreferences()
                        val masterEnabled = userPreferences.masterToggleFlow.first()
                        
                        if (masterEnabled) {
                            val serviceIntent = Intent(context, UsbBlockingService::class.java).apply {
                                this.action = action
                                putExtra(UsbManager.EXTRA_DEVICE, device)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            Log.d(TAG, "Protection disabled. Ignoring device attached/detached event.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking master toggle in receiver", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                Log.w(TAG, "USB Device was null in intent extras")
            }
        }
    }
}
