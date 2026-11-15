package com.skypass.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.appear
import com.skypass.core.designsystem.theme.pop
import com.skypass.core.designsystem.theme.rememberTimeline
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.designsystem.theme.typed

/*
 * The three onboarding "motion videos". Like Decivio, they are not video files: each scene is
 * live Compose UI driven by one millisecond clock (rememberTimeline). Every element compares the
 * clock with its own cue and springs into place, so the choreography is exact and the motion still
 * feels physical. Scenes replay each time their slide comes back, show the final frame in
 * previews, and jump to it when the phone's animations are off.
 *
 * Beyond Decivio: layers move at different speeds while you swipe (parallax from the pager
 * offset) and the hero colour blends continuously between slides.
 */

/**
 * The stage, in the board's 327×350 coordinates. There is no card behind it: the scene plays
 * straight on the app background, so it feels like the app itself is moving.
 */
@Composable
private fun Stage(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = description },
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// 01 · Find flights that fit your plans — JFK → CDG on mint.
// The route draws itself with a plane riding the tip, the codes type in, the pass drops onto
// the table, its route draws, gate and seat print, the barcode scans, and a price sticker pops.
// ---------------------------------------------------------------------------------------------

private object S1 {
    const val DURATION = 3600
    const val ARC_START = 100
    const val ARC_LENGTH = 1000
    const val LANDED = 1100
    const val GHOST = 650
    const val PASS = 900
    const val CODE_FROM = 1300
    const val CODE_TO = 1450
    const val ROUTE = 1550
    const val GATE = 2100
    const val SEAT = 2300
    const val SCAN = 2550
    const val STICKER = 3050
}

