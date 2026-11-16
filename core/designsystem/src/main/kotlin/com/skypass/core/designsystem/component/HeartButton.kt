package com.skypass.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.SkypassTheme

/**
 * A white circle with a hollow heart that fills red with a little bounce when saved. Sits on
 * photos (hotel cards, destination tiles), so the circle stays white in dark mode too.
 */
@Composable
fun HeartButton(
    saved: Boolean,
    onClick: () -> Unit,
    saveLabel: String,
    unsaveLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val haptics = LocalHapticFeedback.current
    val alert = SkypassTheme.colors.alert
    val tint by animateColorAsState(if (saved) alert else Color(0xFF1C2550), tween(200), label = "heartTint")
    var bounces by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (bounces % 2 == 1) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 900f),
        finishedListener = { if (bounces % 2 == 1) bounces++ },
        label = "heartScale",
    )
    val label = if (saved) unsaveLabel else saveLabel
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(role = Role.Button, onClickLabel = label) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!saved) bounces++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) SkypassIcons.HeartFilled else SkypassIcons.Heart,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(size * 0.43f).scale(scale),
        )
    }
}
