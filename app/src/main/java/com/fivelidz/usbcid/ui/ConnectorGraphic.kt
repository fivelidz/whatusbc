package com.fivelidz.usbcid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.fivelidz.usbcid.model.CableAssessment
import com.fivelidz.usbcid.model.DataSpeed
import com.fivelidz.usbcid.model.PinGroup

/**
 * Draws a stylised USB-C plug with its 24-pin row, lighting up the pin groups
 * that are in use for the current assessment:
 *   - VBUS / GND  (power, always)
 *   - CC          (configuration / PD)
 *   - D+/D-       (USB 2.0 data)
 *   - SuperSpeed TX/RX pairs (USB 3 / USB4)
 *   - SBU         (alt-mode sideband)
 */
@Composable
fun ConnectorGraphic(
    assessment: CableAssessment,
    modifier: Modifier = Modifier,
    onPinTap: (PinGroup) -> Unit = {},
) {
    val speed = assessment.dataSpeed.value
    val hasSuperSpeed = speed in setOf(
        DataSpeed.USB3_GEN1, DataSpeed.USB3_GEN2, DataSpeed.USB3_GEN2X2,
        DataSpeed.USB4_GEN2, DataSpeed.USB4_GEN3, DataSpeed.USB4_V2,
        DataSpeed.TB3, DataSpeed.TB4, DataSpeed.TB5,
    )
    // SBU (AUX) is wired in any SuperSpeed cable that could carry DP Alt Mode.
    val hasSbu = hasSuperSpeed
    val pdActive = assessment.protocol.value.name.startsWith("PD") ||
        assessment.sysfs.pdActive == true
    val powered = assessment.live.charging

    Box(modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        pinGroupAt(offset.x, size.width.toFloat())?.let(onPinTap)
                    }
                }
        ) {
            drawConnector(powered, pdActive, hasSuperSpeed, hasSbu)
        }
    }
}

/**
 * Map a horizontal tap position to the pin group it falls on, mirroring the
 * pin layout used in [drawConnector]. Rough zones across the plug width.
 */
private fun pinGroupAt(x: Float, width: Float): PinGroup? {
    val frac = x / width
    return when {
        frac < 0.16f || frac > 0.84f -> PinGroup.POWER       // GND/VBUS at the ends
        frac in 0.16f..0.30f || frac in 0.70f..0.84f -> PinGroup.SUPERSPEED
        frac in 0.30f..0.40f || frac in 0.60f..0.70f -> PinGroup.POWER // VBUS block
        frac in 0.40f..0.46f -> PinGroup.CC
        frac in 0.46f..0.56f -> PinGroup.USB2
        frac in 0.56f..0.60f -> PinGroup.SBU
        else -> PinGroup.CC
    }
}

private val ON_POWER = Color(0xFFFFC542)
private val ON_CC = Color(0xFF34D399)
private val ON_DATA = Color(0xFF60A5FA)
private val ON_SS = Color(0xFFA78BFA)
private val ON_SBU = Color(0xFFF472B6)
private val OFF = Color(0xFF2A2F3A)
private val SHELL = Color(0xFF3A4150)

private fun DrawScope.drawConnector(
    powered: Boolean,
    pd: Boolean,
    ss: Boolean,
    sbu: Boolean,
) {
    val w = size.width
    val h = size.height
    val plugW = w * 0.86f
    val plugH = h * 0.42f
    val left = (w - plugW) / 2f
    val top = h * 0.20f

    // Outer plug shell (oval-ish rounded rect)
    drawRoundRect(
        color = SHELL,
        topLeft = Offset(left, top),
        size = Size(plugW, plugH),
        cornerRadius = CornerRadius(plugH / 2f, plugH / 2f),
        style = Stroke(width = 6f),
    )
    drawRoundRect(
        color = Color(0xFF161A21),
        topLeft = Offset(left + 8, top + 8),
        size = Size(plugW - 16, plugH - 16),
        cornerRadius = CornerRadius(plugH / 2.4f, plugH / 2.4f),
    )

    // 12 pins per row, two rows (top/bottom of tongue). Group definitions per the
    // USB-C pinout (A-side): GND, TX1+, TX1-, VBUS, CC1, D+, D-, SBU1, VBUS, RX2-, RX2+, GND
    data class Pin(val role: String)
    val rowA = listOf("GND","SS","SS","VBUS","CC","D","D","SBU","VBUS","SS","SS","GND")

    fun colorFor(role: String): Color = when (role) {
        "VBUS","GND" -> if (powered) ON_POWER else OFF
        "CC" -> if (pd) ON_CC else (if (powered) ON_CC.copy(alpha = 0.5f) else OFF)
        "D" -> if (powered) ON_DATA else OFF
        "SS" -> if (ss) ON_SS else OFF
        "SBU" -> if (sbu) ON_SBU else OFF
        else -> OFF
    }

    val pinAreaLeft = left + plugW * 0.10f
    val pinAreaW = plugW * 0.80f
    val n = rowA.size
    val gap = pinAreaW / n
    val pinW = gap * 0.55f
    val pinH = plugH * 0.30f
    val rowTopY = top + plugH * 0.20f
    val rowBotY = top + plugH * 0.50f

    for (i in 0 until n) {
        val x = pinAreaLeft + gap * i + (gap - pinW) / 2f
        // top row
        drawRoundRect(
            color = colorFor(rowA[i]),
            topLeft = Offset(x, rowTopY),
            size = Size(pinW, pinH),
            cornerRadius = CornerRadius(2f, 2f),
        )
        // bottom row (rotationally mirrored -> reverse the role list)
        drawRoundRect(
            color = colorFor(rowA[n - 1 - i]),
            topLeft = Offset(x, rowBotY),
            size = Size(pinW, pinH),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }

    // cable stub
    val stubW = plugW * 0.16f
    drawRoundRect(
        color = SHELL,
        topLeft = Offset((w - stubW) / 2f, top + plugH - 2),
        size = Size(stubW, h * 0.30f),
        cornerRadius = CornerRadius(8f, 8f),
    )
}
