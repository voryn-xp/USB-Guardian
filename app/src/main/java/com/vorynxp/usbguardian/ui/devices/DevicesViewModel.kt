package com.vorynxp.usbguardian.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorynxp.usbguardian.data.db.UsbDeviceDao
import com.vorynxp.usbguardian.data.db.UsbDeviceEntity
import com.vorynxp.usbguardian.data.prefs.DeviceRule
import com.vorynxp.usbguardian.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val usbDeviceDao: UsbDeviceDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val devicesState: StateFlow<List<UsbDeviceEntity>> = usbDeviceDao.getAllDevicesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateDeviceRule(device: UsbDeviceEntity, rule: DeviceRule) {
        viewModelScope.launch {
            // Update rule in DataStore
            userPreferences.setDeviceRule(device.vendorId, device.productId, rule)
            // Update status in Room Database
            usbDeviceDao.insertOrUpdate(device.copy(status = rule.name))
        }
    }
}
