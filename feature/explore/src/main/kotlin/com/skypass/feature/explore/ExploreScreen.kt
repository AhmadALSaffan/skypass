package com.skypass.feature.explore

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.skypass.core.designsystem.component.AnzKoru
import com.skypass.core.designsystem.component.AnzWordmark
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.HeartButton
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.model.Airport
import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import com.skypass.core.model.ExploreCategory
import java.text.NumberFormat
import java.util.Locale

/** In the traveller's currency (Settings), from US dollars. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun usd(amount: Int): String = com.skypass.core.designsystem.theme.money(amount)

@Composable
fun ExploreRoute(
    onOpenFlights: () -> Unit,
    onOpenHotels: () -> Unit,
    onOpenCars: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flights by rememberUpdatedState(onOpenFlights)
    val hotels by rememberUpdatedState(onOpenHotels)
    val cars by rememberUpdatedState(onOpenCars)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ExploreEffect.OpenFlights -> flights()
                ExploreEffect.OpenHotels -> hotels()
                ExploreEffect.OpenCars -> cars()
            }
        }
    }
    ExploreScreen(state, viewModel::onEvent)
}

@Composable
fun ExploreScreen(state: ExploreUiState, onEvent: (ExploreEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = top + 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        fullWidth("title") {
            Row(Modifier.reveal(0), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.explore_title),
                    style = SkypassTheme.typography.headline,
                    color = colors.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                state.origin?.let {
                    Eyebrow(stringResource(R.string.explore_from_origin, it.code), modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
        fullWidth("search") {
            SearchField(
                query = state.query,
                onQueryChange = { onEvent(ExploreEvent.QueryChanged(it)) },
                onClear = { onEvent(ExploreEvent.ClearQuery) },
                onDone = { focus.clearFocus() },
                modifier = Modifier.reveal(1),
            )
        }
        if (!state.isSearching) {
            fullWidth("services") {
                ServiceTiles(onEvent, Modifier.reveal(2))
            }
            state.deal?.let { deal ->
                fullWidth("deal") {
                    DealCard(deal, onClick = { onEvent(ExploreEvent.DealTapped) }, modifier = Modifier.reveal(3).animateItem())
                }
            }
        }
        fullWidth("chips") {
            CategoryChips(state.category, { onEvent(ExploreEvent.CategorySelected(it)) }, Modifier.reveal(4).bleed(Gutter))
        }

        when {
            state.error != null -> fullWidth("error") { OfflineCard(onRetry = { onEvent(ExploreEvent.Retry) }) }
            state.loading -> items(4, key = { "skeleton-$it" }) { SkeletonTile(it) }
            else -> {
                if (state.airportMatches.isNotEmpty()) {
                    fullWidth("airports-header") { Eyebrow(stringResource(R.string.explore_airports), modifier = Modifier.animateItem()) }
                    items(state.airportMatches, key = { "airport-${it.code}" }, span = { GridItemSpan(maxLineSpan) }) { airport ->
                        AirportRow(airport, onClick = { onEvent(ExploreEvent.AirportTapped(airport)) }, modifier = Modifier.animateItem())
                    }
                }
                itemsIndexed(state.destinations) { index, destination ->
                    DestinationTile(
                        destination,
                        saved = destination.id in state.savedIds,
                        onToggleSaved = { onEvent(ExploreEvent.ToggleSaved(destination)) },
                        onClick = { onEvent(ExploreEvent.DestinationTapped(destination)) },
                        modifier = Modifier.animateItem().reveal(5 + index / 2),
                    )
                }
                if (state.isEmpty) fullWidth("empty") { EmptyState(Modifier.animateItem()) }
            }
        }
    }
}

private val Gutter = 20.dp

private fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.itemsIndexed(list: List<Destination>, content: @Composable LazyGridItemScope.(Int, Destination) -> Unit) =
    items(list.size, key = { list[it].id }) { content(it, list[it]) }

/** Lets a row run edge to edge past the grid's gutters (the chips scroll under the screen edge). */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val hint = stringResource(R.string.explore_search_hint)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.surface, Shapes.field)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SkypassIcons.Search, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(hint, style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = SkypassTheme.typography.labelMd.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SkypassIcons.Close, contentDescription = stringResource(R.string.explore_clear_search), tint = colors.text2, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AirportRow(airport: Airport, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .clip(Shapes.sm)
            .background(colors.surface)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(colors.sky, Shapes.tile), contentAlignment = Alignment.Center) {
            Text(airport.code, style = SkypassTheme.typography.monoSm, color = colors.onPastel)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.explore_fly_to, airport.city), style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Bold), color = colors.ink)
            Text("${airport.name} · ${airport.country}", style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
        }
        Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Service tiles and deal
// ---------------------------------------------------------------------------------------------

@Composable
private fun ServiceTiles(onEvent: (ExploreEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ServiceTile(stringResource(R.string.explore_flights), SkypassIcons.Plane, colors.mint, iconRotation = 45f, Modifier.weight(1f)) {
            onEvent(ExploreEvent.FlightsTapped)
        }
        ServiceTile(stringResource(R.string.explore_hotels), SkypassIcons.Bed, colors.lavender, iconRotation = 0f, Modifier.weight(1f)) {
            onEvent(ExploreEvent.HotelsTapped)
        }
        ServiceTile(stringResource(R.string.explore_cars), SkypassIcons.Car, colors.peach, iconRotation = 0f, Modifier.weight(1f)) {
            onEvent(ExploreEvent.CarsTapped)
        }
    }
}

