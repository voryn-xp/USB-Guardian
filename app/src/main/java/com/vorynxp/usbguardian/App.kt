package com.vorynxp.usbguardian

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vorynxp.usbguardian.domain.UsbBlockingService
import dagger.hilt.android.HiltAndroidApp
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

@HiltAndroidApp
class App : Application() {

    companion object {
        private const val TAG = "USBGuardianApp"
    }

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
            HiddenApiBypass.addBridgeToApp()
            Log.d(TAG, "Hidden API restrictions bypassed")
        }

        // Setup Shizuku listeners
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Log.d(TAG, "Shizuku listeners registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    private fun startBlockingService() {
        val intent = Intent(this, UsbBlockingService::class.java).apply {
            action = UsbBlockingService.ACTION_START_SERVICE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start UsbBlockingService from App init", e)
        }
    }
}
