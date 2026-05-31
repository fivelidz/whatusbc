package com.fivelidz.usbcid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fivelidz.usbcid.data.CableDatabase
import com.fivelidz.usbcid.data.KnownCable
import com.fivelidz.usbcid.model.*
import com.fivelidz.usbcid.shizuku.ShellAccess

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF171C24)
private val CardHi = Color(0xFF1E2530)
private val TextHi = Color(0xFFF2F5FA)
private val TextLo = Color(0xFF9AA4B2)
private val Accent = Color(0xFF1B6FE0)
private val Green = Color(0xFF34D399)
private val Amber = Color(0xFFFFC542)
private val Purple = Color(0xFFA78BFA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    assessment: CableAssessment,
    verdict: CapabilityVerdict,
    wizard: WizardAnswers,
    backend: ShellAccess.Backend,
    onRefresh: () -> Unit,
    onRequestShizuku: () -> Unit,
    onWizard: (WizardAnswers) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showWizard by remember { mutableStateOf(false) }
    var pinInfo by remember { mutableStateOf<PinGroup?>(null) }
    var picked by remember { mutableStateOf<KnownCable?>(null) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Header(assessment, backend, onRefresh)

            ConnectorGraphic(assessment, onPinTap = { pinInfo = it })
            Text(
                "Tap a pin group to learn what it does",
                color = TextLo, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            PinLegend()

            Spacer(Modifier.height(8.dp))

            // THE answer to the user's question: what can this cable do?
            CapabilityVerdictCard(verdict)

            // Guided identification wizard — fills the gap a phone can't probe.
            WizardButton(wizard) { showWizard = true }

            // Two headline capability cards: DATA and CHARGING
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DataCard(assessment, Modifier.weight(1f))
                ChargeCard(assessment, Modifier.weight(1f))
            }

            LivePowerCard(assessment.live)

            DataTestCard(assessment.dataLink)

            DetailCard(assessment)

            if (assessment.notes.isNotEmpty()) NotesCard(assessment.notes)

            TierCard(backend, assessment.sysfs, onRequestShizuku, onRefresh)

            PickerButton(picked) { showPicker = true }

            if (picked != null) DatabaseCard(picked!!)
        }
    }

    if (showPicker) {
        CablePickerSheet(
            onDismiss = { showPicker = false },
            onPick = { picked = it; showPicker = false }
        )
    }
    if (showWizard) {
        WizardSheet(
            initial = wizard,
            onDismiss = { showWizard = false },
            onApply = { onWizard(it); showWizard = false }
        )
    }
    pinInfo?.let { PinInfoSheet(it) { pinInfo = null } }
}

@Composable
private fun WizardButton(wizard: WizardAnswers, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        Icon(Icons.Default.Quiz, null)
        Spacer(Modifier.width(8.dp))
        Text(if (wizard.answered) "Refine identification" else "Identify my cable (guided)")
    }
}

