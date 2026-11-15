package com.skypass.feature.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.AnzKoru
import com.skypass.core.designsystem.component.AnzWordmark
import com.skypass.core.designsystem.component.Perforation
import com.skypass.core.designsystem.component.QrCode
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.DmMono
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.model.BoardingPass
import java.time.format.DateTimeFormatter

// The pass is paper: white with navy ink in light and dark mode alike.
internal val PassInk = Color(0xFF1C2550)
internal val PassMuted = Color(0xFF5B6386)
private val PassPerforation = Color(0xFFD7DAE6)
private val PassShape = RoundedCornerShape(26.dp)
private val Clock = DateTimeFormatter.ofPattern("HH:mm")
private val Day = DateTimeFormatter.ofPattern("d MMM")

/** The board's boarding pass (screen 07): airline, route, passenger grid, tear line, QR and PNR. */
@Composable
internal fun BoardingPassCard(pass: BoardingPass, notchColor: Color, modifier: Modifier = Modifier) {
    val description = "Boarding pass for ${pass.passengerName}, ${pass.airlineName} ${pass.flightNumber}, " +
        "${pass.originCity} to ${pass.destinationCity}, ${pass.departure.format(Day)}, boards ${pass.boardingTime.format(Clock)}, " +
        "gate ${pass.gate}, seat ${pass.seat}, ${pass.zone}. Booking ${pass.pnr}."
    Column(
        modifier
            .fillMaxWidth()
            .clip(PassShape)
            .background(Color.White)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pass.airlineCode == "NZ") {
                AnzWordmark(height = 12.dp, tint = PassInk)
                Spacer(Modifier.width(8.dp))
                AnzKoru(size = 22.dp, tint = PassInk)
            } else {
                AirlineLogo(pass.airlineCode, Color(pass.airlineColor), size = 30.dp, logoUrl = pass.airlineLogoUrl)
                Spacer(Modifier.width(8.dp))
                Text(pass.airlineName, style = text(14, FontWeight.Bold), color = PassInk, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text("BOARDING PASS", style = mono(10, 0.08f), color = PassMuted)
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(pass.originCode, style = text(34, FontWeight.ExtraBold, -0.02f), color = PassInk)
                Text(pass.originCity, style = text(12, FontWeight.Normal), color = PassMuted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(SkypassIcons.Plane, contentDescription = null, tint = PassInk, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f })
                Text("${pass.durationMinutes / 60}h ${pass.durationMinutes % 60}m", style = mono(11), color = PassMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(pass.destinationCode, style = text(34, FontWeight.ExtraBold, -0.02f), color = PassInk)
                Text(pass.destinationCity, style = text(12, FontWeight.Normal), color = PassMuted)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Field("Passenger", pass.passengerName, Modifier.weight(2f))
                Field("Class", pass.cabin.label, Modifier.weight(1f))
            }
            Row {
                Field("Flight", pass.flightNumber, Modifier.weight(1f))
                Field("Date", pass.departure.format(Day), Modifier.weight(1f))
                Field("Boards", pass.boardingTime.format(Clock), Modifier.weight(1f))
            }
            Row {
                Field("Gate", pass.gate, Modifier.weight(1f))
                Field("Seat", pass.seat, Modifier.weight(1f))
                Field("Zone", pass.zone, Modifier.weight(1f))
            }
        }

        Perforation(notchColor = notchColor, height = 28.dp, notchRadius = 11.dp, dashColor = PassPerforation, modifier = Modifier.padding(vertical = 8.dp))

        Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            QrCode("${pass.pnr}|${pass.flightNumber}|${pass.departure.toLocalDate()}|${pass.seat}|${pass.passengerName}", size = 128.dp, color = PassInk)
            Spacer(Modifier.height(10.dp))
            Text(pass.pnr, style = mono(13, 0.2f), color = PassInk)
            Text("Show this code at security and the gate", style = text(12, FontWeight.Normal), color = PassMuted)
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = mono(9, 0.08f), color = PassMuted)
        Text(value, style = text(14, FontWeight.Bold), color = PassInk, maxLines = 1)
    }
}

private fun text(size: Int, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(fontFamily = JakartaSans, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.15f).sp, letterSpacing = tracking.em)

private fun mono(size: Int, tracking: Float = 0f) = TextStyle(fontFamily = DmMono, fontSize = size.sp, letterSpacing = tracking.em)
