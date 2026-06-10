package com.vorynxp.usbguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.vorynxp.usbguardian.domain.UsbBlockingService

class UsbEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbEventReceiver"
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
                
                // Start UsbBlockingService to process this event
                val serviceIntent = Intent(context, UsbBlockingService::class.java).apply {
                    this.action = action
                    putExtra(UsbManager.EXTRA_DEVICE, device)
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start UsbBlockingService from receiver", e)
                }
            } else {
                Log.w(TAG, "USB Device was null in intent extras")
            }
        }
    }
}