@Composable
private fun Header(a: CableAssessment, backend: ShellAccess.Backend, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("What USB-C?", color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh", tint = TextLo)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            a.headline,
            color = if (a.connected) Green else TextLo,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        if (a.dataLink.portCeilingKnown) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .background(Purple.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Smartphone, null, tint = Purple, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "This phone's port: up to ${a.dataLink.portCeiling.short}",
                    color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PinLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendDot(Amber, "Power")
        LegendDot(Green, "CC/PD")
        LegendDot(Color(0xFF60A5FA), "USB2")
        LegendDot(Purple, "SuperSpeed")
        LegendDot(Color(0xFFF472B6), "SBU")
    }
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, color = TextLo, fontSize = 11.sp)
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun DataCard(a: CableAssessment, modifier: Modifier) {
    CardBox(modifier) {
        IconLabel(Icons.Default.Speed, "DATA", Purple)
        Spacer(Modifier.height(8.dp))
        val s = a.dataSpeed.value
        Text(
            if (s == DataSpeed.UNKNOWN) "Unknown" else s.short,
            color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (s == DataSpeed.UNKNOWN) "Not readable" else s.label,
            color = TextLo, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        ConfidenceChip(a.dataSpeed.confidence)
    }
}

@Composable
private fun ChargeCard(a: CableAssessment, modifier: Modifier) {
    CardBox(modifier) {
        IconLabel(Icons.Default.Bolt, "CHARGING", Amber)
        Spacer(Modifier.height(8.dp))
        val c = a.currentRating.value
        val big = when (c) {
            CurrentRating.UNKNOWN -> "Unknown"
            CurrentRating.EPR240 -> "240 W"
            else -> "${c.maxWattsAt20V} W"
        }
        Text(big, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(if (c == CurrentRating.UNKNOWN) "Not readable" else c.label, color = TextLo, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ConfidenceChip(a.currentRating.confidence)
    }
}

@Composable
private fun LivePowerCard(live: LivePower) {
    CardBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconLabel(Icons.Default.Power, "LIVE SESSION", Green)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Status", if (live.charging) "Charging" else "Idle")
            Stat("Source", live.pluggedType)
            Stat("Battery", "${live.batteryPct}%")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Current", if (live.currentNowUa != 0) "%.2f A".format(live.currentNowUa / 1_000_000.0) else "—")
            Stat("Batt. V", if (live.batteryVoltageMv > 0) "%.2f V".format(live.batteryVoltageMv / 1000.0) else "—")
            val w = if (live.negotiatedPowerW >= 1) live.negotiatedPowerW else live.livePowerW
            Stat("Power", if (w >= 0.5) "%.1f W".format(w) else "—")
        }
        if (live.maxChargeVoltageUv > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Charger advertises up to %.0f V / %.1f A".format(
                    live.maxChargeVoltageUv / 1_000_000.0,
                    live.maxChargeCurrentUa / 1_000_000.0
                ),
                color = TextLo, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DataTestCard(link: com.fivelidz.usbcid.model.DataLinkProbe) {
    CardBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconLabel(Icons.Default.Usb, "LIVE DATA TEST", Purple)
        Spacer(Modifier.height(10.dp))
        if (link.provenCarriesData) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Green, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Data link active — cable carries data", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            link.activeFunctions?.let {
                Text("Active functions: $it", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Text(
                "Plug this cable into a computer (with USB file-transfer / MTP enabled). " +
                    "The phone will form a data link over the cable — if it does, the cable " +
                    "carries data. This is a real functional test, no chip access needed.",
                color = TextLo, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        if (link.portCeilingKnown) {
            Spacer(Modifier.height(10.dp))
            val ceilingNote = if (link.portCeiling == com.fivelidz.usbcid.model.DataSpeed.USB2)
                "This phone's USB port maxes at USB 2.0 (480 Mbps) — the cable can't go faster here."
            else
                "This phone's port supports up to ${link.portCeiling.label} (${link.portCeiling.short})."
            Row {
                Icon(Icons.Default.PhonelinkSetup, null, tint = TextLo, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(ceilingNote, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
            }
            link.controllerName?.let {
                Text("Controller: $it", color = TextLo.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun DetailCard(a: CableAssessment) {
    CardBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconLabel(Icons.Default.Info, "DETAILS", Accent)
        Spacer(Modifier.height(10.dp))
        DetailRow("Charge protocol", a.protocol.value.label, a.protocol.confidence)
        DetailRow("eMarker chip", if (a.hasEmarker.value) "Present" else "None / unknown", a.hasEmarker.confidence)
        DetailRow("Construction", a.construction.value.label, a.construction.confidence)
        DetailRow("Other end", a.partner.value.label, a.partner.confidence)
        a.cableVendor?.let { DetailRow("Cable vendor", it, Confidence.EMARKER) }
        a.sysfs.realType?.let { DetailRow("Charger type", it, Confidence.KERNEL) }
        a.sysfs.quickChargeType?.takeIf { it != "0" && it.isNotBlank() }
            ?.let { DetailRow("Fast-charge tier", it, Confidence.KERNEL) }
        // MediaTek PD engine (root): show the raw chip dump if we got it.
        a.sysfs.mtkPeReady?.let { DetailRow("PD engine ready", it.trim(), Confidence.KERNEL) }
        a.sysfs.mtkCapsInfo?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Chip PD capabilities (mt6375):", color = TextLo, fontSize = 11.sp)
            Text(
                it.trim().take(400), color = TextHi, fontSize = 11.sp, lineHeight = 15.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun NotesCard(notes: List<String>) {
    Column(
        Modifier
            .fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(CardHi, RoundedCornerShape(16.dp)).padding(16.dp)
    ) {
        IconLabel(Icons.Default.Lightbulb, "GOOD TO KNOW", Amber)
        Spacer(Modifier.height(8.dp))
        notes.forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = TextLo, fontSize = 13.sp)
                Text(it, color = TextLo, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun TierCard(
    backend: ShellAccess.Backend,
    sysfs: SysfsSnapshot,
    onRequestShizuku: () -> Unit,
    onRefresh: () -> Unit,
) {
    CardBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconLabel(Icons.Default.Tune, "DETECTION DEPTH", Accent)
        Spacer(Modifier.height(10.dp))
        val (label, color) = when (backend) {
            ShellAccess.Backend.ROOT -> "Root access — full sysfs" to Green
            ShellAccess.Backend.SHIZUKU -> "Shizuku — shell access" to Green
            ShellAccess.Backend.NONE -> "Basic — BatteryManager only" to Amber
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(8.dp))
            Text(label, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (backend == ShellAccess.Backend.NONE) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Shizuku is a free companion app that grants shell-level access " +
                    "without root. Connecting it lets this app read the charger " +
                    "protocol, real PD voltage and — when present — the cable's eMarker.",
                color = TextLo, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Note: some phones (e.g. Xiaomi HyperOS) block these system files " +
                    "even from Shizuku — there only root unlocks the deeper data. " +
                    "Everything below the connector still works without it.",
                color = TextLo.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequestShizuku,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Connect Shizuku") }
        }
        if (sysfs.available && sysfs.rawDump.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("${sysfs.rawDump.size} sysfs nodes read via ${sysfs.source}", color = TextLo, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PickerButton(picked: KnownCable?, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi)
    ) {
        Icon(Icons.Default.MenuBook, null, tint = TextLo)
        Spacer(Modifier.width(8.dp))
        Text(if (picked == null) "Identify by cable model (database)" else "Picked: ${picked.name}")
    }
}

@Composable
private fun DatabaseCard(c: KnownCable) {
    CardBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconLabel(Icons.Default.MenuBook, "DATABASE MATCH", Purple)
        Spacer(Modifier.height(8.dp))
        Text("${c.maker} · ${c.name}", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        DetailRow("Data", "${c.speed.label} (${c.speed.short})", Confidence.DATABASE)
        DetailRow("Charging", c.current.label, Confidence.DATABASE)
        DetailRow("Construction", c.construction.label, Confidence.DATABASE)
        DetailRow("eMarker", if (c.hasEmarker) "Yes" else "No", Confidence.DATABASE)
        Spacer(Modifier.height(8.dp))
        Text(c.notes, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CablePickerSheet(onDismiss: () -> Unit, onPick: (KnownCable) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { CableDatabase.search(query) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card) {
        Column(Modifier.padding(16.dp).heightIn(max = 560.dp)) {
            Text("Pick your cable", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search brand, speed, watts…", color = TextLo) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                    focusedBorderColor = Accent, unfocusedBorderColor = CardHi,
                )
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                results.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(CardHi, RoundedCornerShape(12.dp))
                            .clickable { onPick(c) }
                            .padding(12.dp)
                    ) {
                        Text("${c.maker} · ${c.name}", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${c.speed.short} · ${c.current.label}", color = TextLo, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---- small helpers ----

@Composable
private fun IconLabel(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextLo, fontSize = 11.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String, conf: Confidence) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        ConfidenceChip(conf, small = true)
    }
}

@Composable
private fun ConfidenceChip(conf: Confidence, small: Boolean = false) {
    if (conf == Confidence.NONE) return
    val color = when (conf) {
        Confidence.EMARKER -> Green
        Confidence.KERNEL -> Accent
        Confidence.MEASURED -> Amber
        Confidence.DATABASE -> Purple
        Confidence.INFERRED -> TextLo
        Confidence.NONE -> TextLo
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(conf.label, color = color, fontSize = if (small) 9.sp else 10.sp)
    }
}


