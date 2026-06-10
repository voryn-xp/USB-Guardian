package com.vorynxp.usbguardian.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vorynxp.usbguardian.MainActivity
import com.vorynxp.usbguardian.data.db.LogDao
import com.vorynxp.usbguardian.data.db.LogEntity
import com.vorynxp.usbguardian.data.db.UsbDeviceDao
import com.vorynxp.usbguardian.data.db.UsbDeviceEntity
import com.vorynxp.usbguardian.data.prefs.DeviceRule
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
class UsbBlockingService : Service() {

    companion object {
        private const val TAG = "UsbBlockingService"
        
        // Notification Channels
        private const val SERVICE_CHANNEL_ID = "usb_guardian_service_channel"
        private const val EVENTS_CHANNEL_ID = "usb_guardian_events_channel"

        // Notification IDs
        private const val PERSISTENT_NOTIF_ID = 1001
        private const val ALERT_NOTIF_ID_BASE = 2000

        // Custom Actions
        const val ACTION_START_SERVICE = "com.vorynxp.usbguardian.action.START"
        const val ACTION_STOP_SERVICE = "com.vorynxp.usbguardian.action.STOP"
        const val ACTION_ALLOW_DEVICE = "com.vorynxp.usbguardian.action.ALLOW"
        const val ACTION_BLOCK_DEVICE = "com.vorynxp.usbguardian.action.BLOCK"
        
        // Extras
        const val EXTRA_VID = "extra_device_vid"
        const val EXTRA_PID = "extra_device_pid"
        const val EXTRA_NAME = "extra_device_name"
    }

    @Inject
    lateinit var shizukuUsbManager: ShizukuUsbManager

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var usbDeviceDao: UsbDeviceDao

