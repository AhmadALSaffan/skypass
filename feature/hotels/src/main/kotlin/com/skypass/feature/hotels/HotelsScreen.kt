package com.skypass.feature.hotels

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.skypass.core.designsystem.component.HeartButton
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.Hotel
import com.skypass.core.model.HotelFilter
import com.skypass.core.model.Stay
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** In the traveller's currency (Settings), from US dollars. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun usd(amount: Int): String = com.skypass.core.designsystem.theme.money(amount)

private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val Day = DateTimeFormatter.ofPattern("d", Locale.getDefault())

/** "20–27 Nov" or "28 Nov – 3 Dec". */
internal fun Stay.dateRange(): String =
    if (checkIn.month == checkOut.month) "${checkIn.format(Day)}–${checkOut.format(DayMonth)}"
    else "${checkIn.format(DayMonth)} – ${checkOut.format(DayMonth)}"

@Composable
fun HotelsRoute(onBack: () -> Unit, onOpenMyTrips: () -> Unit, viewModel: HotelsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val back by rememberUpdatedState(onBack)
    val openTrips by rememberUpdatedState(onOpenMyTrips)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HotelsEffect.OpenMyTrips -> openTrips()
                is HotelsEffect.OpenMap -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(effect.query)))
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // No maps app: open the web map instead.
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(effect.query))),
                        )
                    }
                }
            }
        }
    }
    HotelsScreen(state, viewModel::onEvent, onBack = { back() })
}

