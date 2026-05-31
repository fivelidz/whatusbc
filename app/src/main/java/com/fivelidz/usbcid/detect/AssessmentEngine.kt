package com.fivelidz.usbcid.detect

import com.fivelidz.usbcid.model.*

/**
 * Fuses Tier-1 (BatteryManager) and Tier-2/3 (sysfs) data into a single
 * [CableAssessment], being scrupulously honest about confidence: eMarker data
 * is ground truth, kernel state is strong, live measurement is an inference,
 * and anything else is a guess or unavailable.
 */
object AssessmentEngine {

    fun assess(
        live: LivePower,
        sysfs: SysfsSnapshot,
        dataLink: DataLinkProbe = DataLinkProbe(),
    ): CableAssessment {
        val notes = mutableListOf<String>()

        // ---- Protocol ----
        val protocol = derivedProtocol(live, sysfs)

        // ---- Cable current rating ----
        val emarker = if (sysfs.cableVdo1Hex != null || sysfs.cableIdHeaderPresent()) {
            EmarkerParser.decode(
                idHeaderHex = sysfs.cableIdHeaderHex, // RAW 32-bit id_header VDO
                cableVdo1Hex = sysfs.cableVdo1Hex,
                typeStr = sysfs.cableType,
            )
        } else null

        val currentRating: Fact<CurrentRating> = when {
            emarker != null && emarker.current != CurrentRating.UNKNOWN ->
                Fact(emarker.current, Confidence.EMARKER)
            // Infer from negotiated contract
            live.negotiatedPowerW >= 100 -> Fact(CurrentRating.A5, Confidence.MEASURED)
            parsePositive(sysfs.apdoMax) ->
                Fact(CurrentRating.A5, Confidence.KERNEL)
            live.maxChargeCurrentUa >= 4_500_000 -> Fact(CurrentRating.A5, Confidence.MEASURED)
            live.maxChargeCurrentUa >= 2_700_000 -> Fact(CurrentRating.A3, Confidence.MEASURED)
            live.maxChargeCurrentUa in 1..2_699_999 -> {
                val r = if (live.maxChargeCurrentUa >= 1_300_000) CurrentRating.A1_5 else CurrentRating.A0_9
                Fact(r, Confidence.MEASURED)
            }
            else -> Fact(CurrentRating.UNKNOWN, Confidence.NONE)
        }

        // ---- Data speed ----
        // Precedence: eMarker (truth) > live link test > unknown.
        // The live link test proves the cable carries data and, when the phone's
        // port ceiling is known, bounds the achievable speed.
        val dataSpeed: Fact<DataSpeed> = when {
            emarker != null && emarker.speed != DataSpeed.UNKNOWN ->
                Fact(emarker.speed, Confidence.EMARKER)
            dataLink.provenCarriesData && dataLink.portCeilingKnown ->
                Fact(dataLink.portCeiling, Confidence.MEASURED)
            dataLink.provenCarriesData ->
                Fact(DataSpeed.USB2, Confidence.MEASURED)  // at least USB 2.0 proven
            else -> {
                notes += "Plug the cable into a computer to run a live data test — " +
                    "the link speed it negotiates proves the cable's data capability " +
                    "without needing the cable's chip."
                Fact(DataSpeed.UNKNOWN, Confidence.NONE)
            }
        }

        if (dataLink.portCeilingKnown && dataLink.portCeiling == DataSpeed.USB2) {
            notes += "This phone's USB port (${dataLink.controllerName}) tops out at " +
                "USB 2.0 / 480 Mbps — no cable can exceed that here."
        }

        // ---- Construction ----
        val construction: Fact<CableConstruction> = when {
            emarker != null && emarker.construction != CableConstruction.UNKNOWN ->
                Fact(emarker.construction, Confidence.EMARKER)
            sysfs.cableType?.equals("passive", true) == true ->
                Fact(CableConstruction.PASSIVE, Confidence.KERNEL)
            sysfs.cableType?.equals("active", true) == true ->
                Fact(CableConstruction.ACTIVE_RETIMER, Confidence.KERNEL)
            else -> Fact(CableConstruction.UNKNOWN, Confidence.NONE)
        }

        // ---- eMarker presence ----
        val hasEmarker: Fact<Boolean> = when {
            emarker != null -> Fact(true, Confidence.EMARKER)
            currentRating.value == CurrentRating.A5 || currentRating.value == CurrentRating.EPR240 ->
                Fact(true, Confidence.INFERRED) // >3A legally requires an eMarker
            else -> Fact(false, Confidence.NONE)
        }

        // ---- Partner ----
        val partner: Fact<PartnerType> = when {
            sysfs.usbType?.contains("PD", true) == true || protocol.value == ChargeProtocol.PD ->
                Fact(PartnerType.CHARGER, Confidence.KERNEL)
            live.pluggedType.startsWith("AC") -> Fact(PartnerType.CHARGER, Confidence.INFERRED)
            live.pluggedType == "USB" -> Fact(PartnerType.HOST_PC, Confidence.INFERRED)
            live.charging -> Fact(PartnerType.CHARGER, Confidence.INFERRED)
            else -> Fact(PartnerType.NONE, Confidence.NONE)
        }

        val vendor = EmarkerParser.vendorName(sysfs.cableVendorId) ?: sysfs.cableVendorId

        // ---- Headline ----
        val headline = buildHeadline(live, sysfs, protocol, currentRating, hasEmarker)

        if (!sysfs.available) {
            notes += "Grant Shizuku or root access to read the charger protocol, " +
                "negotiated voltage and (if present) the cable's eMarker chip."
        }
        if (sysfs.available && emarker == null && live.charging) {
            notes += "No eMarker data exposed. Either the cable has none (≤3 A passive) or " +
                "this phone's kernel doesn't surface cable identity."
        }
        if (sysfs.mtkCapsInfo != null || sysfs.mtkInfo != null) {
            notes += "Read MediaTek PD engine directly (root): the chip's negotiated " +
                "capabilities are shown in DETAILS."
        }

        return CableAssessment(
            live = live,
            sysfs = sysfs,
            dataLink = dataLink,
            dataSpeed = dataSpeed,
            currentRating = currentRating,
            construction = construction,
            protocol = protocol,
            partner = partner,
            hasEmarker = hasEmarker,
            cableVendor = vendor,
            headline = headline,
            notes = notes,
        )
    }

