package com.skypass.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.softShadow

/** Segmented tabs (One way / Round trip / Multi-city). */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken, Shapes.segmentTrack)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(if (selected) colors.surface else Color.Transparent, tween(Durations.BASE), label = "seg")
            val fg by animateColorAsState(if (selected) colors.ink else colors.text2, tween(Durations.BASE), label = "segText")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .softShadow(SoftShadow.Segment, Shapes.segment, enabled = selected)
                    .clip(Shapes.segment)
                    .background(bg)
                    .selectable(selected, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = SkypassTheme.typography.labelSm, color = fg)
            }
        }
    }
}

/** − value + stepper with 44dp square buttons. */
@Composable
fun CounterStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    decrementLabel: String = "Fewer",
    incrementLabel: String = "More",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepButton("−", decrementLabel, canDecrement, onDecrement)
        Text(
            value.toString(),
            style = SkypassTheme.typography.topBar,
            color = SkypassTheme.colors.ink,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center,
        )
        StepButton("+", incrementLabel, canIncrement, onIncrement)
    }
}

@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(Shapes.stepper)
            .background(SkypassTheme.colors.surfaceSunken)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = SkypassTheme.typography.figureSm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = if (enabled) SkypassTheme.colors.ink else SkypassTheme.colors.borderStrong,
        )
    }
}

/**
 * 52×32 switch: teal when on, knob slides with a soft spring. Pass a null [onCheckedChange] when a
 * whole row is the switch (the row is then the touch target and the one accessibility node).
 */
@Composable
fun SkypassToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val track by animateColorAsState(if (checked) colors.brand else colors.borderStrong, tween(Durations.BASE), label = "track")
    val knobX by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "knob",
    )
    Box(
        modifier
            .width(52.dp)
            .height(32.dp)
            .clip(Shapes.pill)
            .background(track)
            .then(if (onCheckedChange != null) Modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(28.dp)
                .softShadow(SoftShadow.Segment, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** Filter / sort chip: navy fill when selected, outlined otherwise. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val bg by animateColorAsState(if (selected) colors.ink else Color.Transparent, tween(Durations.BASE), label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.onInk else colors.ink, tween(Durations.BASE), label = "chipFg")
    val border by animateColorAsState(if (selected) colors.ink else colors.borderStrong, tween(Durations.BASE), label = "chipBorder")
    Box(
        modifier
            .height(36.dp)
            .clip(Shapes.pill)
            .background(bg)
            .border(1.5.dp, border, Shapes.pill)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = SkypassTheme.typography.labelSm, color = fg)
    }
}

/** Sub-page top bar: 44dp back button, centred title (+ optional subtitle), optional action. */
@Composable
fun SkypassTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircleButton(SkypassIcons.ArrowLeft, "Back", onBack)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = SkypassTheme.typography.topBar, color = SkypassTheme.colors.ink, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = SkypassTheme.typography.caption, color = SkypassTheme.colors.text2)
            }
        }
        if (action != null) action() else Spacer(Modifier.size(44.dp))
    }
}

/** Bottom navigation item spec. */
data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** 4 icon-only tabs; the active one turns navy with a 4dp dot underneath. */
@Composable
fun SkypassBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEachIndexed { index, item -> BottomBarTab(item, index == selectedIndex) { onSelect(index) } }
    }
}

@Composable
private fun RowScope.BottomBarTab(item: BottomBarItem, selected: Boolean, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val tint by animateColorAsState(if (selected) colors.ink else colors.icon2, tween(Durations.BASE), label = "tab")
    val dot by animateDpAsState(if (selected) 4.dp else 0.dp, spring(dampingRatio = 0.5f), label = "dot")
    Column(
        Modifier
            .size(width = 56.dp, height = 44.dp)
            .clip(Shapes.tile)
            .selectable(selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
        Box(Modifier.size(dot).background(colors.ink, CircleShape))
    }
}
