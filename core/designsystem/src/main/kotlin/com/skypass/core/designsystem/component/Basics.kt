package com.skypass.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.R
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme

/** "Skypass" wordmark: Plus Jakarta Sans ExtraBold, tightly tracked, teal on light. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    size: Int = 16,
    color: Color = SkypassTheme.colors.brand,
) {
    Text(
        text = "Skypass",
        modifier = modifier.semantics { contentDescription = "Skypass" },
        style = TextStyle(
            fontFamily = JakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = size.sp,
            letterSpacing = (if (size >= 40) -0.045f else -0.02f).em,
        ),
        color = color,
    )
}

/**
 * Pager dots from the board: active is a 26×8 navy pill, the rest 8×8 dots, each in a 44dp hit
 * area. Width and colour animate over 300 ms.
 */
@Composable
fun PageDots(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "Page ${currentPage + 1} of $pageCount" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dotWidth")
            val color by animateColorAsState(
                if (selected) SkypassTheme.colors.ink else SkypassTheme.colors.borderStrong,
                tween(300),
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .width(width + 10.dp)
                    .clickable(indication = null, interactionSource = null, role = Role.Tab) { onDotClick(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(width).height(8.dp).background(color, Shapes.pill))
            }
        }
    }
}

/** 18dp ring spinner (2.5dp border, .8 s per turn). */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    color: Color = SkypassTheme.colors.ink,
    track: Color = SkypassTheme.colors.border,
    size: Dp = 18.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(modifier.size(size)) {
        val w = 2.5.dp.toPx()
        drawCircle(track, radius = (this.size.minDimension - w) / 2, style = Stroke(w))
        drawArc(
            color = color,
            startAngle = angle - 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

/** Small rounded pill ("On time", "Best value", "Deal"). */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = SkypassTheme.colors.mint,
    content: Color = SkypassTheme.colors.brandDeep,
) {
    Text(
        text = text,
        modifier = modifier
            .background(container, Shapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = SkypassTheme.typography.pill,
        color = content,
    )
}

/** Initials avatar on a pastel circle. */
@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = SkypassTheme.colors.peach,
    ring: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (ring) Modifier.border(2.dp, SkypassTheme.colors.surface, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = SkypassTheme.typography.label.copy(fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.ExtraBold),
            color = SkypassTheme.colors.onPastel,
            textAlign = TextAlign.Center,
        )
    }
}

/** Air New Zealand koru mark (black PNG tinted to ink so it works in dark mode). */
@Composable
fun AnzKoru(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = Color.Black) {
    Image(
        painter = painterResource(R.drawable.anz_koru),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Air New Zealand wordmark, sized by height like the board (11–12dp). */
@Composable
fun AnzWordmark(modifier: Modifier = Modifier, height: Dp = 11.dp, tint: Color = Color.Black) {
    Image(
        painter = painterResource(R.drawable.anz_wordmark),
        contentDescription = "Air New Zealand",
        modifier = modifier
            .height(height)
            .width(height * (569f / 48f)),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Diagonal-striped placeholder for photography (board: repeating 135° stripes). */
fun Modifier.stripes(tone: Color, stripe: Color = Color.White.copy(alpha = 0.45f), width: Dp = 10.dp): Modifier =
    drawBehind {
        drawRect(tone)
        val w = width.toPx()
        val step = w * 2
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = stripe,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = w,
            )
            x += step * 1.4142f
        }
    }

/** Section header row: title + optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = SkypassTheme.typography.title, color = SkypassTheme.colors.ink, modifier = Modifier.weight(1f))
        if (action != null) {
            SkypassTextButton(action, onAction, color = SkypassTheme.colors.brand, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
        }
    }
}

/** Uppercase DM Mono field label (FROM, DEPART, GATE…). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = SkypassTheme.colors.text2, small: Boolean = false) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = if (small) SkypassTheme.typography.eyebrowSm else SkypassTheme.typography.eyebrow,
        color = color,
    )
}
