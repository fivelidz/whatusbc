package com.fivelidz.usbcid.detect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.fivelidz.usbcid.model.LivePower
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Tier-1 detection: works on every Android phone with zero permissions.
 *
 * Uses the public BatteryManager API + the sticky ACTION_BATTERY_CHANGED
 * broadcast. This is exactly the data source that apps like "Ampere" rely on.
 * It tells us: plug type, battery voltage, live charging current, and the
 * charger-advertised max current/voltage extras.
 */
class BatteryMonitor(private val context: Context) {

    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    /** A cold flow that emits a fresh [LivePower] whenever the battery state changes. */
    fun flow(): Flow<LivePower> = callbackFlow {
        fun emit(intent: Intent?) {
            trySend(snapshot(intent))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = emit(intent)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // registerReceiver with a battery-changed filter returns the sticky intent
        val sticky = context.registerReceiver(receiver, filter)
        emit(sticky)

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /** Build a one-shot snapshot from an optional ACTION_BATTERY_CHANGED intent. */
    fun snapshot(intent: Intent? = null): LivePower {
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?: bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val pluggedLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC / fast"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 /* BATTERY_PLUGGED_DOCK */ -> "Dock"
            else -> if (charging) "Power" else "—"
        }

        val battVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Hidden-but-usually-present charger advertised maxima
        val maxCurrent = intent?.getIntExtra("max_charging_current", 0) ?: 0
        val maxVoltage = intent?.getIntExtra("max_charging_voltage", 0) ?: 0

        // Live current: CURRENT_NOW is in µA on most devices (+charging here, OEM sign varies)
        val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        return LivePower(
            charging = charging,
            plugged = plugged,
            pluggedType = pluggedLabel,
            batteryVoltageMv = battVoltage,
            currentNowUa = normalizeCurrent(currentNow, charging),
            maxChargeCurrentUa = maxCurrent,
            maxChargeVoltageUv = maxVoltage,
            batteryPct = pct,
            temperatureDeciC = temp,
        )
    }

    /**
     * CURRENT_NOW units & sign are inconsistent across OEMs: some report mA
     * instead of µA, some report charging as negative. We coerce to a
     * µA-magnitude, only applying the mA→µA scaling while actively charging
     * (when idle, small genuine µA trickle values must not be inflated).
     */
    private fun normalizeCurrent(raw: Int, isCharging: Boolean): Int {
        if (raw == 0) return 0
        val abs = kotlin.math.abs(raw)
        // Charging currents are at least ~100 mA. A magnitude < 10000 while
        // charging is therefore mA-scaled; multiply up. Otherwise treat as µA.
        return if (isCharging && abs < 10_000) abs * 1000 else abs
    }
}
