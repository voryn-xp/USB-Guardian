package com.vorynxp.usbguardian.domain

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vorynxp.usbguardian.R
import com.vorynxp.usbguardian.data.db.LogDao
import com.vorynxp.usbguardian.data.db.LogEntity
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import com.vorynxp.usbguardian.shizuku.ShizukuUsbManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
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

    @Inject
    lateinit var logDao: LogDao

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

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateNotification()
        checkAndBlockPc()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateNotification()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateNotification()
        checkAndBlockPc()
    }

    override fun onCreate() {
        super.onCreate()
        // Register dynamic USB_STATE receiver
        try {
            registerReceiver(usbStateReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register usbStateReceiver", e)
        }

        // Register Shizuku binder and permission listeners to dynamically update notifications
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }

        // Listen for protection toggle changes to restore default state if disabled
        serviceScope.launch {
            userPreferences.masterToggleFlow.collect { active ->
                if (!active) {
                    // Restore default functions (MTP + ADB)
                    shizukuUsbManager.setCurrentUsbFunctions(5L)
                    isPcBlockedLogged = false
                } else {
                    checkAndBlockPc()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statusText = getShizukuStatusText()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Guardian")
            .setContentText(statusText)
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
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun getShizukuStatusText(): String {
        return when {
            !Shizuku.pingBinder() -> "Waiting for Shizuku service..."
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Monitoring USB connections"
            else -> "Grant Shizuku permission to enable protection"
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val statusText = getShizukuStatusText()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Guardian")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.app_icon)
            .setOngoing(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun checkAndBlockPc() {
        val stickyIntent = registerReceiver(null, IntentFilter("android.hardware.usb.action.USB_STATE"))
        val connected = stickyIntent?.getBooleanExtra("connected", false) ?: false
        if (connected) {
            handlePcConnectionState()
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
        serviceScope.launch {
            val success = shizukuUsbManager.setCurrentUsbFunctions(0L) // Disable MTP + ADB
            if (success) {
                if (!isPcBlockedLogged) {
                    isPcBlockedLogged = true
                    logDao.insertLog(
                        LogEntity(
                            timestamp = System.currentTimeMillis(),
                            deviceName = "Computer (PC)",
                            vidPid = "0000:0000",
                            action = "Blocked"
                        )
                    )
                    withContext(Dispatchers.Main) {
                        UsbAlertNotificationManager.showPcBlockedAlert(this@UsbGuardianForegroundService, true)
                    }
                }
            } else {
                // If it fails, log once but allow retry on permission change
                if (!isPcBlockedLogged) {
                    logDao.insertLog(
                        LogEntity(
                            timestamp = System.currentTimeMillis(),
                            deviceName = "Computer (PC)",
                            vidPid = "0000:0000",
                            action = "Block Failed"
                        )
                    )
                    withContext(Dispatchers.Main) {
                        UsbAlertNotificationManager.showPcBlockedAlert(this@UsbGuardianForegroundService, false)
                    }
                }
            }
        }
    }
}
