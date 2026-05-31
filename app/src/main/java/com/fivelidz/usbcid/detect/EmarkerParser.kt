package com.fivelidz.usbcid.detect

import com.fivelidz.usbcid.model.CableConstruction
import com.fivelidz.usbcid.model.CurrentRating
import com.fivelidz.usbcid.model.DataSpeed

/**
 * Decodes USB-PD Structured VDOs (Vendor Defined Objects) read from
 * /sys/class/typec/portN-cable/identity into human-meaningful capability fields.
 *
 * Bit layout verified against the Linux kernel header
 * include/linux/usb/pd_vdo.h (the authoritative source for the values exposed
 * in sysfs):
 *
 *  ID Header VDO:
 *    bits[15:0]   USB-IF Vendor ID
 *    bits[29:27]  Product Type (SOP'): 3=Passive Cable, 4=Active Cable, 6=VPD
 *
 *  Passive/Active Cable VDO1 (product_type_vdo1), PD Rev3.0+:
 *    bits[2:0]    USB Highest Speed: 0=USB2, 1=USB3.2 Gen1, 2=USB3.2/4 Gen2, 3=USB4 Gen3
 *    bits[6:5]    VBUS current capability: 1=3A, 2=5A
 *    bits[19:18]  Cable / connector type: 2=Type-C, 3=Captive
 *
 *  Active Cable VDO1 additionally: bit[9] retimer/active element context lives in
 *  Active Cable VDO2; we rely on the explicit sysfs "type" node for active/passive.
 *
 * We decode conservatively and fall back to UNKNOWN rather than asserting wrong
 * values for malformed or zeroed VDOs (common on counterfeit cables).
 */
object EmarkerParser {

    // Field extractors mirroring the kernel macros.
    private fun cableSpeed(vdo: Long) = (vdo and 0x7).toInt()           // bits[2:0]
    private fun cableCurrent(vdo: Long) = ((vdo shr 5) and 0x3).toInt() // bits[6:5]
    private fun cableConnType(vdo: Long) = ((vdo shr 18) and 0x3).toInt() // bits[19:18]
    private fun idhProductType(vdo: Long) = ((vdo shr 27) and 0x7).toInt() // bits[29:27]

    data class Decoded(
        val vendorId: String?,
        val isCablePlug: Boolean,
        val speed: DataSpeed,
        val current: CurrentRating,
        val construction: CableConstruction,
    )

    /** Parse a hex string like "0x12345678" or "12345678" to Long, or null. */
    fun hex(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().removePrefix("0x").removePrefix("0X")
        return t.toLongOrNull(16)
    }

    fun decodeIdHeader(idHeaderHex: String?): Pair<String?, Boolean> {
        val v = hex(idHeaderHex) ?: return null to false
        val vid = (v and 0xFFFF).toInt()
        val ptype = idhProductType(v)
        // 3 = Passive Cable, 4 = Active Cable, 6 = VPD (also cable-like)
        val isCable = ptype == 3 || ptype == 4 || ptype == 6
        return String.format("0x%04X", vid) to isCable
    }

    fun decodeCableVdo1(vdoHex: String?): Triple<DataSpeed, CurrentRating, CableConstruction> {
        val v = hex(vdoHex) ?: return Triple(DataSpeed.UNKNOWN, CurrentRating.UNKNOWN, CableConstruction.UNKNOWN)

        val speed = when (cableSpeed(v)) {                   // bits[2:0]
            0 -> DataSpeed.USB2
            1 -> DataSpeed.USB3_GEN1
            2 -> DataSpeed.USB3_GEN2     // USB 3.2 / USB4 Gen 2 (10 Gbps line rate)
            3 -> DataSpeed.USB4_GEN3
            else -> DataSpeed.UNKNOWN
        }

        val current = when (cableCurrent(v)) {               // bits[6:5]
            1 -> CurrentRating.A3
            2 -> CurrentRating.A5
            else -> CurrentRating.UNKNOWN   // 0 = default, 3 = reserved
        }

        return Triple(speed, current, CableConstruction.UNKNOWN)
    }

    /**
     * Combine the explicit sysfs "type" string with the decoded VDOs.
     * @param idHeaderHex the RAW 32-bit id_header VDO hex (not a pre-decoded VID).
     */
    fun decode(
        idHeaderHex: String?,
        cableVdo1Hex: String?,
        typeStr: String?,   // "active" / "passive"
    ): Decoded {
        val (vid, isCable) = decodeIdHeader(idHeaderHex)
        val (speed, current, _) = decodeCableVdo1(cableVdo1Hex)
        val construction = when (typeStr?.lowercase()) {
            "passive" -> CableConstruction.PASSIVE
            "active" -> CableConstruction.ACTIVE_RETIMER
            else -> CableConstruction.UNKNOWN
        }
        return Decoded(vid, isCable, speed, current, construction)
    }

    /** Friendly vendor name for a known VID. */
    fun vendorName(vid: String?): String? = when (vid?.uppercase()) {
        "0X8087" -> "Intel (Thunderbolt)"
        "0X05AC" -> "Apple"
        "0X04B4" -> "Cypress / Infineon"
        "0X2109" -> "VIA Labs"
        "0X0BDA" -> "Realtek"
        "0X18D1" -> "Google"
        "0X04E8" -> "Samsung"
        else -> null
    }
}