@Composable
private fun ServiceTile(
    label: String,
    icon: ImageVector,
    container: Color,
    iconRotation: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressed = 0.94f)
            .height(92.dp)
            .clip(Shapes.card)
            .background(container)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(label, style = SkypassTheme.typography.labelSm, color = SkypassTheme.colors.onPastel)
        Icon(
            icon,
            contentDescription = null,
            tint = SkypassTheme.colors.onPastel,
            modifier = Modifier.align(Alignment.BottomEnd).size(26.dp).rotate(iconRotation),
        )
    }
}

@Composable
private fun DealCard(deal: Deal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val route = if (deal.nonstop) {
        stringResource(R.string.explore_nonstop_route, deal.originCode, deal.destinationCode)
    } else {
        stringResource(R.string.explore_route, deal.originCode, deal.destinationCode)
    }
    val from = stringResource(R.string.explore_from)
    val price = usd(deal.fromPriceUsd)
    Column(
        modifier
            .pressScale(interaction, pressed = 0.98f)
            .fillMaxWidth()
            .clip(Shapes.hero)
            .background(colors.surface)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${deal.title}. $route, $from $price" },
    ) {
        var photoShown by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(140.dp).then(if (photoShown) Modifier else Modifier.stripes(Color(deal.tone)))) {
            deal.imageUrl?.let { Photo(it, Modifier.matchParentSize(), onLoaded = { photoShown = true }) }
            StatusPill(
                stringResource(R.string.explore_deal),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                container = Color(0xFF1C2550),
                content = Color.White,
            )
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (deal.airlineCode == "NZ") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnzWordmark(height = 10.dp, tint = colors.ink)
                    AnzKoru(size = 16.dp, tint = colors.ink)
                }
            }
            Text(deal.title, style = SkypassTheme.typography.titleLg, color = colors.ink)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(route, style = SkypassTheme.typography.bodySm, color = colors.text2, modifier = Modifier.weight(1f))
                Text(
                    buildAnnotatedString {
                        append("$from ")
                        withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.ink)) { append(usd(deal.fromPriceUsd)) }
                    },
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Categories and grid
// ---------------------------------------------------------------------------------------------

@Composable
private fun CategoryChips(selected: ExploreCategory, onSelect: (ExploreCategory) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Gutter, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(ExploreCategory.entries) { category ->
            FilterChip(category.label, selected = category == selected, onClick = { onSelect(category) })
        }
    }
}

@Composable
private fun DestinationTile(
    destination: Destination,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val price = usd(destination.fromPriceUsd)
    val subtitle = stringResource(R.string.explore_country_price, destination.country, price)
    Box(modifier) {
    Column(
        Modifier
            .pressScale(interaction)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${destination.city}, $subtitle" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var photoShown by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(Shapes.card)
                .then(if (photoShown) Modifier else Modifier.stripes(Color(destination.tone))),
        ) {
            val photo = destination.imageUrl
            if (photo != null) {
                Photo(photo, Modifier.matchParentSize(), onLoaded = { photoShown = true })
            } else {
                Text(
                    stringResource(R.string.explore_photo),
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp),
                    style = SkypassTheme.typography.monoSm.copy(fontSize = 10.sp),
                    color = colors.onPastel2,
                )
            }
        }
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(destination.city, style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
            Text(subtitle, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
        }
    }
        // Outside the tile's merged semantics, so TalkBack reads it as its own button.
        HeartButton(
            saved = saved,
            onClick = onToggleSaved,
            saveLabel = stringResource(R.string.explore_save, destination.city),
            unsaveLabel = stringResource(R.string.explore_unsave, destination.city),
            size = 36.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
    }
}

@Composable
private fun Photo(url: String, modifier: Modifier = Modifier, onLoaded: () -> Unit = {}) {
    AsyncImage(
        onSuccess = { onLoaded() },
        model = ImageRequest.Builder(LocalPlatformContext.current).data(url).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** Striped pastel tiles that breathe while the grid loads. */
@Composable
private fun SkeletonTile(index: Int) {
    val colors = SkypassTheme.colors
    val tones = listOf(colors.mint, colors.butter, colors.sky, colors.peach)
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = index * 120), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(Modifier.graphicsLayer { alpha = pulse }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(130.dp).clip(Shapes.card).stripes(tones[index % tones.size]))
        Box(Modifier.width(90.dp).height(12.dp).background(colors.border, Shapes.xs))
        Box(Modifier.width(120.dp).height(10.dp).background(colors.borderSoft, Shapes.xs))
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.Compass, contentDescription = null, tint = colors.text2, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.explore_empty_title), style = SkypassTheme.typography.label, color = colors.ink)
        Text(stringResource(R.string.explore_empty_body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, Shapes.heroLg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.explore_offline_title), style = SkypassTheme.typography.label, color = colors.ink)
        Text(stringResource(R.string.explore_offline_body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        SkypassTextButton(stringResource(R.string.explore_retry), onRetry, color = colors.brand)
    }
}
