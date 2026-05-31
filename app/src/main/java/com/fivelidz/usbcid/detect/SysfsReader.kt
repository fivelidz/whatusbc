package com.fivelidz.usbcid.detect

import com.fivelidz.usbcid.model.SysfsSnapshot
import com.fivelidz.usbcid.shizuku.ShellAccess

/**
 * Tier-2/3 detection: reads USB-C / power-supply sysfs nodes via Shizuku or
 * root. This is where the genuinely interesting data lives -- the negotiated
 * PD contract, the charger protocol, and (when the kernel populated it) the
 * cable eMarker VDOs.
 *
 * Different OEMs expose wildly different node sets, so we probe a superset of
 * known paths (stock Linux typec class + Qualcomm + MediaTek + Xiaomi vendor
 * nodes) and keep whatever answers.
 */
class SysfsReader {

    fun read(backend: ShellAccess.Backend = ShellAccess.bestBackend()): SysfsSnapshot {
        val b = backend
        if (b == ShellAccess.Backend.NONE) return SysfsSnapshot(available = false)

        val dump = linkedMapOf<String, String>()
        fun cat(path: String): String? = ShellAccess.cat(path, b)?.also { dump[path] = it }

        // ---- Stock Linux Type-C class (Pixel, many AOSP-ish kernels) ----
        val portBase = findPortBase(b)
        val powerOpMode = cat("$portBase/power_operation_mode")
        val ccOrientation = cat("$portBase/orientation")

        // eMarker (SOP') -- only present if kernel queried it AND cable is eMarked
        val cableBase = portBase.replace(Regex("/port(\\d+)$"), "/port$1-cable")
        val cableType = cat("$cableBase/type")
        val cablePlugType = cat("$cableBase/plug_type")
        val cableIdHeader = cat("$cableBase/identity/id_header")
        val cableVdo1 = cat("$cableBase/identity/product_type_vdo1")

        // ---- power_supply/usb : the broadly-available charger contract ----
        val ps = "/sys/class/power_supply/usb"
        val usbType = cat("$ps/usb_type") ?: cat("$ps/type")
        val realType = cat("$ps/real_type")               // Xiaomi
        val quickChargeType = cat("$ps/quick_charge_type") // Xiaomi
        val vbus = cat("$ps/voltage_now")?.toLongOrNull()
        val currentMax = cat("$ps/current_max")?.toLongOrNull()
        val apdoMax = cat("$ps/apdo_max")
        val ccOrientation2 = cat("$ps/typec_cc_orientation")
        val pdAuthd = cat("$ps/pd_verifed") // Xiaomi spelling

        val pdActive = when {
            usbType?.contains("PD", ignoreCase = true) == true -> true
            realType?.contains("PD", ignoreCase = true) == true -> true
            powerOpMode?.contains("power_delivery", ignoreCase = true) == true -> true
            else -> null
        }

        val cableVid = cableIdHeader?.let {
            EmarkerParser.decodeIdHeader(it).first
        }

        // ---- MediaTek tcpc class (mt6375 etc.) ----
        // These nodes (info / caps_info / pe_ready) hold the PD engine state and
        // negotiated source-capability PDOs. They are SELinux-locked to the
        // charger domain on HyperOS, so only ROOT (with permissive/allowing
        // policy) can read them -- but on rooted MTK phones this is gold.
        val mtkPort = findMtkPort(b)
        val mtkInfo = mtkPort?.let { cat("$it/info") }
        val mtkCaps = mtkPort?.let { cat("$it/caps_info") }
        val mtkPe = mtkPort?.let { cat("$it/pe_ready") }

        val mtkPdActive = mtkPe?.contains("1") == true ||
            mtkInfo?.contains("PE_", ignoreCase = true) == true

        return SysfsSnapshot(
            available = true,
            source = if (b == ShellAccess.Backend.ROOT) "root" else "shizuku",
            usbType = usbType,
            realType = realType,
            quickChargeType = quickChargeType,
            vbusVoltageUv = vbus,
            currentMaxUa = currentMax,
            apdoMax = apdoMax,
            ccOrientation = ccOrientation ?: ccOrientation2,
            powerOperationMode = powerOpMode,
            pdActive = pdActive ?: mtkPdActive.takeIf { it },
            mtkInfo = mtkInfo,
            mtkCapsInfo = mtkCaps,
            mtkPeReady = mtkPe,
            cableType = cableType,
            cablePlugType = cablePlugType,
            cableIdHeaderHex = cableIdHeader,
            cableVdo1Hex = cableVdo1,
            cableVendorId = cableVid,
            rawDump = dump,
        )
    }

    /** Find the first available typec port directory. */
    private fun findPortBase(b: ShellAccess.Backend): String {
        val listed = ShellAccess.run("ls -d /sys/class/typec/port[0-9] 2>/dev/null | head -1", b)
        return listed?.trim()?.lineSequence()?.firstOrNull()?.trim()
            ?: "/sys/class/typec/port0"
    }

    /**
     * Find the MediaTek tcpc port directory, e.g.
     * /sys/class/tcpc/type_c_port0 (a symlink the kernel creates on MTK SoCs).
     */
    private fun findMtkPort(b: ShellAccess.Backend): String? {
        val listed = ShellAccess.run(
            "ls -d /sys/class/tcpc/type_c_port[0-9] 2>/dev/null | head -1", b
        )?.trim()?.lineSequence()?.firstOrNull()?.trim()
        return listed?.takeIf { it.isNotBlank() }
    }
}
