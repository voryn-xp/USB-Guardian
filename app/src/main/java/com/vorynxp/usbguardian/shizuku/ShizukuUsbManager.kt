package com.vorynxp.usbguardian.shizuku

import android.hardware.usb.UsbDevice
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuUsbManager @Inject constructor() {

    companion object {
        private const val TAG = "ShizukuUsbManager"
    }

    /**
     * Set the package name associated with a USB device.
     * Passing a null packageName (or a dummy value) revokes default handlers, 
     * effectively blocking access to the device.
     */
    fun setDevicePackage(device: UsbDevice, packageName: String?, userId: Int): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku binder is not available")
            return false
        }

        try {
            // Get the privileged USB service binder via Shizuku
            val usbBinder: IBinder = SystemServiceHelper.getSystemService("usb")
            
            // Reflectively find the stub and call asInterface
            val stubClass = Class.forName("android.hardware.usb.IUsbManager\$Stub")
            val asInterfaceMethod: Method = stubClass.getMethod("asInterface", IBinder::class.java)
            val iUsbManager: Any = asInterfaceMethod.invoke(null, usbBinder)
                ?: throw IllegalStateException("Failed to cast binder to IUsbManager")

            // Reflectively invoke setDevicePackage(UsbDevice, String, int)
            val iUsbManagerClass = Class.forName("android.hardware.usb.IUsbManager")
            val setDevicePackageMethod: Method = iUsbManagerClass.getMethod(
                "setDevicePackage",
                UsbDevice::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )

            setDevicePackageMethod.invoke(iUsbManager, device, packageName, userId)
            Log.d(TAG, "Successfully setDevicePackage for ${device.deviceName} (VID:PID = ${device.vendorId}:${device.productId}) to '$packageName'")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking setDevicePackage via Shizuku", e)
            return false
        }
    }
}
