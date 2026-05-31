package com.fivelidz.usbcid.model

/**
 * The guided-identification wizard. A phone can't electrically probe a cable's
 * pins, but a human can read the markings and report what they observe. These
 * answers feed [com.fivelidz.usbcid.detect.CapabilityResolver] to produce a
 * confident verdict for the five capability axes.
 */

enum class TriState { UNKNOWN, YES, NO }

/** What the user can observe about the connector / cable. */
data class WizardAnswers(
    /** Marking printed near the connector. */
    val marking: CableMarking = CableMarking.UNKNOWN,
    /** A logo/label indicating Thunderbolt (lightning bolt) or USB4. */
    val brandLogo: BrandLogo = BrandLogo.NONE,
    /** Did video output work when tried? */
    val videoWorks: TriState = TriState.UNKNOWN,
    /** Plug width / thickness hint for charge-only vs full cables. */
    val whatCameWith: CameWith = CameWith.UNKNOWN,
) {
    val answered: Boolean
        get() = marking != CableMarking.UNKNOWN ||
            brandLogo != BrandLogo.NONE ||
            videoWorks != TriState.UNKNOWN ||
            whatCameWith != CameWith.UNKNOWN
}

/** The "SS", "10", "20", "40" style speed markings stamped on good cables. */
enum class CableMarking(val label: String, val hint: String) {
    UNKNOWN("Not sure / no marking", ""),
    NONE("No SS logo or number", "Plain — usually USB 2.0"),
    SS("\"SS\" or trident logo", "SuperSpeed → at least 5 Gbps"),
    SS10("\"10\" or \"SS10\"", "10 Gbps (USB 3.2 Gen 2)"),
    SS20("\"20\"", "20 Gbps (USB 3.2 Gen 2x2)"),
    N40("\"40\"", "40 Gbps (USB4)"),
    N80("\"80\"", "80 Gbps (USB4 v2)"),
}

enum class BrandLogo(val label: String) {
    NONE("None / unknown"),
    THUNDERBOLT("Thunderbolt ⚡ (bolt + number)"),
    USB4("USB4 logo"),
    USB_IF_CERT("USB-IF certified logo (watts/Gbps badge)"),
}

enum class CameWith(val label: String) {
    UNKNOWN("Not sure"),
    PHONE_CHARGER("Phone / charger box"),
    LAPTOP("Laptop / dock"),
    EXTERNAL_SSD("External SSD / drive"),
    MONITOR("Monitor / display"),
    BOUGHT_DATA("Bought as a data/video cable"),
}