    @Inject
    lateinit var logDao: LogDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    private var isPcBlockedLogged = false
    private val usbStateReceiver = object : android.content.BroadcastReceiver() {
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
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        startForeground(PERSISTENT_NOTIF_ID, createPersistentNotification())

        // Register dynamic USB_STATE receiver
        try {
            registerReceiver(usbStateReceiver, android.content.IntentFilter("android.hardware.usb.action.USB_STATE"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register usbStateReceiver", e)
        }

        // Listen for protection toggle changes
        serviceScope.launch {
            userPreferences.masterToggleFlow.collect { active ->
                Log.d(TAG, "Master toggle state collected in service: active=$active")
                if (!active) {
                    shizukuUsbManager.setUserUsbFunctions("mtp,adb")
                    // Restore adb settings globally
                    shizukuUsbManager.setAdbEnabledSetting(true)
                    isPcBlockedLogged = false
                } else {
                    // Check if USB is already plugged in when toggling ON
                    val stickyIntent = registerReceiver(null, android.content.IntentFilter("android.hardware.usb.action.USB_STATE"))
                    val connected = stickyIntent?.getBooleanExtra("connected", false) ?: false
                    if (connected) {
                        blockPcConnection()
                    }
                }
            }
        }

        Log.d(TAG, "UsbBlockingService Created and started in Foreground")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand Action: $action")

        when (action) {
            ACTION_START_SERVICE -> {
                // Already started in onCreate, just refresh state
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stopping UsbBlockingService")
                stopSelf()
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = getUsbDeviceFromIntent(intent)
                if (device != null) {
                    handleDeviceAttached(device)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = getUsbDeviceFromIntent(intent)
                if (device != null) {
                    handleDeviceDetached(device)
                }
            }
            ACTION_ALLOW_DEVICE -> {
                val vid = intent.getIntExtra(EXTRA_VID, -1)
                val pid = intent.getIntExtra(EXTRA_PID, -1)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Unknown Device"
                if (vid != -1 && pid != -1) {
                    serviceScope.launch {
                        allowDeviceRule(vid, pid, name)
                    }
                }
                cancelAlertNotification(vid, pid)
            }
            ACTION_BLOCK_DEVICE -> {
                val vid = intent.getIntExtra(EXTRA_VID, -1)
                val pid = intent.getIntExtra(EXTRA_PID, -1)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Unknown Device"
                if (vid != -1 && pid != -1) {
                    serviceScope.launch {
                        blockDeviceRule(vid, pid, name)
                    }
                }
                cancelAlertNotification(vid, pid)
            }
        }

        return START_STICKY
    }

    private fun getUsbDeviceFromIntent(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        serviceScope.launch {
            val masterEnabled = userPreferences.masterToggleFlow.first()
            val deviceName = device.productName ?: device.deviceName ?: "USB Device"
            val vid = device.vendorId
            val pid = device.productId
            val vidPidStr = String.format("%04X:%04X", vid, pid)
            val currentRule = userPreferences.getDeviceRuleFlow(vid, pid).first()

            Log.d(TAG, "Device attached: $deviceName ($vidPidStr). Rule: $currentRule. MasterToggle: $masterEnabled")

            // Register device in Room DB
            val entity = UsbDeviceEntity(
                vendorId = vid,
                productId = pid,
                name = deviceName,
                lastSeen = System.currentTimeMillis(),
                status = currentRule.name
            )
            usbDeviceDao.insertOrUpdate(entity)

            if (!masterEnabled) {
                // Protection is disabled, pass through normally
                logDao.insertLog(LogEntity(
                    timestamp = System.currentTimeMillis(),
                    deviceName = deviceName,
                    vidPid = vidPidStr,
                    action = "Allowed (Protection Disabled)"
                ))
                return@launch
            }

            when (currentRule) {
                DeviceRule.ALLOWED -> {
                    // Allowed device, let it pass through
                    logDao.insertLog(LogEntity(
                        timestamp = System.currentTimeMillis(),
                        deviceName = deviceName,
                        vidPid = vidPidStr,
                        action = "Allowed"
                    ))
                }
                DeviceRule.BLOCKED -> {
                    // Block the device
                    blockDeviceAccess(device, deviceName, vidPidStr)
                }
                DeviceRule.UNKNOWN -> {
                    // By default block immediately to be safe
                    blockDeviceAccess(device, deviceName, vidPidStr)
                    
                    // Update entity state to Blocked temporarily or keep Unknown
                    usbDeviceDao.insertOrUpdate(entity.copy(status = DeviceRule.BLOCKED.name))

                    // Show user warning / decision notification with action buttons
                    showDecisionNotification(device, deviceName, vid, pid)

                    logDao.insertLog(LogEntity(
                        timestamp = System.currentTimeMillis(),
                        deviceName = deviceName,
                        vidPid = vidPidStr,
                        action = "Blocked (Unknown Device)"
                    ))
                }
            }
        }
    }

    private fun handleDeviceDetached(device: UsbDevice) {
        val deviceName = device.productName ?: device.deviceName ?: "USB Device"
        val vid = device.vendorId
        val pid = device.productId
        val vidPidStr = String.format("%04X:%04X", vid, pid)

        Log.d(TAG, "Device detached: $deviceName ($vidPidStr)")
        cancelAlertNotification(vid, pid)

        serviceScope.launch {
            logDao.insertLog(LogEntity(
                timestamp = System.currentTimeMillis(),
                deviceName = deviceName,
                vidPid = vidPidStr,
                action = "Detached"
            ))
        }
    }

    private fun blockDeviceAccess(device: UsbDevice, deviceName: String, vidPidStr: String) {
        val userId = getMyUserId()
        val success = shizukuUsbManager.setDevicePackage(device, null, userId)
        
        serviceScope.launch {
            if (success) {
                logDao.insertLog(LogEntity(
                    timestamp = System.currentTimeMillis(),
                    deviceName = deviceName,
                    vidPid = vidPidStr,
                    action = "Blocked"
                ))
                showBlockedNotification(deviceName)
            } else {
                logDao.insertLog(LogEntity(
                    timestamp = System.currentTimeMillis(),
                    deviceName = deviceName,
                    vidPid = vidPidStr,
                    action = "Block Failed (No Permission)"
                ))
            }
        }
    }

    private suspend fun allowDeviceRule(vid: Int, pid: Int, name: String) {
        userPreferences.setDeviceRule(vid, pid, DeviceRule.ALLOWED)
        
        // Update DB
        val device = usbDeviceDao.getDevice(vid, pid)
        if (device != null) {
            usbDeviceDao.insertOrUpdate(device.copy(status = DeviceRule.ALLOWED.name))
        }

        logDao.insertLog(LogEntity(
            timestamp = System.currentTimeMillis(),
            deviceName = name,
            vidPid = String.format("%04X:%04X", vid, pid),
            action = "User Allowed (Rule Saved)"
        ))
        
        // Note: The device is currently blocked. The user should replug it, 
        // or since we allowed it, it will pass through on the next insertion.
    }

    private suspend fun blockDeviceRule(vid: Int, pid: Int, name: String) {
        userPreferences.setDeviceRule(vid, pid, DeviceRule.BLOCKED)
        
        // Update DB
        val device = usbDeviceDao.getDevice(vid, pid)
        if (device != null) {
            usbDeviceDao.insertOrUpdate(device.copy(status = DeviceRule.BLOCKED.name))
        }

        logDao.insertLog(LogEntity(
            timestamp = System.currentTimeMillis(),
            deviceName = name,
            vidPid = String.format("%04X:%04X", vid, pid),
            action = "User Blocked (Rule Saved)"
        ))
    }

    private fun getMyUserId(): Int {
        return try {
            val myUserIdMethod = android.os.UserHandle::class.java.getDeclaredMethod("myUserId")
            myUserIdMethod.isAccessible = true
            myUserIdMethod.invoke(null) as Int
        } catch (e: Exception) {
            Log.w(TAG, "Could not call myUserId reflectively, falling back to 0", e)
            0
        }
    }

    // --- Notifications Helper ---

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "USB Guardian Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active protection state of USB Guardian"
            }

            val eventsChannel = NotificationChannel(
                EVENTS_CHANNEL_ID,
                "USB Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts about blocked or unauthorized USB devices"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(eventsChannel)
        }
    }

    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("USB Guardian Active")
            .setContentText("Monitoring USB ports for unauthorized connections.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun showBlockedNotification(deviceName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, EVENTS_CHANNEL_ID)
            .setContentTitle("USB Connection Blocked")
            .setContentText("Blocked unauthorized device: $deviceName")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIF_ID_BASE - 1, notification)
    }

