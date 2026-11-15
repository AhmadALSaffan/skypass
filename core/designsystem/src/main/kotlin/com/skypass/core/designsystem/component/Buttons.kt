package com.skypass.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.pressScale

/**
 * Navy primary button (56dp, radius 18). On press it turns brand teal — the board's hover state —
 * and settles under the finger.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderStrong
            pressed -> colors.brand
            else -> colors.ink
        },
        animationSpec = tween(Durations.BASE),
        label = "primaryContainer",
    )
    Row(
        modifier = modifier
            .pressScale(interaction)
            .height(height)
            .clip(Shapes.button)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) Spinner(color = colors.onInk, track = colors.onInk.copy(alpha = 0.35f))
        if (leadingIcon != null && !loading) {
            Icon(leadingIcon, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(19.dp))
        }
        Text(text = text, style = SkypassTheme.typography.label, color = colors.onInk)
        if (trailingIcon != null && !loading) {
            Icon(trailingIcon, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(18.dp))
        }
    }
}

/** Outlined white button (Google sign-in, Sign out). */
@Composable
fun OutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = SkypassTheme.colors.ink,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border by animateColorAsState(if (pressed) colors.ink else colors.border, tween(Durations.BASE), label = "border")
    Row(
        modifier = modifier
            .pressScale(interaction)
            .height(height)
            .clip(Shapes.button)
            .background(colors.surface)
            .border(1.5.dp, border, Shapes.button)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text = text, style = SkypassTheme.typography.titleSm, color = contentColor)
    }
}

/** Quiet text button ("Skip", "Continue as guest", "See all"). Keeps a 44dp touch target. */
@Composable
fun SkypassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SkypassTheme.colors.text2,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .clip(Shapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = SkypassTheme.typography.labelMd, color = color)
    }
}

/** 44dp white circular icon button (back, bell, filters, share). */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = SkypassTheme.colors.surface,
    tint: Color = SkypassTheme.colors.ink,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    badge: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .pressScale(interaction, 0.92f)
                .size(size)
                .clip(CircleShape)
                .background(container)
                .clickable(interaction, null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 11.dp)
                    .size(8.dp)
                    .border(2.dp, container, CircleShape)
                    .background(SkypassTheme.colors.alert, CircleShape),
            )
        }
    }
}

/** Full-width helper. */
fun Modifier.fullWidth() = fillMaxWidth()
