package com.fivelidz.usbcid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF171C24)
private val TextHi = Color(0xFFF2F5FA)
private val TextLo = Color(0xFF9AA4B2)
private val Accent = Color(0xFF1B6FE0)

private data class Page(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val pages = listOf(
    Page(
        Icons.Default.Cable,
        "What can this cable do?",
        "Not all USB-C cables are equal. This app helps you work out whether a cable " +
            "can carry data, video, audio, and how much power — across five clear axes."
    ),
    Page(
        Icons.Default.Bolt,
        "What your phone can read live",
        "With nothing to set up, the app reads the live charging session — current, " +
            "voltage and watts — to show what's flowing right now through the cable."
    ),
    Page(
        Icons.Default.Lock,
        "Why we can't just 'read' a cable",
        "A cable's electronic ID (the eMarker) lives on the USB-C control wires and is " +
            "handled by a dedicated chip + the kernel. There's no app access to it on most " +
            "phones, and passive cables have no chip at all. That's physics, not a bug."
    ),
    Page(
        Icons.Default.Usb,
        "Live data test: make the cable prove it",
        "Plug the cable into a computer. The phone forms a real data link over it — if it " +
            "does, the cable carries data, and the negotiated speed proves how fast. A real " +
            "functional test, no chip access or charger needed."
    ),
    Page(
        Icons.Default.Quiz,
        "Or tell us what you can see",
        "Tap \"Identify my cable\" for a quick guided wizard: read the SS/10/40 markings, " +
            "any Thunderbolt/USB4 logo, whether video has ever worked. We turn that into a " +
            "confident verdict."
    ),
    Page(
        Icons.Default.TouchApp,
        "Explore the connector",
        "Tap any pin group on the USB-C graphic to learn what it does and what it tells " +
            "you about the cable. Every result shows a confidence label so you always know " +
            "how sure we are."
    ),
)

@Composable
fun OnboardingOverlay(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val p = pages[page]
    val isLast = page == pages.lastIndex

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text("Skip", color = TextLo) }
            }
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.size(96.dp).background(Accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(p.icon, null, tint = Accent, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                p.title, color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                p.body, color = TextLo, fontSize = 15.sp, lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 10.dp else 7.dp)
                            .background(
                                if (i == page) Accent else TextLo.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (isLast) onFinish() else page++ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (isLast) "Get started" else "Next", fontSize = 16.sp)
                if (!isLast) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}
