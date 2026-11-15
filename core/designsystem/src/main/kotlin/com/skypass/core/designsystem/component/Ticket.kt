package com.skypass.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.SkypassTheme

/**
 * The ticket "tear line": two half-circle notches in [notchColor] (the colour behind the card)
 * biting into the card edges, with a dashed line between them. [progress] 0→1 grows the notches
 * and draws the dashes from the centre outwards (used by the onboarding "tear" animation).
 */
@Composable
fun Perforation(
    notchColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    notchRadius: Dp = height / 2,
    dashColor: Color = SkypassTheme.colors.perforation,
    progress: Float = 1f,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = notchRadius.toPx() * progress.coerceIn(0f, 1f)
        val cy = size.height / 2
        drawCircle(notchColor, r, Offset(0f, cy))
        drawCircle(notchColor, r, Offset(size.width, cy))
        val inset = notchRadius.toPx() + 6.dp.toPx()
        val half = (size.width / 2 - inset) * progress.coerceIn(0f, 1f)
        if (half > 0f) {
            drawLine(
                color = dashColor,
                start = Offset(size.width / 2 - half, cy),
                end = Offset(size.width / 2 + half, cy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
        }
    }
}

/**
 * Route line between two airports: hollow dot, dashes, plane, dashes, filled dot.
 * [progress] 0→1 draws it left to right with the plane riding the tip.
 */
@Composable
fun DashedRoute(
    modifier: Modifier = Modifier,
    color: Color = SkypassTheme.colors.ink,
    dashColor: Color = SkypassTheme.colors.borderStrong,
    progress: Float = 1f,
    showPlane: Boolean = true,
    planeBackground: Color = SkypassTheme.colors.surface,
) {
    Box(modifier.height(18.dp), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val cy = size.height / 2
            val dot = 3.5.dp.toPx()
            val p = progress.coerceIn(0f, 1f)
            drawCircle(color, dot, Offset(dot, cy), style = Stroke(1.5.dp.toPx()))
            val endX = dot * 2 + (size.width - dot * 4) * p
            drawLine(
                color = dashColor,
                start = Offset(dot * 2 + 2.dp.toPx(), cy),
                end = Offset(endX, cy),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            if (p >= 1f) drawCircle(color, dot, Offset(size.width - dot, cy))
        }
        if (showPlane) {
            val bias = (progress.coerceIn(0f, 1f) * 2f - 1f).let { if (progress >= 1f) 0f else it }
            Icon(
                SkypassIcons.Plane,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .align(androidx.compose.ui.BiasAlignment(bias, 0f))
                    .background(planeBackground, CircleShape)
                    .size(16.dp)
                    .rotate(90f),
            )
        }
    }
}

/**
 * Decorative barcode (board pattern: bars of 2/1/3 px with gaps). [scan] 0→1 sweeps a teal
 * line across, used when a pass "checks in".
 */
@Composable
fun Barcode(
    modifier: Modifier = Modifier,
    color: Color = SkypassTheme.colors.ink,
    scan: Float = 0f,
) {
    val pattern = remember { intArrayOf(2, 1, 1, 3, 3, 1, 1, 3, 2, 1, 1, 2, 3, 2, 1, 1, 2, 3, 1, 1) }
    val scanColor = SkypassTheme.colors.brand
    Canvas(modifier.clearAndSetSemantics { }) {
        val unit = 1.dp.toPx()
        var x = 0f
        var i = 0
        var bar = true
        while (x < size.width) {
            val w = pattern[i % pattern.size] * unit
            if (bar) drawRect(color, Offset(x, 0f), androidx.compose.ui.geometry.Size(w.coerceAtMost(size.width - x), size.height))
            x += w
            bar = !bar
            i++
        }
        if (scan in 0.001f..0.999f) {
            val sx = size.width * scan
            drawLine(scanColor, Offset(sx, -2.dp.toPx()), Offset(sx, size.height + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** A real, scannable QR code of [content] (zxing), drawn crisp in [color]. */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    color: Color = SkypassTheme.colors.ink,
) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier.size(size).semantics { contentDescription = "QR code for $content" }) {
        val n = matrix.width
        val cell = this.size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) {
                drawRect(
                    color,
                    Offset(x * cell, y * cell),
                    androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

/**
 * Airline mark. Air New Zealand keeps the board's koru in a bordered white circle. Everyone else shows
 * their square logo in a circle; the coloured monogram sits underneath while it loads, or if it fails.
 */
@Composable
fun AirlineLogo(
    code: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    logoUrl: String? = null,
) {
    if (code == "NZ") {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, SkypassTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { AnzKoru(size = size * 0.6f) }
        return
    }
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (loaded) Color.White else brandColor)
            .border(1.dp, if (loaded) SkypassTheme.colors.border else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                code,
                style = SkypassTheme.typography.labelSm.copy(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.32f).sp),
                color = Color.White,
            )
        }
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.size(size * 0.78f).clip(CircleShape),
            )
        }
    }
}
