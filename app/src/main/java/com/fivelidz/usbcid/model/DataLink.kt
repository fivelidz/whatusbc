package com.fivelidz.usbcid.model

/**
 * Result of the "live data test": instead of asking the cable what it is, we
 * watch the phone actually use it for data and report what the link achieved.
 *
 * Two independent facts:
 *  1. The phone port's MAX possible USB speed (from its device controller).
 *     A cable can never exceed this on this phone, so it's the hard ceiling.
 *  2. Whether a data link is currently LIVE over the cable (an active USB
 *     function such as MTP/PTP/ADB while attached to a host) — functional proof
 *     the cable carries data at all.
 */
data class DataLinkProbe(
    val portCeiling: DataSpeed = DataSpeed.UNKNOWN,
    val portCeilingKnown: Boolean = false,
    val controllerName: String? = null,    // e.g. "musb-hdrc", "dwc3"
    val linkLive: Boolean = false,          // a data function is active now
    val activeFunctions: String? = null,    // e.g. "mtp,adb"
    val inHostMode: Boolean = false,         // phone is the USB host (OTG)
) {
    /**
     * Functional verdict for the cable's *data* capability based purely on what
     * we observed (not the eMarker).
     */
    val provenCarriesData: Boolean get() = linkLive || inHostMode
}

/**
 * Maps a Linux USB Device Controller (UDC) driver name to the maximum USB
 * generation that controller supports. This is the port's hard ceiling.
 */
object UsbController {
    fun ceilingFor(name: String?): DataSpeed = when {
        name == null -> DataSpeed.UNKNOWN
        // USB 2.0-only device controllers (480 Mbps max)
        name.contains("musb", true) -> DataSpeed.USB2
        name.contains("hsusb", true) -> DataSpeed.USB2
        name.contains("ci_hdrc", true) -> DataSpeed.USB2
        name.contains("fsl", true) -> DataSpeed.USB2
        name.contains("dummy", true) -> DataSpeed.UNKNOWN
        // SuperSpeed-capable controllers (5 Gbps+)
        name.contains("dwc3", true) -> DataSpeed.USB3_GEN1
        name.contains("xhci", true) -> DataSpeed.USB3_GEN1
        name.contains("ssusb", true) -> DataSpeed.USB3_GEN1
        name.contains("cdns", true) -> DataSpeed.USB3_GEN2
        name.contains("dwc_usb3", true) -> DataSpeed.USB3_GEN1
        else -> DataSpeed.UNKNOWN
    }

    fun isKnown(name: String?): Boolean = ceilingFor(name) != DataSpeed.UNKNOWN
}
