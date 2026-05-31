package com.fivelidz.usbcid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fivelidz.usbcid.model.*

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF171C24)
private val CardHi = Color(0xFF1E2530)
private val TextHi = Color(0xFFF2F5FA)
private val TextLo = Color(0xFF9AA4B2)
private val Accent = Color(0xFF1B6FE0)

private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.YES -> Color(0xFF34D399)
    Verdict.LIKELY -> Color(0xFF6EE7B7)
    Verdict.MAYBE -> Color(0xFFFFC542)
    Verdict.UNLIKELY -> Color(0xFFF59E0B)
    Verdict.NO -> Color(0xFFF87171)
    Verdict.UNKNOWN -> Color(0xFF6B7280)
}

private fun axisIcon(name: String): ImageVector = when (name) {
    "Data" -> Icons.Default.SwapVert
    "Speed" -> Icons.Default.Speed
    "Video" -> Icons.Default.Videocam
    "Audio" -> Icons.Default.VolumeUp
    "Charging" -> Icons.Default.Bolt
    else -> Icons.Default.Info
}

/** The hero "What can this cable do?" card — the answer to the user's real question. */
@Composable
fun CapabilityVerdictCard(verdict: CapabilityVerdict, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Checklist, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "WHAT THIS CABLE CAN DO", color = TextLo, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        val axes = verdict.axes
        axes.forEachIndexed { i, axis ->
            CapabilityRow(axis)
            if (i < axes.lastIndex) {
                HorizontalDivider(color = CardHi, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun CapabilityRow(axis: CapabilityAxis) {
    var expanded by remember { mutableStateOf(false) }
    val vc = verdictColor(axis.verdict)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(axisIcon(axis.name), null, tint = TextLo, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(axis.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(axis.detail, color = TextLo, fontSize = 12.sp)
            }
            // verdict pill
            Box(
                Modifier
                    .background(vc.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(axis.verdict.label, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = TextLo, modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 32.dp, top = 6.dp)) {
                Text(axis.explanation, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
                if (axis.confidence != Confidence.NONE) {
                    Spacer(Modifier.height(4.dp))
                    Text(axis.confidence.label, color = vc, fontSize = 10.sp)
                }
            }
        }
    }
}

/** Guided identification wizard bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardSheet(
    initial: WizardAnswers,
    onDismiss: () -> Unit,
    onApply: (WizardAnswers) -> Unit,
) {
    var answers by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Identify your cable", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "A phone can't probe a cable's pins electrically — but you can read it. " +
                    "Answer what you can; we'll work out the rest.",
                color = TextLo, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            Question("Any speed marking printed on the connector?")
            CableMarking.entries.forEach { m ->
                ChoiceRow(m.label, m.hint, answers.marking == m) {
                    answers = answers.copy(marking = m)
                }
            }

            Spacer(Modifier.height(16.dp))
            Question("Any brand logo?")
            BrandLogo.entries.forEach { b ->
                ChoiceRow(b.label, "", answers.brandLogo == b) {
                    answers = answers.copy(brandLogo = b)
                }
            }

            Spacer(Modifier.height(16.dp))
            Question("Have you ever gotten video out of it (to a monitor/TV)?")
            TriRow(answers.videoWorks) { answers = answers.copy(videoWorks = it) }

            Spacer(Modifier.height(16.dp))
            Question("What did this cable come with?")
            CameWith.entries.forEach { c ->
                ChoiceRow(c.label, "", answers.whatCameWith == c) {
                    answers = answers.copy(whatCameWith = c)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onApply(answers) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChoiceRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (selected) Accent.copy(alpha = 0.18f) else CardHi, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            null, tint = if (selected) Accent else TextLo, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextHi, fontSize = 13.sp)
            if (hint.isNotBlank()) Text(hint, color = TextLo, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TriRow(value: TriState, onSelect: (TriState) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            TriState.YES to "Yes",
            TriState.NO to "No",
            TriState.UNKNOWN to "Not sure",
        ).forEach { (state, label) ->
            val sel = value == state
            Box(
                Modifier
                    .weight(1f)
                    .background(if (sel) Accent.copy(alpha = 0.2f) else CardHi, RoundedCornerShape(10.dp))
                    .clickable { onSelect(state) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (sel) Accent else TextHi, fontSize = 13.sp)
            }
        }
    }
}

/** Bottom-sheet explaining a tapped pin group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinInfoSheet(group: PinGroup, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text(group.display, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(group.pins, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(group.purpose, color = TextHi, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                Icon(Icons.Outlined.Lightbulb, null, tint = Color(0xFFFFC542), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(group.tells, color = TextLo, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}
