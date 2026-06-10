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
     * Set the package name associated with a USB device (Host Mode).
     * Passing a null packageName (or a dummy value) revokes default handlers, 
     * effectively blocking access to the device.
     */
    fun setDevicePackage(device: UsbDevice, packageName: String?, userId: Int): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku binder is not available")
            return false
        }

        try {
            val usbBinder: IBinder = SystemServiceHelper.getSystemService("usb")
            val stubClass = Class.forName("android.hardware.usb.IUsbManager\$Stub")
            val asInterfaceMethod: Method = stubClass.getMethod("asInterface", IBinder::class.java)
            val iUsbManager: Any = asInterfaceMethod.invoke(null, usbBinder)
                ?: throw IllegalStateException("Failed to cast binder to IUsbManager")

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

    /**
     * Sets the active USB client functions (gadget mode) via direct Binder calls.
     * Pass 0L to disable MTP/ADB (charge only), or 5L (FUNCTION_MTP = 4 | FUNCTION_ADB = 1) to restore default functions.
     */
    fun setCurrentUsbFunctions(functions: Long): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku binder is not available to set USB functions")
            return false
        }
        try {
            val usbBinder = SystemServiceHelper.getSystemService("usb")
            val stubClass = Class.forName("android.hardware.usb.IUsbManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val iUsbManager = asInterfaceMethod.invoke(null, usbBinder)
                ?: throw IllegalStateException("Failed to cast binder to IUsbManager")

            val iUsbManagerClass = Class.forName("android.hardware.usb.IUsbManager")
            
            try {
                // Try modern setCurrentFunctions(long)
                val method = iUsbManagerClass.getMethod("setCurrentFunctions", Long::class.javaPrimitiveType)
                method.invoke(iUsbManager, functions)
                Log.d(TAG, "Successfully invoked setCurrentFunctions($functions)")
            } catch (e: NoSuchMethodException) {
                // Fallback to older setCurrentFunction(String, boolean)
                val functionStr = if (functions == 0L) "none" else "mtp,adb"
                val method = iUsbManagerClass.getMethod(
                    "setCurrentFunction",
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(iUsbManager, functionStr, false)
                Log.d(TAG, "Successfully invoked fallback setCurrentFunction($functionStr, false)")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting USB client functions via binder", e)
            return false
        }
    }
}
