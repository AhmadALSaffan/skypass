package com.skypass.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.R
import com.skypass.core.model.PaymentMethodType

/** The network's own mark, as the brand publishes it. */
@DrawableRes
fun paymentLogoRes(type: PaymentMethodType): Int = when (type) {
    PaymentMethodType.Visa -> R.drawable.logo_visa
    PaymentMethodType.Mastercard -> R.drawable.logo_mastercard
    PaymentMethodType.Amex -> R.drawable.logo_amex
    PaymentMethodType.Discover -> R.drawable.logo_discover
    PaymentMethodType.GooglePay -> R.drawable.logo_google_pay
}

/** How much of the tile each mark fills: wordmarks need room, the Amex box fills it edge to edge. */
private fun inset(type: PaymentMethodType): Dp = when (type) {
    PaymentMethodType.Amex -> 0.dp
    PaymentMethodType.Mastercard -> 4.dp
    PaymentMethodType.GooglePay -> 5.dp
    else -> 6.dp
}

/**
 * A payment network's logo on a small white card, the way wallets show them. The tile stays white
 * in dark mode: the marks are designed for a light background (Discover and Google Pay use dark
 * text), and their colours must not be changed.
 */
@Composable
fun PaymentLogo(
    type: PaymentMethodType,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    height: Dp = 32.dp,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(height * 0.22f)
    val amex = type == PaymentMethodType.Amex
    // The Google Pay lockup is wide (about 5.6 : 1); give it a wider card so it stays legible.
    val tileWidth = if (type == PaymentMethodType.GooglePay) maxOf(width, height * 2.6f) else width
    Box(
        modifier
            .size(tileWidth, height)
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(shape)
            // The Amex logo is its own blue box; it fills the tile instead of floating on white.
            .background(if (amex) Color(0xFF016FD0) else Color.White)
            .border(1.dp, Color(0x1F1C2550), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(paymentLogoRes(type)),
            contentDescription = null,
            contentScale = if (amex) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(horizontal = inset(type), vertical = inset(type) * 0.7f),
        )
    }
}
