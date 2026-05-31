package com.fivelidz.usbcid.detect

import android.content.Context
import android.hardware.usb.UsbManager
import com.fivelidz.usbcid.model.DataLinkProbe
import com.fivelidz.usbcid.model.UsbController

/**
 * The "live data test" reader. Works with NO special permissions:
 *
 *  - Reads `sys.usb.controller` (the USB device-controller driver name) via
 *    android.os.SystemProperties (a hidden but app-callable API for
 *    non-protected props). This gives the port's hard speed ceiling.
 *  - Reads `sys.usb.state` / `sys.usb.config` to see if a data function
 *    (mtp / ptp / adb / ncm / rndis) is active right now — functional proof the
 *    cable is carrying data while plugged into a host.
 *  - Uses UsbManager to detect host (OTG) mode and any enumerated peripheral.
 *
 * Insight: a USB-2.0-only cable physically cannot train a SuperSpeed link, so
 * if we ever observe a SuperSpeed link the cable MUST be a SuperSpeed cable.
 * Conversely, the phone's controller ceiling caps what any cable can achieve
 * here, which is itself useful to tell the user.
 */
class DataLinkReader(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    fun probe(): DataLinkProbe {
        val controller = getProp("sys.usb.controller").ifBlank { getProp("ro.usb.controller") }
        val state = getProp("sys.usb.state").ifBlank { getProp("sys.usb.config") }

        val ceiling = UsbController.ceilingFor(controller)
        val ceilingKnown = UsbController.isKnown(controller)

        // Active data functions = a live data link to a host.
        val dataFns = listOf("mtp", "ptp", "adb", "ncm", "rndis", "uvc")
        val linkLive = dataFns.any { state.contains(it, ignoreCase = true) }

        // Host/OTG mode: phone acts as host and may have peripherals attached.
        val hostMode = (usbManager?.deviceList?.isNotEmpty() == true)

        return DataLinkProbe(
            portCeiling = ceiling,
            portCeilingKnown = ceilingKnown,
            controllerName = controller.ifBlank { null },
            linkLive = linkLive,
            activeFunctions = state.ifBlank { null },
            inHostMode = hostMode,
        )
    }

    /** Read a system property via the hidden SystemProperties.get(String). */
    private fun getProp(key: String): String = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val m = cls.getMethod("get", String::class.java)
        (m.invoke(null, key) as? String).orEmpty()
    }.getOrDefault("")
}
