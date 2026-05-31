package com.fivelidz.usbcid.model

/**
 * Domain model for a USB-C connection / cable assessment.
 *
 * Derived from USB Type-C 2.2, USB PD 3.1, USB4 v2 and Thunderbolt specs.
 * See docs/RESEARCH.md for the full technical background and the hard limits
 * on what an Android phone can actually observe.
 */

/** USB data-rate capability classes. */
enum class DataSpeed(val label: String, val bps: Long, val short: String) {
    UNKNOWN("Unknown", 0, "?"),
    USB2("USB 2.0", 480_000_000, "480 Mbps"),
    USB3_GEN1("USB 3.2 Gen 1", 5_000_000_000, "5 Gbps"),
    USB3_GEN2("USB 3.2 Gen 2", 10_000_000_000, "10 Gbps"),
    USB3_GEN2X2("USB 3.2 Gen 2x2", 20_000_000_000, "20 Gbps"),
    USB4_GEN2("USB4 Gen 2", 20_000_000_000, "20 Gbps"),
    USB4_GEN3("USB4 Gen 3", 40_000_000_000, "40 Gbps"),
    USB4_V2("USB4 v2", 80_000_000_000, "80 Gbps"),
    TB3("Thunderbolt 3", 40_000_000_000, "40 Gbps"),
    TB4("Thunderbolt 4", 40_000_000_000, "40 Gbps"),
    TB5("Thunderbolt 5", 120_000_000_000, "120 Gbps");
}

/** Charging current capability of the cable conductors. */
enum class CurrentRating(val label: String, val amps: Double, val maxWattsAt20V: Int) {
    UNKNOWN("Unknown", 0.0, 0),
    A0_9("USB default 0.9 A", 0.9, 18),
    A1_5("1.5 A", 1.5, 30),
    A3("3 A (60 W)", 3.0, 60),
    A5("5 A (100 W)", 5.0, 100),
    EPR240("EPR 5 A @ 48 V (240 W)", 5.0, 240);
}

/** Construction of the cable. */
enum class CableConstruction(val label: String) {
    UNKNOWN("Unknown"),
    PASSIVE("Passive"),
    ACTIVE_REDRIVER("Active (re-driver)"),
    ACTIVE_RETIMER("Active (re-timer)"),
    ACTIVE_OPTICAL("Active optical");
}

/** Which power-delivery protocol the current charging session is using. */
enum class ChargeProtocol(val label: String) {
    NONE("Not charging"),
    USB_DEFAULT("USB (5 V)"),
    USB_TYPEC_CURRENT("USB-C current (no PD)"),
    BC12("BC 1.2 / DCP"),
    PD("USB Power Delivery"),
    PD_PPS("USB PD PPS (adjustable)"),
    PD_EPR("USB PD EPR (>100 W)"),
    QC("Qualcomm Quick Charge"),
    PROPRIETARY("Proprietary fast charge (VOOC/SuperVOOC/etc.)"),
    UNKNOWN("Unknown");
}

/** The kind of thing on the other end of the cable. */
enum class PartnerType(val label: String) {
    UNKNOWN("Unknown"),
    CHARGER("Power adapter"),
    HOST_PC("Computer / host"),
    HUB("Hub"),
    PERIPHERAL("Peripheral / accessory"),
    DISPLAY("Display"),
    NONE("Nothing connected");
}

/** How confident we are about a given fact and where it came from. */
enum class Confidence(val label: String) {
    /** Read directly from the cable eMarker (ground truth). */
    EMARKER("from eMarker"),
    /** Read from kernel PD/Type-C state. */
    KERNEL("from system"),
    /** Inferred from the live charging session (V x I). */
    MEASURED("measured live"),
    /** Looked up from the bundled spec database by model. */
    DATABASE("from database"),
    /** Best-effort guess. */
    INFERRED("inferred"),
    /** No data available. */
    NONE("unavailable");
}

/** A single fact about the cable with provenance. */
data class Fact<T>(val value: T, val confidence: Confidence) {
    val isKnown: Boolean get() = confidence != Confidence.NONE
}

