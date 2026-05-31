package com.fivelidz.usbcid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fivelidz.usbcid.detect.AssessmentEngine
import com.fivelidz.usbcid.detect.BatteryMonitor
import com.fivelidz.usbcid.detect.CapabilityResolver
import com.fivelidz.usbcid.detect.DataLinkReader
import com.fivelidz.usbcid.detect.SysfsReader
import com.fivelidz.usbcid.model.CableAssessment
import com.fivelidz.usbcid.model.CapabilityVerdict
import com.fivelidz.usbcid.model.LivePower
import com.fivelidz.usbcid.model.SysfsSnapshot
import com.fivelidz.usbcid.model.WizardAnswers
import com.fivelidz.usbcid.shizuku.ShellAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CableViewModel(app: Application) : AndroidViewModel(app) {

    private val battery = BatteryMonitor(app)
    private val sysfsReader = SysfsReader()
    private val dataLinkReader = DataLinkReader(app)

    private val _state = MutableStateFlow(CableAssessment())
    val state: StateFlow<CableAssessment> = _state.asStateFlow()

    private val _backend = MutableStateFlow(ShellAccess.Backend.NONE)
    val backend: StateFlow<ShellAccess.Backend> = _backend.asStateFlow()

    private val _wizard = MutableStateFlow(WizardAnswers())
    val wizard: StateFlow<WizardAnswers> = _wizard.asStateFlow()

    private val _verdict = MutableStateFlow(
        CapabilityResolver.resolve(CableAssessment(), WizardAnswers())
    )
    val verdict: StateFlow<CapabilityVerdict> = _verdict.asStateFlow()

    /** One-shot connect/disconnect signal for transient UI cues (toast/animation). */
    enum class PlugEvent { CONNECTED, DISCONNECTED }
    private val _plugEvents = MutableStateFlow<PlugEvent?>(null)
    val plugEvents: StateFlow<PlugEvent?> = _plugEvents.asStateFlow()

    // Shared between Main and IO threads; @Volatile guarantees visibility.
    @Volatile private var lastLive: LivePower = LivePower()
    @Volatile private var lastSysfs: SysfsSnapshot = SysfsSnapshot()

    init {
        // Tier-1: live battery stream, always on. Detects connect/disconnect by
        // watching the "connected" edge, and only does the heavier sysfs read on
        // a fresh connect (not on every battery tick).
        viewModelScope.launch {
            battery.flow().collect { live ->
                val wasConnected = isConnected(lastLive)
                lastLive = live
                recompute()

                val nowConnected = isConnected(live)
                if (nowConnected && !wasConnected) {
                    _plugEvents.value = PlugEvent.CONNECTED
                    refreshSysfs()                 // read deep data once, on connect
                } else if (!nowConnected && wasConnected) {
                    _plugEvents.value = PlugEvent.DISCONNECTED
                    lastSysfs = SysfsSnapshot()     // clear stale cable data
                    recompute()
                }
            }
        }
        refreshBackend()
    }

    private fun isConnected(l: LivePower): Boolean = l.plugged != 0 || l.charging

    fun consumePlugEvent() { _plugEvents.value = null }

    fun refreshBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            _backend.value = ShellAccess.probeBackend()
            refreshSysfs()
        }
    }

    fun refreshSysfs() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = sysfsReader.read(_backend.value)
            lastSysfs = snap
            recompute()
        }
    }

    fun updateWizard(answers: WizardAnswers) {
        _wizard.value = answers
        recompute()
    }

    private fun recompute() {
        val dataLink = runCatching { dataLinkReader.probe() }.getOrNull() ?: lastDataLink
        lastDataLink = dataLink
        val assessment = AssessmentEngine.assess(lastLive, lastSysfs, dataLink)
        _state.value = assessment
        _verdict.value = CapabilityResolver.resolve(assessment, _wizard.value)
    }

    @Volatile private var lastDataLink = com.fivelidz.usbcid.model.DataLinkProbe()
}
