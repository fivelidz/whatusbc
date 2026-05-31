package com.fivelidz.usbcid.model

/**
 * The five capability axes a user actually cares about when holding a USB-C
 * cable or port: "can it do data, how fast, video, audio, and how much power?"
 *
 * Each axis resolves to a [Verdict] with an explanation and a confidence, so the
 * app can give a useful answer even when it can only infer rather than measure.
 */

enum class Verdict(val label: String) {
    YES("Yes"),
    LIKELY("Likely"),
    MAYBE("Maybe"),
    UNLIKELY("Unlikely"),
    NO("No"),
    UNKNOWN("Unknown");
}

/** One capability axis. */
data class CapabilityAxis(
    val name: String,
    val verdict: Verdict,
    val detail: String,          // e.g. "Up to 10 Gbps" or "60 W (3 A @ 20 V)"
    val explanation: String,     // why we think this
    val confidence: Confidence,
)

/** The full capability verdict across all five axes. */
data class CapabilityVerdict(
    val data: CapabilityAxis,
    val speed: CapabilityAxis,
    val video: CapabilityAxis,
    val audio: CapabilityAxis,
    val charging: CapabilityAxis,
) {
    val axes get() = listOf(data, speed, video, audio, charging)
}

/**
 * The 24-pin USB-C pinout grouped into the functional groups that determine
 * capability. Used both for the interactive graphic and for inference.
 */
enum class PinGroup(
    val display: String,
    val pins: String,
    val purpose: String,
    val tells: String,
) {
    POWER(
        "Power", "VBUS + GND",
        "Carries the charging voltage and ground return.",
        "Always present. Thicker conductors → higher current (5 A) cables."
    ),
    CC(
        "CC / PD", "CC1, CC2",
        "Configuration Channel: orientation, role, and USB Power Delivery messaging.",
        "Required for any fast charging above 5 V. Also where an eMarker lives."
    ),
    USB2(
        "USB 2.0 data", "D+ , D-",
        "The legacy 480 Mbps data pair.",
        "If present but no SuperSpeed pins → it's a USB 2.0 cable."
    ),
    SUPERSPEED(
        "SuperSpeed", "TX1/RX1, TX2/RX2",
        "High-speed differential pairs for USB 3.x / USB4 / Thunderbolt.",
        "Wired → 5 Gbps or faster, and DisplayPort video becomes possible."
    ),
    SBU(
        "Sideband (SBU)", "SBU1, SBU2",
        "Auxiliary channel used by DisplayPort/Thunderbolt Alt Mode.",
        "Wired → video/audio Alt Mode support is likely."
    );
}
