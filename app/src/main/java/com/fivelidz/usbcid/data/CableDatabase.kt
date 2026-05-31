package com.fivelidz.usbcid.data

import com.fivelidz.usbcid.model.CableConstruction
import com.fivelidz.usbcid.model.CurrentRating
import com.fivelidz.usbcid.model.DataSpeed

/**
 * A small curated database of common USB-C cable archetypes and named products.
 * This is the "What USBC" style fallback: when we cannot read the cable
 * electronically (the common case for passive cables), the user can pick the
 * closest match and see what that cable *should* do per its spec sheet.
 */
data class KnownCable(
    val name: String,
    val maker: String,
    val speed: DataSpeed,
    val current: CurrentRating,
    val construction: CableConstruction,
    val hasEmarker: Boolean,
    val notes: String,
)

object CableDatabase {

    /** Generic archetypes — useful for teaching and quick identification. */
    val archetypes = listOf(
        KnownCable(
            "Charge-only USB 2.0", "Generic",
            DataSpeed.USB2, CurrentRating.A3, CableConstruction.PASSIVE,
            hasEmarker = false,
            notes = "480 Mbps data (or no data at all), up to 60 W. The cable in most phone boxes."
        ),
        KnownCable(
            "USB 3.2 Gen 1 (5 Gbps)", "Generic",
            DataSpeed.USB3_GEN1, CurrentRating.A3, CableConstruction.PASSIVE,
            hasEmarker = false,
            notes = "5 Gbps data, up to 60 W. No eMarker required."
        ),
        KnownCable(
            "USB 3.2 Gen 2 (10 Gbps)", "Generic",
            DataSpeed.USB3_GEN2, CurrentRating.A3, CableConstruction.PASSIVE,
            hasEmarker = false,
            notes = "10 Gbps data, up to 60 W. Often visually identical to a 5 Gbps cable."
        ),
        KnownCable(
            "100 W USB 2.0 (5 A)", "Generic",
            DataSpeed.USB2, CurrentRating.A5, CableConstruction.PASSIVE,
            hasEmarker = true,
            notes = "Charge-focused: only 480 Mbps data but carries 5 A / 100 W. Has an eMarker."
        ),
        KnownCable(
            "USB4 / 240 W EPR", "Generic",
            DataSpeed.USB4_GEN3, CurrentRating.EPR240, CableConstruction.PASSIVE,
            hasEmarker = true,
            notes = "40 Gbps data + up to 240 W (48 V/5 A). Always has an eMarker."
        ),
        KnownCable(
            "Thunderbolt 4", "Generic",
            DataSpeed.TB4, CurrentRating.A5, CableConstruction.PASSIVE,
            hasEmarker = true,
            notes = "40 Gbps, 100 W, supports USB4/TB. Intel-certified eMarker (VID 0x8087)."
        ),
        KnownCable(
            "Thunderbolt 5", "Generic",
            DataSpeed.TB5, CurrentRating.EPR240, CableConstruction.ACTIVE_RETIMER,
            hasEmarker = true,
            notes = "Up to 120 Gbps, 240 W. Active cable, always eMarked."
        ),
    )

    /** A few well-known named cables for the searchable picker. */
    val products = listOf(
        KnownCable(
            "Apple USB-C Charge Cable (240 W)", "Apple",
            DataSpeed.USB2, CurrentRating.EPR240, CableConstruction.PASSIVE,
            true, "USB 2.0 data only, but 240 W EPR charging. Shipped with recent MacBooks."
        ),
        KnownCable(
            "Apple Thunderbolt 4 Pro Cable", "Apple",
            DataSpeed.TB4, CurrentRating.A5, CableConstruction.ACTIVE_RETIMER,
            true, "40 Gbps, 100 W. Active cable up to 1.8 m."
        ),
        KnownCable(
            "Anker 765 USB-C (140 W)", "Anker",
            DataSpeed.USB3_GEN2, CurrentRating.EPR240, CableConstruction.PASSIVE,
            true, "10 Gbps + 140 W EPR. Bonded-nylon."
        ),
        KnownCable(
            "Samsung 5 A Cable (EP-DN975)", "Samsung",
            DataSpeed.USB2, CurrentRating.A5, CableConstruction.PASSIVE,
            true, "USB 2.0 data, 5 A / 100 W for 45 W Super Fast Charging 2.0."
        ),
        KnownCable(
            "Google Pixel USB-C Cable", "Google",
            DataSpeed.USB2, CurrentRating.A3, CableConstruction.PASSIVE,
            false, "USB 2.0, 3 A / 60 W. Bundled with Pixel phones."
        ),
        KnownCable(
            "Cable Matters USB4 (40 Gbps)", "Cable Matters",
            DataSpeed.USB4_GEN3, CurrentRating.EPR240, CableConstruction.PASSIVE,
            true, "USB4 40 Gbps, 240 W EPR, DisplayPort Alt Mode."
        ),
    )

    val all: List<KnownCable> by lazy { archetypes + products }

    fun search(query: String): List<KnownCable> {
        if (query.isBlank()) return all
        val q = query.trim().lowercase()
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.maker.lowercase().contains(q) ||
                it.notes.lowercase().contains(q) ||
                it.speed.label.lowercase().contains(q)
        }
    }
}