    private fun showDecisionNotification(device: UsbDevice, deviceName: String, vid: Int, pid: Int) {
        // Pending Intent to Allow
        val allowIntent = Intent(this, UsbBlockingService::class.java).apply {
            action = ACTION_ALLOW_DEVICE
            putExtra(EXTRA_VID, vid)
            putExtra(EXTRA_PID, pid)
            putExtra(EXTRA_NAME, deviceName)
        }
        val allowPendingIntent = PendingIntent.getService(
            this, vid + pid + 100, allowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Pending Intent to Block
        val blockIntent = Intent(this, UsbBlockingService::class.java).apply {
            action = ACTION_BLOCK_DEVICE
            putExtra(EXTRA_VID, vid)
            putExtra(EXTRA_PID, pid)
            putExtra(EXTRA_NAME, deviceName)
        }
        val blockPendingIntent = PendingIntent.getService(
            this, vid + pid + 200, blockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val vidPidStr = String.format("%04X:%04X", vid, pid)

        val notification = NotificationCompat.Builder(this, EVENTS_CHANNEL_ID)
            .setContentTitle("New USB Device: $deviceName")
            .setContentText("VID:PID is $vidPidStr. Tap to decide.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(android.R.drawable.checkbox_on_background, "Allow", allowPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Block", blockPendingIntent)
            .setOngoing(true)
            .build()

        val notifId = getNotificationId(vid, pid)
        notificationManager.notify(notifId, notification)
    }

    private fun cancelAlertNotification(vid: Int, pid: Int) {
        val notifId = getNotificationId(vid, pid)
        notificationManager.cancel(notifId)
    }

    private fun getNotificationId(vid: Int, pid: Int): Int {
        return ALERT_NOTIF_ID_BASE + (vid xor pid)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister usbStateReceiver", e)
        }
        serviceScope.launch {
            // Log that service stopped
            logDao.insertLog(LogEntity(
                timestamp = System.currentTimeMillis(),
                deviceName = "System",
                vidPid = "0000:0000",
                action = "Protection Service Stopped"
            ))
        }
        Log.d(TAG, "UsbBlockingService Destroyed")
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
        val success = shizukuUsbManager.setUserUsbFunctions("none")
        if (success && !isPcBlockedLogged) {
            isPcBlockedLogged = true
            // Disable adb settings globally for double protection
            shizukuUsbManager.setAdbEnabledSetting(false)
            serviceScope.launch {
                logDao.insertLog(LogEntity(
                    timestamp = System.currentTimeMillis(),
                    deviceName = "Computer (PC)",
                    vidPid = "0000:0000",
                    action = "Blocked PC Access (ADB/MTP Disabled)"
                ))
            }
            showBlockedNotification("Computer (PC)")
        }
    }
}
