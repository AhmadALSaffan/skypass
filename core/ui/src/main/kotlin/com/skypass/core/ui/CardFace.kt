package com.skypass.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.component.PaymentLogo
import com.skypass.core.designsystem.component.Wordmark
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.DmMono
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.PaymentMethodType

private val TealDeep = Color(0xFF0B4642)
private val Teal = Color(0xFF0F5B56)
private val TealLight = Color(0xFF17776F)
private val Mint = Color(0xFFCDEBE6)
private val ChipGold = Color(0xFFE9D8A6)
private val ChipLine = Color(0xFFC9B47A)

/**
 * A payment card in Skypass colours: the brand's teal with the board's dotted flight arc and plane
 * crossing it, the Skypass wordmark in one corner and the card network's mark in the other.
 * Used on the Add a card form (filling in as you type) and at checkout.
 *
 * [network] is null until the first digits say which network it is.
 */
@Composable
fun SkypassCardFace(
    network: PaymentMethodType?,
    number: String,
    holder: String,
    expiry: String,
    modifier: Modifier = Modifier,
) {
    val holderLabel = stringResource(R.string.card_face_holder)
    val expiresLabel = stringResource(R.string.card_face_expires)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .softShadow(SoftShadow.Brand, Shapes.hero)
            .clip(Shapes.hero)
            .background(Brush.linearGradient(listOf(TealDeep, Teal, TealLight)))
            .drawBehind {
                // A pale mint sun rising off the top edge.
                drawCircle(Mint.copy(alpha = 0.16f), radius = size.height * 0.62f, center = Offset(size.width * 0.86f, -size.height * 0.08f))
                drawCircle(Mint.copy(alpha = 0.10f), radius = size.height * 0.42f, center = Offset(size.width * 0.86f, -size.height * 0.08f))
                // The board's dotted flight arc, from the bottom-left corner up towards the logo.
                val arc = Path().apply {
                    moveTo(-size.width * 0.04f, size.height * 0.92f)
                    quadraticTo(size.width * 0.42f, size.height * 0.08f, size.width * 0.92f, size.height * 0.46f)
                }
                drawPath(
                    arc,
                    color = Color.White.copy(alpha = 0.28f),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(network?.name, number.takeLast(4).takeIf { it.all(Char::isDigit) }, holder, expiry).joinToString(", ")
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        // Plane at the end of the arc, pointing along it.
        Icon(
            SkypassIcons.Plane,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .align(BiasAlignment)
                .size(16.dp)
                .rotate(PLANE_ANGLE),
        )
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(size = 20, color = Color.White)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = network,
                transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
                label = "cardNetwork",
            ) { n ->
                if (n != null) PaymentLogo(n, width = 54.dp, height = 36.dp) else Spacer(Modifier.height(36.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip()
            Text(
                number,
                style = TextStyle(fontFamily = DmMono, fontSize = 17.sp, letterSpacing = 0.06.em),
                color = Color.White,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                CardField(holderLabel, holder.uppercase(), Modifier.weight(1f))
                CardField(expiresLabel, expiry)
            }
        }
    }
}

/** The gold contact chip, with its etched lines. */
@Composable
private fun Chip() {
    Box(
        Modifier
            .width(38.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ChipGold)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(ChipLine, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                drawLine(ChipLine, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.35f, size.height), stroke)
                drawLine(ChipLine, Offset(size.width * 0.65f, 0f), Offset(size.width * 0.65f, size.height), stroke)
            },
    )
}

@Composable
private fun CardField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = TextStyle(fontFamily = DmMono, fontSize = 9.sp, letterSpacing = 0.08.em),
            color = Mint.copy(alpha = 0.85f),
        )
        Text(
            value,
            style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** ISO/IEC 7810 ID-1: 85.60 × 53.98 mm. */
private const val CARD_RATIO = 1.586f
private const val PLANE_ANGLE = 127f
private val BiasAlignment = androidx.compose.ui.BiasAlignment(0.86f, -0.02f)
