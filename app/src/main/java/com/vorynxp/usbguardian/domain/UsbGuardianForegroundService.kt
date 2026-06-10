package com.vorynxp.usbguardian.domain

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vorynxp.usbguardian.R
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import com.vorynxp.usbguardian.shizuku.ShizukuUsbManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UsbGuardianForegroundService : Service() {

    companion object {
        private const val TAG = "UsbGuardianService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "usb_guardian_channel"
    }

    @Inject
    lateinit var shizukuUsbManager: ShizukuUsbManager

    @Inject
    lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isPcBlockedLogged = false

    private val usbStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.hardware.usb.action.USB_STATE") {
                val connected = intent.getBooleanExtra("connected", false)
                val configured = intent.getBooleanExtra("configured", false)
                Log.d(TAG, "USB_STATE change: connected=$connected, configured=$configured")
                
                if (connected) {
                    handlePcConnectionState()
                } else {
                    isPcBlockedLogged = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Register dynamic USB_STATE receiver
        try {
            registerReceiver(usbStateReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register usbStateReceiver", e)
        }

        // Listen for protection toggle changes to restore default state if disabled
        serviceScope.launch {
            userPreferences.masterToggleFlow.collect { active ->
                if (!active) {
                    // Restore default functions (MTP + ADB)
                    shizukuUsbManager.setCurrentUsbFunctions(5L)
                    isPcBlockedLogged = false
                } else {
                    // If already plugged in when active, block PC connection
                    val stickyIntent = registerReceiver(null, IntentFilter("android.hardware.usb.action.USB_STATE"))
                    val connected = stickyIntent?.getBooleanExtra("connected", false) ?: false
                    if (connected) {
                        blockPcConnection()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Guardian Active")
            .setContentText("Monitoring USB connections")
            .setSmallIcon(R.drawable.app_icon)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister usbStateReceiver", e)
        }
    }

    private fun handlePcConnectionState() {
        serviceScope.launch {
            val masterEnabled = userPreferences.masterToggleFlow.first()
            if (masterEnabled) {
                blockPcConnection()
            }
        }
    }

    private fun blockPcConnection() {
        val success = shizukuUsbManager.setCurrentUsbFunctions(0L) // Disable MTP + ADB
        if (success && !isPcBlockedLogged) {
            isPcBlockedLogged = true
            Log.d(TAG, "Blocked PC connection successfully")
        }
    }
}
