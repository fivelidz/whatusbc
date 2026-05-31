package com.fivelidz.usbcid.detect

import com.fivelidz.usbcid.model.*

/**
 * Turns everything we know — eMarker (ground truth), live charging measurement,
 * and the user's guided-wizard observations — into a confident verdict across
 * the five axes the user actually asked about: Data, Speed, Video, Audio,
 * Charging.
 *
 * Precedence: EMARKER > KERNEL/MEASURED > wizard DATABASE/INFERRED.
 */
object CapabilityResolver {

    fun resolve(a: CableAssessment, w: WizardAnswers): CapabilityVerdict {
        // The strongest known data speed from any electronic source.
        val electronicSpeed = a.dataSpeed.value.takeIf { it != DataSpeed.UNKNOWN }
        val emarkerConf = a.dataSpeed.confidence

        // Infer a speed from the wizard markings/logos.
        val wizardSpeed = inferSpeedFromWizard(w)

        val speed: DataSpeed
        val speedConf: Confidence
        when {
            electronicSpeed != null -> { speed = electronicSpeed; speedConf = emarkerConf }
            wizardSpeed != null -> { speed = wizardSpeed; speedConf = Confidence.DATABASE }
            else -> { speed = DataSpeed.UNKNOWN; speedConf = Confidence.NONE }
        }

        return CapabilityVerdict(
            data = dataAxis(speed, speedConf, w, a.dataLink),
            speed = speedAxis(speed, speedConf),
            video = videoAxis(speed, speedConf, w),
            audio = audioAxis(speed, speedConf, w),
            charging = chargingAxis(a, w),
        )
    }

    private fun inferSpeedFromWizard(w: WizardAnswers): DataSpeed? {
        // Brand logos are the strongest hint.
        when (w.brandLogo) {
            BrandLogo.THUNDERBOLT -> return DataSpeed.TB4
            BrandLogo.USB4 -> return DataSpeed.USB4_GEN3
            else -> {}
        }
        when (w.marking) {
            CableMarking.N80 -> return DataSpeed.USB4_V2
            CableMarking.N40 -> return DataSpeed.USB4_GEN3
            CableMarking.SS20 -> return DataSpeed.USB3_GEN2X2
            CableMarking.SS10 -> return DataSpeed.USB3_GEN2
            CableMarking.SS -> return DataSpeed.USB3_GEN1
            CableMarking.NONE -> return DataSpeed.USB2
            CableMarking.UNKNOWN -> {}
        }
        // Provenance hints.
        return when (w.whatCameWith) {
            CameWith.EXTERNAL_SSD, CameWith.MONITOR, CameWith.LAPTOP -> DataSpeed.USB3_GEN1
            CameWith.PHONE_CHARGER -> DataSpeed.USB2
            else -> null
        }
    }

    private val superSpeeds = setOf(
        DataSpeed.USB3_GEN1, DataSpeed.USB3_GEN2, DataSpeed.USB3_GEN2X2,
        DataSpeed.USB4_GEN2, DataSpeed.USB4_GEN3, DataSpeed.USB4_V2,
        DataSpeed.TB3, DataSpeed.TB4, DataSpeed.TB5,
    )
    private val altModeSpeeds = setOf(
        DataSpeed.USB3_GEN1, DataSpeed.USB3_GEN2, DataSpeed.USB3_GEN2X2,
        DataSpeed.USB4_GEN2, DataSpeed.USB4_GEN3, DataSpeed.USB4_V2,
        DataSpeed.TB3, DataSpeed.TB4, DataSpeed.TB5,
    )

    private fun dataAxis(
        speed: DataSpeed, conf: Confidence, w: WizardAnswers, link: DataLinkProbe,
    ): CapabilityAxis {
        val proven = link.provenCarriesData
        return when {
            proven && speed == DataSpeed.USB2 -> CapabilityAxis(
                "Data", Verdict.YES, "USB 2.0 (proven live)",
                "A live data link is running over this cable right now, so it carries data. " +
                    if (link.portCeilingKnown)
                        "This phone's port maxes at ${link.portCeiling.short}, so that's the ceiling here."
                    else "",
                Confidence.MEASURED
            )
            proven -> CapabilityAxis(
                "Data", Verdict.YES, "${speed.short} (proven live)",
                "A live data link is running over this cable right now — it carries data.",
                Confidence.MEASURED
            )
            speed == DataSpeed.UNKNOWN -> CapabilityAxis(
                "Data", Verdict.UNKNOWN, "—",
                "Plug into a computer to run the live data test, or use the guided wizard. " +
                    "Most plain charge cables still carry USB 2.0 data.",
                Confidence.NONE
            )
            speed == DataSpeed.USB2 -> CapabilityAxis(
                "Data", Verdict.YES, "USB 2.0",
                "Carries basic 480 Mbps data — fine for phones, keyboards, slow transfers.",
                conf
            )
            else -> CapabilityAxis(
                "Data", Verdict.YES, speed.label,
                "Full high-speed data lanes are present.",
                conf
            )
        }
    }

