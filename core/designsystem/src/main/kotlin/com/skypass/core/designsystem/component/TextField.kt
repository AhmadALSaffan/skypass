package com.skypass.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme

/**
 * Board-style input: DM Mono label above a 52dp white field. The border turns navy while focused
 * and red with a message when [error] is set.
 */
@Composable
fun SkypassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            error != null -> colors.danger
            focused -> colors.ink
            else -> colors.border
        },
        tween(Durations.BASE),
        label = "fieldBorder",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.surface, Shapes.field)
                .border(1.5.dp, border, Shapes.field)
                .padding(horizontal = 16.dp)
                .semantics { if (error != null) error(error) },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = SkypassTheme.typography.bodySm, color = colors.text2)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = SkypassTheme.typography.bodyStrong.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) Text(error, style = SkypassTheme.typography.caption, color = colors.danger)
    }
}
