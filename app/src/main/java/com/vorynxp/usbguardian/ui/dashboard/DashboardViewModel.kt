package com.vorynxp.usbguardian.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorynxp.usbguardian.data.db.LogDao
import com.vorynxp.usbguardian.data.db.UsbDeviceDao
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import com.vorynxp.usbguardian.domain.UsbBlockingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Calendar
import javax.inject.Inject

enum class ShizukuState {
    CONNECTED,
    DISCONNECTED,
    PERMISSION_DENIED
}

data class DashboardUiState(
    val isProtectionActive: Boolean = true,
    val shizukuState: ShizukuState = ShizukuState.DISCONNECTED,
    val blockedTodayCount: Int = 0,
    val allowedCount: Int = 0,
    val totalDevicesSeen: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val usbDeviceDao: UsbDeviceDao,
    private val logDao: LogDao
) : ViewModel() {

    private val _shizukuState = MutableStateFlow(ShizukuState.DISCONNECTED)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuState.value = ShizukuState.DISCONNECTED
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        checkShizukuPermission()
    }

    init {
        checkShizukuPermission()
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Exception) {
            // Safe fallback if binder not bound yet
        }
    }

    fun checkShizukuPermission() {
        _shizukuState.value = when {
            !Shizuku.pingBinder() -> ShizukuState.DISCONNECTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.CONNECTED
            else -> ShizukuState.PERMISSION_DENIED
        }
    }

    fun toggleProtection(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setMasterToggle(enabled)
            val intent = Intent(context, UsbBlockingService::class.java).apply {
                action = if (enabled) UsbBlockingService.ACTION_START_SERVICE else UsbBlockingService.ACTION_STOP_SERVICE
            }
            try {
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private val startOfDay: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    val uiState: StateFlow<DashboardUiState> = combine(
        userPreferences.masterToggleFlow,
        _shizukuState,
        logDao.getBlockedCountSinceFlow(startOfDay),
        usbDeviceDao.getAllowedCountFlow(),
        usbDeviceDao.getDevicesCountFlow()
    ) { active, shizuku, blocked, allowed, total ->
        DashboardUiState(
            isProtectionActive = active && shizuku == ShizukuState.CONNECTED,
            shizukuState = shizuku,
            blockedTodayCount = blocked,
            allowedCount = allowed,
            totalDevicesSeen = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
