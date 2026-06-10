package com.vorynxp.usbguardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import com.vorynxp.usbguardian.domain.UsbGuardianForegroundService
import com.vorynxp.usbguardian.shizuku.ShizukuUsbManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    companion object {
        private const val TAG = "USBGuardianApp"
    }

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var shizukuUsbManager: ShizukuUsbManager

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received!")
        startBlockingService()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died!")
    }

    override fun onCreate() {
        super.onCreate()

        // Bypass hidden API restrictions on Android P+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
            Log.d(TAG, "Hidden API restrictions bypassed")
        }

        // Initialize Shizuku binder lifecycle helper
        shizukuUsbManager.init()

        // Create notification channels
        createNotificationChannels()

        // Setup Shizuku listeners for foreground service auto-starting
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Log.d(TAG, "Shizuku listeners registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "usb_guardian_channel",
                "USB Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for USB device connections"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun startBlockingService() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = userPreferences.masterToggleFlow.first()
                if (enabled) {
                    val intent = Intent(this@App, UsbGuardianForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    Log.d(TAG, "Protection disabled. Not starting UsbGuardianForegroundService on binder received.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start UsbGuardianForegroundService from App init", e)
            }
        }
    }
}