    private fun speedAxis(speed: DataSpeed, conf: Confidence): CapabilityAxis {
        if (speed == DataSpeed.UNKNOWN) return CapabilityAxis(
            "Speed", Verdict.UNKNOWN, "—",
            "Unknown without an eMarker or a cable marking.", Confidence.NONE
        )
        return CapabilityAxis(
            "Speed", Verdict.YES, speed.short,
            "${speed.label} — ${speed.short}.", conf
        )
    }

    private fun videoAxis(speed: DataSpeed, conf: Confidence, w: WizardAnswers): CapabilityAxis {
        // Confirmed by user trying it.
        if (w.videoWorks == TriState.YES) return CapabilityAxis(
            "Video", Verdict.YES, "Confirmed",
            "You confirmed video output works through this cable.", Confidence.MEASURED
        )
        if (w.videoWorks == TriState.NO) return CapabilityAxis(
            "Video", Verdict.NO, "Didn't work",
            "Video didn't work — likely a USB 2.0 / charge-only cable, or the device lacks DP Alt Mode.",
            Confidence.MEASURED
        )
        return when {
            speed in altModeSpeeds -> CapabilityAxis(
                "Video", Verdict.LIKELY, "DP Alt Mode",
                "SuperSpeed lanes present → DisplayPort Alt Mode video is supported (if both devices do).",
                conf
            )
            speed == DataSpeed.USB2 -> CapabilityAxis(
                "Video", Verdict.UNLIKELY, "No SS lanes",
                "USB 2.0 cable: no SuperSpeed lanes, so no DisplayPort video.",
                conf
            )
            else -> CapabilityAxis(
                "Video", Verdict.MAYBE, "Depends",
                "Video needs SuperSpeed lanes. Try it, or check for an SS/10/40 marking.",
                Confidence.NONE
            )
        }
    }

    private fun audioAxis(speed: DataSpeed, conf: Confidence, w: WizardAnswers): CapabilityAxis {
        // USB-C audio is usually digital over USB data (USB Audio Class) or over
        // DP Alt Mode for HDMI/DP audio. Analog "audio adapter accessory mode" is
        // rare on cables. So audio essentially follows data/video.
        return when {
            w.videoWorks == TriState.YES -> CapabilityAxis(
                "Audio", Verdict.YES, "Over DP/USB",
                "If video works, audio rides along over DisplayPort or USB audio.",
                Confidence.MEASURED
            )
            speed == DataSpeed.UNKNOWN -> CapabilityAxis(
                "Audio", Verdict.MAYBE, "Depends",
                "Digital USB audio works on most data cables; HDMI/DP audio needs SuperSpeed lanes.",
                Confidence.NONE
            )
            speed == DataSpeed.USB2 -> CapabilityAxis(
                "Audio", Verdict.LIKELY, "USB audio",
                "USB Audio (e.g. a USB-C headset/DAC) works over USB 2.0 data. No HDMI/DP audio though.",
                conf
            )
            else -> CapabilityAxis(
                "Audio", Verdict.YES, "USB + DP",
                "Supports USB audio and, via Alt Mode, DisplayPort/HDMI audio.",
                conf
            )
        }
    }

    private fun chargingAxis(a: CableAssessment, w: WizardAnswers): CapabilityAxis {
        val rating = a.currentRating.value
        val conf = a.currentRating.confidence
        // Live-measured wattage is concrete proof of charging.
        val liveW = maxOf(a.live.negotiatedPowerW, a.live.livePowerW)
        return when {
            rating == CurrentRating.EPR240 -> CapabilityAxis(
                "Charging", Verdict.YES, "Up to 240 W",
                "EPR cable: 5 A at up to 48 V. Charges anything, including big laptops.", conf
            )
            rating == CurrentRating.A5 -> CapabilityAxis(
                "Charging", Verdict.YES, "Up to 100 W",
                "5 A eMarked cable: 100 W at 20 V. Fast-charges phones and most laptops.", conf
            )
            rating == CurrentRating.A3 -> CapabilityAxis(
                "Charging", Verdict.YES, "Up to 60 W",
                "3 A cable: 60 W at 20 V. Fine for phones and smaller laptops.", conf
            )
            rating != CurrentRating.UNKNOWN -> CapabilityAxis(
                "Charging", Verdict.YES, rating.label,
                "Basic charging current.", conf
            )
            liveW >= 0.5 -> CapabilityAxis(
                "Charging", Verdict.YES, "~%.0f W now".format(liveW),
                "Currently charging — so it carries power. Plug into a stronger charger to see its ceiling.",
                Confidence.MEASURED
            )
            w.whatCameWith != CameWith.UNKNOWN -> CapabilityAxis(
                "Charging", Verdict.LIKELY, "≥60 W",
                "Almost every USB-C cable carries at least 3 A / 60 W of charging.",
                Confidence.INFERRED
            )
            else -> CapabilityAxis(
                "Charging", Verdict.LIKELY, "≥60 W",
                "Virtually all USB-C cables do at least 60 W. Plug into a charger to confirm the ceiling.",
                Confidence.INFERRED
            )
        }
    }
}