@Composable
fun HotelsScreen(state: HotelsUiState, onEvent: (HotelsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Header(state.stay, onBack = onBack, onEdit = { onEvent(HotelsEvent.EditStay) }, onMap = { onEvent(HotelsEvent.OpenMap) })
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(HotelFilter.entries) { filter ->
                FilterChip(filter.label, selected = filter == state.filter, onClick = { onEvent(HotelsEvent.SelectFilter(filter)) })
            }
        }
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottom + 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            when {
                state.error != null -> item(key = "error") { OfflineCard(onRetry = { onEvent(HotelsEvent.Retry) }) }
                state.loading || state.stay == null -> items(2, key = { "skeleton-$it" }) { SkeletonCard(it) }
                state.isEmpty -> item(key = "empty") {
                    Text(
                        stringResource(R.string.hotels_empty),
                        style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Normal),
                        color = colors.text2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp).animateItem(),
                    )
                }
                else -> {
                    val stay = state.stay
                    items(state.hotels.size, key = { state.hotels[it].id }) { index ->
                        val hotel = state.hotels[index]
                        HotelCard(
                            hotel = hotel,
                            nights = stay.nights,
                            saved = hotel.id in state.savedIds,
                            onToggleSaved = { onEvent(HotelsEvent.ToggleSaved(hotel)) },
                            onViewRooms = { onEvent(HotelsEvent.ViewRooms(hotel)) },
                            modifier = Modifier.animateItem().reveal(index.coerceAtMost(4)),
                        )
                    }
                }
            }
        }
    }

    state.draft?.let { StaySheet(it, state.firstBookableDate, onEvent) }
    state.rooms?.let { sheet -> state.stay?.let { RoomsSheet(sheet, it, onEvent) } }
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun Header(stay: Stay?, onBack: () -> Unit, onEdit: () -> Unit, onMap: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircleButton(SkypassIcons.ArrowLeft, "Back", onBack)
        Column(
            Modifier
                .weight(1f)
                .clip(Shapes.sm)
                .clickable(enabled = stay != null, role = Role.Button, onClickLabel = stringResource(R.string.hotels_edit_stay), onClick = onEdit)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stay?.let { stringResource(R.string.hotels_title, it.city) } ?: "",
                style = SkypassTheme.typography.topBar,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stay != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${stay.dateRange()} · ${pluralStringResource(R.plurals.hotels_rooms, stay.rooms, stay.rooms)} · " +
                            pluralStringResource(R.plurals.hotels_guests, stay.guests, stay.guests),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                    )
                    Icon(SkypassIcons.ChevronDown, contentDescription = null, tint = colors.text2, modifier = Modifier.size(12.dp))
                }
            }
        }
        IconCircleButton(SkypassIcons.Map, stringResource(R.string.hotels_map), onMap, iconSize = 19.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// Hotel card (board 09)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HotelCard(
    hotel: Hotel,
    nights: Int,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onViewRooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(SoftShadow.Card, Shapes.hero)
            .clip(Shapes.hero)
            .background(colors.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            var shown by remember { mutableStateOf(false) }
            Box(Modifier.matchParentSize().then(if (shown) Modifier else Modifier.stripes(colors.lavender)))
            if (hotel.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current).data(hotel.imageUrl).crossfade(true).build(),
                    contentDescription = hotel.name,
                    contentScale = ContentScale.Crop,
                    onSuccess = { shown = true },
                    modifier = Modifier.matchParentSize(),
                )
            }
            HeartButton(
                saved = saved,
                onClick = onToggleSaved,
                saveLabel = stringResource(R.string.hotels_save),
                unsaveLabel = stringResource(R.string.hotels_unsave),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
            if (hotel.freeCancellation) {
                Text(
                    stringResource(R.string.hotels_free_cancel),
                    style = SkypassTheme.typography.pill,
                    color = Color(0xFF0B4642),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.White, Shapes.pill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(hotel.name, style = SkypassTheme.typography.titleSm, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.hotels_area, hotel.area, formatKm(hotel.distanceKm)),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                    )
                }
                val reviews = pluralStringResource(R.plurals.hotels_reviews, hotel.reviewCount, NumberFormat.getIntegerInstance().format(hotel.reviewCount))
                val rating = String.format(Locale.US, "%.1f", hotel.rating)
                val ratingLabel = stringResource(R.string.hotels_rating, rating) + ", " + reviews
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = ratingLabel
                    },
                ) {
                    Text(
                        rating,
                        style = SkypassTheme.typography.labelSm.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        modifier = Modifier.background(Color(0xFF0F5B56), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(reviews, style = SkypassTheme.typography.caption.copy(fontSize = 10.sp), color = colors.text2)
                }
            }
            val starsLabel = stringResource(R.string.hotels_stars, hotel.stars)
            Text(
                "★".repeat(hotel.stars),
                style = SkypassTheme.typography.caption.copy(letterSpacing = 2.sp),
                color = Color(0xFFF5B400),
                modifier = Modifier.semantics { contentDescription = starsLabel },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hotel.amenities.forEach { amenity ->
                    Text(
                        amenity,
                        style = SkypassTheme.typography.pill.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                        modifier = Modifier.background(colors.bg, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(usd(hotel.pricePerNightUsd), style = SkypassTheme.typography.figure, color = colors.ink)
                        Text(
                            stringResource(R.string.hotels_per_night),
                            style = SkypassTheme.typography.caption,
                            color = colors.text2,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        pluralStringResource(R.plurals.hotels_nights_total, nights, usd(hotel.pricePerNightUsd * nights), nights),
                        style = SkypassTheme.typography.caption.copy(fontSize = 11.sp),
                        color = colors.text2,
                    )
                }
                ViewRoomsButton(onViewRooms)
            }
        }
    }
}

@Composable
private fun ViewRoomsButton(onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.ink)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.hotels_view_rooms), style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Bold), color = colors.onInk)
    }
}

private fun formatKm(km: Double): String = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.US, "%.1f", km)

@Composable
private fun SkeletonCard(index: Int) {
    val colors = SkypassTheme.colors
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = index * 150), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = pulse }.clip(Shapes.hero).background(colors.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(160.dp).stripes(if (index == 0) colors.lavender else colors.sky))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(180.dp).height(14.dp).background(colors.border, Shapes.xs))
            Box(Modifier.width(120.dp).height(10.dp).background(colors.borderSoft, Shapes.xs))
            Box(Modifier.width(220.dp).height(24.dp).background(colors.borderSoft, Shapes.xs))
        }
    }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.surface, Shapes.heroLg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.hotels_offline_title), style = SkypassTheme.typography.label, color = colors.ink)
        Text(stringResource(R.string.hotels_offline_body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        SkypassTextButton(stringResource(R.string.hotels_retry), onRetry, color = colors.brand)
    }
}
