package com.skypass.feature.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.animateCountUp
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.Tier
import java.text.NumberFormat
import java.util.Locale

/** 48200 → "48.2k", 900 → "900". */
internal fun compactMiles(miles: Int): String = when {
    miles < 1_000 -> miles.toString()
    miles % 1_000 < 50 -> "${miles / 1_000}k"
    else -> String.format(Locale.US, "%.1fk", miles / 1_000f)
}

internal fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

@Composable
fun ProfileRoute(
    onSettings: () -> Unit,
    onPassengers: () -> Unit,
    onSaved: () -> Unit,
    onTrips: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by rememberUpdatedState(onSettings)
    val passengers by rememberUpdatedState(onPassengers)
    val saved by rememberUpdatedState(onSaved)
    val trips by rememberUpdatedState(onTrips)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.Settings -> settings()
                ProfileEffect.Passengers -> passengers()
                ProfileEffect.Saved -> saved()
                ProfileEffect.Trips -> trips()
            }
        }
    }
    // Back on the tab after booking or editing travellers: the tiles and badges catch up.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ProfileEvent.Refresh)
        onPauseOrDispose { }
    }
    ProfileScreen(state, viewModel::onEvent)
}

@Composable
fun ProfileScreen(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.profile_title),
                style = SkypassTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconCircleButton(SkypassIcons.Settings, stringResource(R.string.profile_settings), { onEvent(ProfileEvent.OpenSettings) })
        }
        val stats = state.overview?.stats
        UserCard(state, stats)
        StatTiles(stats ?: ProfileStats.Empty, loading = stats == null)
        if (state.error != null && state.overview == null) {
            OfflineNote { onEvent(ProfileEvent.Retry) }
        }
        Menu(state, onEvent)
    }
    when (val sheet = state.sheet) {
        is ProfileSheet.PersonalInfo -> PersonalInfoSheet(sheet.draft, onEvent)
        ProfileSheet.Payments -> PaymentsSheet(onDismiss = { onEvent(ProfileEvent.DismissSheet) })
        ProfileSheet.Help -> HelpSheet(onDismiss = { onEvent(ProfileEvent.DismissSheet) })
        null -> Unit
    }
}

@Composable
private fun UserCard(state: ProfileUiState, stats: ProfileStats?) {
    val colors = SkypassTheme.colors
    val guest = state.session?.isGuest == true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.hero)
            .background(colors.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(state.initials, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    state.displayName ?: stringResource(R.string.profile_guest_name),
                    style = SkypassTheme.typography.titleLg,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.overview?.profile?.email ?: state.session?.email ?: stringResource(
                        if (guest) R.string.profile_guest_email else R.string.profile_no_email,
                    ),
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                TierPill(stats?.tier ?: Tier.Blue)
            }
        }
        if (stats != null) TierProgress(stats)
    }
}

@Composable
internal fun TierPill(tier: Tier) {
    val colors = SkypassTheme.colors
    val (container, content) = when (tier) {
        Tier.Blue -> colors.sky to colors.onPastel
        Tier.Silver -> colors.lilac to colors.onPastel
        Tier.Gold -> colors.butter to colors.goldInk
        Tier.Platinum -> colors.ink to colors.onInk
    }
    StatusPill(tier.label, container = container, content = content)
}

/** "1,800 miles to Skypass Platinum", with a bar that fills on arrival. */
@Composable
private fun TierProgress(stats: ProfileStats) {
    val colors = SkypassTheme.colors
    val progress by animateFloatAsState(stats.progress, tween(900), label = "tierProgress")
    val next = stats.nextTier
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.surfaceSunken),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(CircleShape).background(colors.brand))
        }
        Text(
            if (next != null) stringResource(R.string.profile_to_next_tier, grouped(stats.milesToNext), next.label)
            else stringResource(R.string.profile_top_tier),
            style = SkypassTheme.typography.caption,
            color = colors.text2,
        )
    }
}

@Composable
private fun StatTiles(stats: ProfileStats, loading: Boolean) {
    val colors = SkypassTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            value = animateCountUp(stats.trips).toString(),
            label = pluralStringResource(R.plurals.profile_trips, stats.trips),
            container = colors.mint,
            loading = loading,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = animateCountUp(stats.countries).toString(),
            label = pluralStringResource(R.plurals.profile_countries, stats.countries),
            container = colors.lavender,
            loading = loading,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = compactMiles(animateCountUp(stats.miles, durationMs = 900)),
            label = stringResource(R.string.profile_miles),
            container = colors.peach,
            loading = loading,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, container: Color, loading: Boolean, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(14.dp)
            .clearAndSetSemantics { contentDescription = if (loading) "" else "$value $label" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(if (loading) "–" else value, style = SkypassTheme.typography.figure, color = colors.onPastel)
        Text(label, style = SkypassTheme.typography.captionStrong, color = colors.onPastel2)
    }
}

private data class MenuItem(val icon: ImageVector, val label: Int, val badge: Int?, val event: ProfileEvent)

@Composable
private fun Menu(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val items = listOf(
        MenuItem(SkypassIcons.User, R.string.profile_personal_info, null, ProfileEvent.OpenPersonalInfo),
        MenuItem(SkypassIcons.Passport, R.string.profile_passengers, state.overview?.passengers?.size?.takeIf { it > 0 }, ProfileEvent.OpenPassengers),
        MenuItem(SkypassIcons.Card, R.string.profile_payments, null, ProfileEvent.OpenPayments),
        MenuItem(SkypassIcons.Bookmark, R.string.profile_saved, state.savedCount.takeIf { it > 0 }, ProfileEvent.OpenSaved),
        MenuItem(SkypassIcons.Clock, R.string.profile_trips_history, null, ProfileEvent.OpenTrips),
        MenuItem(SkypassIcons.Help, R.string.profile_help, null, ProfileEvent.OpenHelp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(colors.borderSoft))
            val label = stringResource(item.label)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClickLabel = label) { onEvent(item.event) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.bg), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
                }
                Text(label, style = SkypassTheme.typography.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                if (item.badge != null) {
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape)
                            .background(colors.ink)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.badge.toString(), style = SkypassTheme.typography.pill, color = colors.onInk)
                    }
                }
                Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.text3, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun OfflineNote(onRetry: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.profile_offline), style = SkypassTheme.typography.bodySm, color = colors.text2, modifier = Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.profile_retry), onClick = onRetry, height = 40.dp)
    }
}