@Composable
internal fun SearchScene(
    active: Boolean,
    accent: Color,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ink = SkypassTheme.colors.ink
    val stage = SkypassTheme.colors.bg
    val t = rememberTimeline(active, S1.DURATION)
    val arc = eased(t, S1.ARC_START, S1.ARC_LENGTH)
    val planeAlpha = if (t < S1.LANDED) 1f else 1f - linear(t, S1.LANDED, 220)
    val ghost = appear(t >= S1.GHOST)
    val pass = pop(t >= S1.PASS)
    val sticker = pop(t >= S1.STICKER)

    Stage(stringResource(R.string.onboarding_1_visual), modifier) {
        FlightArc(
            draw = arc,
            planeAlpha = planeAlpha,
            startDot = pop(t > 30f),
            endRing = pop(t >= S1.LANDED),
            ink = ink,
            modifier = Modifier.parallax(pageOffset, 0.10f),
        )
        ArcLabel(typed("JFK", t, 150, 70), color = ink, modifier = Modifier.at(26, 312).parallax(pageOffset, 0.10f))
        ArcLabel(typed("CDG", t, S1.LANDED, 70), color = ink, modifier = Modifier.at(268, 24).parallax(pageOffset, 0.10f))

        GhostCard(
            color = accent,
            modifier = Modifier
                .at(48, 92)
                .parallax(pageOffset, 0.22f)
                .graphicsLayer {
                    alpha = ghost
                    rotationZ = 7f
                    translationY = (1f - ghost) * 24.dp.toPx()
                },
        )
        PaperPass(
            from = typed("JFK", t, S1.CODE_FROM, 80),
            to = typed("CDG", t, S1.CODE_TO, 80),
            notchColor = stage,
            gate = typed("A12", t, S1.GATE, 70),
            seat = typed("14C", t, S1.SEAT, 70),
            route = eased(t, S1.ROUTE, 600),
            scan = linear(t, S1.SCAN, 650),
            modifier = Modifier
                .at(36, 100)
                .parallax(pageOffset, 0.34f)
                .graphicsLayer {
                    alpha = pass.coerceIn(0f, 1f)
                    rotationZ = lerp(7f, -5f, pass)
                    translationY = (1f - pass) * -44.dp.toPx()
                    val s = lerp(0.9f, 1f, pass)
                    scaleX = s
                    scaleY = s
                },
        )
        Sticker(
            text = "from $612",
            modifier = Modifier
                .at(204, 54)
                .parallax(pageOffset, 0.46f)
                .graphicsLayer {
                    alpha = sticker.coerceIn(0f, 1f)
                    rotationZ = 8f
                    val s = lerp(0.4f, 1f, sticker)
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 02 · Every result is a boarding pass — DXB → HND on lavender.
// A plain search result slides up with its price counting, then unfolds into a full pass: the
// perforation tears open, gate and seat print, the barcode scans and "Best value" is stamped.
// ---------------------------------------------------------------------------------------------

private object S2 {
    const val DURATION = 3800
    const val ROW = 150
    const val PRICE = 350
    const val UNFOLD = 1150
    const val TEAR = 1300
    const val GATE = 1750
    const val SEAT = 1950
    const val SCAN = 2350
    const val GHOST = 1500
    const val STAMP = 2950
}

@Composable
internal fun ResultScene(
    active: Boolean,
    accent: Color,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ink = SkypassTheme.colors.ink
    val stage = SkypassTheme.colors.bg
    val t = rememberTimeline(active, S2.DURATION)
    val row = appear(t >= S2.ROW)
    val unfold = appear(t >= S2.UNFOLD)
    val ghost = appear(t >= S2.GHOST)
    val stamp = pop(t >= S2.STAMP)
    val price = (845 * eased(t, S2.PRICE, 1100)).toInt()

    Stage(stringResource(R.string.onboarding_2_visual), modifier) {
        FlightArc(
            draw = eased(t, 0, 700),
            endRing = pop(t >= 700f),
            ink = ink,
            modifier = Modifier.parallax(pageOffset, 0.10f),
        )
        ArcLabel(typed("DXB", t, 80, 60), color = ink, modifier = Modifier.at(26, 312).parallax(pageOffset, 0.10f))
        ArcLabel(typed("HND", t, 700, 60), color = ink, modifier = Modifier.at(268, 24).parallax(pageOffset, 0.10f))

        GhostCard(
            color = accent,
            modifier = Modifier
                .at(48, 104)
                .parallax(pageOffset, 0.22f)
                .graphicsLayer {
                    alpha = ghost
                    rotationZ = 6f
                    translationY = (1f - ghost) * 24.dp.toPx()
                },
        )
        PaperPass(
            from = "DXB",
            to = "HND",
            notchColor = stage,
            price = "$$price",
            gate = typed("B22", t, S2.GATE, 70),
            seat = typed("14C", t, S2.SEAT, 70),
            route = eased(t, S2.ROW + 200, 700),
            unfold = unfold,
            perforation = eased(t, S2.TEAR, 500),
            scan = linear(t, S2.SCAN, 650),
            modifier = Modifier
                .at(36, 100)
                .parallax(pageOffset, 0.34f)
                .graphicsLayer {
                    alpha = row.coerceIn(0f, 1f)
                    translationY = (1f - row) * 40.dp.toPx()
                    rotationZ = -4f * unfold
                },
        )
        Stamp(
            modifier = Modifier
                .at(46, 74)
                .parallax(pageOffset, 0.46f)
                .graphicsLayer {
                    alpha = stamp.coerceIn(0f, 1f)
                    rotationZ = -7f
                    val s = lerp(1.6f, 1f, stamp)
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 03 · Your trips stay in your pocket — LHR → JFK on peach.
// The pass slides into a phone, an "Available offline" chip pops, then a live gate alert drops
// in and the gate on the pass flips from B22 to B24 with a teal flash.
// ---------------------------------------------------------------------------------------------

private object S3 {
    const val DURATION = 4400
    const val PHONE = 0
    const val PASS = 350
    const val OFFLINE = 1250
    const val ALERT = 1900
    const val FLIP = 2600
    const val FLASH_END = 3500
}

@Composable
internal fun PocketScene(
    active: Boolean,
    accent: Color,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ink = SkypassTheme.colors.ink
    val t = rememberTimeline(active, S3.DURATION)
    val phone = appear(t >= S3.PHONE + 20)
    val pass = appear(t >= S3.PASS)
    val offline = pop(t >= S3.OFFLINE)
    val alert = appear(t >= S3.ALERT)
    val flash = pop(t >= S3.FLIP) * (1f - linear(t, S3.FLASH_END, 700))

    Stage(stringResource(R.string.onboarding_3_visual), modifier) {
        // Phone outline, centred in the hero.
        Box(
            Modifier
                .at(78, 34)
                .parallax(pageOffset, 0.16f)
                .graphicsLayer {
                    alpha = phone
                    translationY = (1f - phone) * 30.dp.toPx()
                }
                .size(170.dp, 300.dp)
                .border(3.dp, ink, RoundedCornerShape(30.dp))
                .padding(3.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(accent),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 9.dp)
                    .size(46.dp, 12.dp)
                    .background(ink, CircleShape),
            )
            // A quiet trip list under the saved pass.
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(124, 96).forEach { w ->
                    Box(Modifier.size(w.dp, 10.dp).background(PaperInk.copy(alpha = 0.10f), CircleShape))
                }
            }
        }
        // The pass, slid in and shrunk to fit the phone screen.
        PaperPass(
            from = "LHR",
            to = "JFK",
            // Notches bite into the phone screen behind the pass.
            notchColor = accent,
            gate = if (t >= S3.FLIP) "B24" else "B22",
            seat = "14C",
            gateHighlight = flash,
            shadow = false,
            modifier = Modifier
                .at(36, 104)
                .parallax(pageOffset, 0.30f)
                .graphicsLayer {
                    alpha = pass.coerceIn(0f, 1f)
                    translationX = (1f - pass) * -190.dp.toPx()
                    rotationZ = (1f - pass) * -10f
                    scaleX = 0.62f
                    scaleY = 0.62f
                },
        )
        OfflineChip(
            Modifier
                .at(24, 250)
                .parallax(pageOffset, 0.40f)
                .graphicsLayer {
                    alpha = offline.coerceIn(0f, 1f)
                    val s = lerp(0.5f, 1f, offline)
                    scaleX = s
                    scaleY = s
                },
        )
        GateAlert(
            Modifier
                .at(26, 16)
                .parallax(pageOffset, 0.46f)
                .graphicsLayer {
                    alpha = alert.coerceIn(0f, 1f)
                    translationY = (1f - alert) * -70.dp.toPx()
                },
        )
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun Sticker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            // Brand teal reads on the white pass, the light canvas and the dark canvas alike.
            .softShadow(SoftShadow.Low, RoundedCornerShape(50))
            .background(PaperTeal, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
        color = Color.White,
    )
}

@Composable
private fun Stamp(modifier: Modifier = Modifier) {
    Text(
        text = "Best value",
        modifier = modifier
            .softShadow(SoftShadow.Low, RoundedCornerShape(50))
            .background(PaperMint, RoundedCornerShape(50))
            .border(1.5.dp, PaperTeal.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 12.sp),
        color = Color(0xFF0B4642),
    )
}

@Composable
private fun OfflineChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .softShadow(SoftShadow.Low, RoundedCornerShape(50))
            .background(Color.White, RoundedCornerShape(50))
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = PaperTeal, modifier = Modifier.size(16.dp))
        Text(
            "Available offline",
            style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = PaperInk,
        )
    }
}

@Composable
private fun GateAlert(modifier: Modifier = Modifier) {
    Row(
        modifier
            .width(275.dp)
            .softShadow(SoftShadow.Card, RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).background(PaperTeal, CircleShape), contentAlignment = Alignment.Center) {
            Icon(SkypassIcons.Bell, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Gate change · NZ 2",
                style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
                color = PaperInk,
            )
            Text(
                "Now boarding at gate B24",
                style = TextStyle(fontFamily = JakartaSans, fontSize = 11.sp),
                color = PaperInk2,
            )
        }
        Text("now", style = monoStyle(10), color = PaperMuted)
    }
}