/** Live electrical measurement of the current charging session. */
data class LivePower(
    val charging: Boolean = false,
    val plugged: Int = 0,                  // BatteryManager.BATTERY_PLUGGED_* (0 = unplugged)
    val pluggedType: String = "—",        // AC / USB / Wireless / Dock
    val batteryVoltageMv: Int = 0,         // battery terminal voltage (always available)
    val currentNowUa: Int = 0,             // instantaneous current (µA, +charging)
    val maxChargeCurrentUa: Int = 0,       // charger-advertised max current (µA)
    val maxChargeVoltageUv: Int = 0,       // charger-advertised max voltage (µV)
    val batteryPct: Int = 0,
    val temperatureDeciC: Int = 0,
) {
    /** Approximate instantaneous power into the battery, in watts. */
    val livePowerW: Double
        get() = (batteryVoltageMv / 1000.0) * (currentNowUa / 1_000_000.0)

    /** Charger-negotiated contract power (advertised), in watts. */
    val negotiatedPowerW: Double
        get() = (maxChargeVoltageUv / 1_000_000.0) * (maxChargeCurrentUa / 1_000_000.0)
}

/** Optional richer data pulled from sysfs via Shizuku/root. */
data class SysfsSnapshot(
    val available: Boolean = false,
    val source: String = "",               // "shizuku" / "root" / ""
    val usbType: String? = null,           // PD / PD_PPS / DCP / SDP / CDP ...
    val realType: String? = null,          // Xiaomi vendor charger type
    val quickChargeType: String? = null,   // Xiaomi QC tier
    val vbusVoltageUv: Long? = null,        // actual VBUS input voltage
    val currentMaxUa: Long? = null,
    val apdoMax: String? = null,            // EPR/APDO advertised
    val ccOrientation: String? = null,
    val powerOperationMode: String? = null,// default / 1.5A / 3.0A / usb_power_delivery
    val pdActive: Boolean? = null,
    // eMarker (SOP') — the holy grail, rarely present
    val cableType: String? = null,         // active / passive
    val cablePlugType: String? = null,     // type-c / type-a / captive
    val cableIdHeaderHex: String? = null,  // raw id_header VDO (32-bit)
    val cableVdo1Hex: String? = null,      // raw product_type_vdo1
    val cableVendorId: String? = null,     // decoded VID string for display
    // MediaTek tcpc class (root-only on locked phones): rich PD engine info
    val mtkInfo: String? = null,           // type_c_port0/info dump
    val mtkCapsInfo: String? = null,       // type_c_port0/caps_info (PDOs)
    val mtkPeReady: String? = null,        // PD policy-engine ready flag
    val rawDump: Map<String, String> = emptyMap(),
)

/**
 * The fully-assembled assessment of the connected cable/session that the UI renders.
 */
data class CableAssessment(
    val live: LivePower = LivePower(),
    val sysfs: SysfsSnapshot = SysfsSnapshot(),
    val dataLink: DataLinkProbe = DataLinkProbe(),
    val dataSpeed: Fact<DataSpeed> = Fact(DataSpeed.UNKNOWN, Confidence.NONE),
    val currentRating: Fact<CurrentRating> = Fact(CurrentRating.UNKNOWN, Confidence.NONE),
    val construction: Fact<CableConstruction> = Fact(CableConstruction.UNKNOWN, Confidence.NONE),
    val protocol: Fact<ChargeProtocol> = Fact(ChargeProtocol.NONE, Confidence.NONE),
    val partner: Fact<PartnerType> = Fact(PartnerType.UNKNOWN, Confidence.NONE),
    val hasEmarker: Fact<Boolean> = Fact(false, Confidence.NONE),
    val cableVendor: String? = null,
    /** A short human-readable verdict, e.g. "Charging cable, ~18 W observed". */
    val headline: String = "Plug in a USB-C cable",
    /** Notes / caveats to show the user honestly. */
    val notes: List<String> = emptyList(),
) {
    val connected: Boolean
        get() = live.plugged != 0 || live.charging || (sysfs.pdActive == true)
}