    private fun derivedProtocol(live: LivePower, sysfs: SysfsSnapshot): Fact<ChargeProtocol> {
        val s = (sysfs.realType ?: sysfs.usbType ?: "").uppercase()
        val qc = sysfs.quickChargeType
        return when {
            s.contains("PD_PPS") || s.contains("PPS") -> Fact(ChargeProtocol.PD_PPS, Confidence.KERNEL)
            sysfs.apdoMax?.toIntOrNull()?.let { it > 0 } == true && s.contains("PD") ->
                Fact(ChargeProtocol.PD_EPR, Confidence.KERNEL)
            s.contains("PD") -> Fact(ChargeProtocol.PD, Confidence.KERNEL)
            s.contains("DCP") -> Fact(ChargeProtocol.BC12, Confidence.KERNEL)
            s.contains("CDP") || s.contains("SDP") -> Fact(ChargeProtocol.USB_DEFAULT, Confidence.KERNEL)
            s.contains("HVDCP") || s.contains("QC") -> Fact(ChargeProtocol.QC, Confidence.KERNEL)
            !qc.isNullOrBlank() && qc != "0" -> Fact(ChargeProtocol.PROPRIETARY, Confidence.KERNEL)
            live.negotiatedPowerW >= 15 -> Fact(ChargeProtocol.PD, Confidence.INFERRED)
            live.charging && live.pluggedType.startsWith("AC") -> Fact(ChargeProtocol.PROPRIETARY, Confidence.INFERRED)
            live.charging -> Fact(ChargeProtocol.USB_DEFAULT, Confidence.INFERRED)
            else -> Fact(ChargeProtocol.NONE, Confidence.NONE)
        }
    }

    private fun buildHeadline(
        live: LivePower,
        sysfs: SysfsSnapshot,
        protocol: Fact<ChargeProtocol>,
        current: Fact<CurrentRating>,
        emarker: Fact<Boolean>,
    ): String {
        val connected = live.plugged != 0 || live.charging || sysfs.pdActive == true
        if (!connected) return "Plug in a USB-C cable"

        // Data-only connection (e.g. plugged into a PC, not charging).
        if (!live.charging && live.plugged == 0 && sysfs.pdActive != true) {
            return "Cable connected · data link"
        }

        val w = when {
            live.negotiatedPowerW >= 1 -> live.negotiatedPowerW
            live.livePowerW >= 0.5 -> live.livePowerW
            else -> 0.0
        }
        val watt = if (w >= 1) "~${w.toInt()} W" else "low power"
        val proto = protocol.value.label
        val emk = if (emarker.value) " · eMarked" else ""
        return "$proto · $watt$emk"
    }
}

/** Helper: whether we have any eMarker identity hint. */
private fun SysfsSnapshot.cableIdHeaderPresent(): Boolean =
    cableIdHeaderHex != null || cableVendorId != null || cableVdo1Hex != null || cableType != null

/**
 * sysfs apdo_max can be a bare number, a µW value, or a string like "140W" /
 * "28000000". Treat any embedded positive number as "EPR/APDO present".
 */
private fun parsePositive(s: String?): Boolean {
    if (s.isNullOrBlank()) return false
    val n = Regex("\\d+").find(s)?.value?.toLongOrNull() ?: return false
    return n > 0
}
