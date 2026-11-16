package com.skypass.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.FormSection
import com.skypass.core.designsystem.component.InfoCard
import com.skypass.core.designsystem.component.LabeledValue
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.ProfileProblem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SkypassTheme.colors.bg,
    ) { content() }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = SkypassTheme.typography.figure, color = SkypassTheme.colors.ink)
        Text(subtitle, style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2)
    }
}

// ---------------------------------------------------------------------------------------------
// Personal information
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PersonalInfoSheet(draft: PersonalInfoDraft, onEvent: (ProfileEvent) -> Unit) {
    Sheet(onDismiss = { if (draft.pickingAirport) onEvent(ProfileEvent.CancelAirportPick) else onEvent(ProfileEvent.DismissSheet) }) {
        AnimatedContent(
            targetState = draft.pickingAirport,
            transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
            label = "personalInfo",
        ) { picking ->
            if (picking) AirportPicker(draft, onEvent) else PersonalInfoForm(draft, onEvent)
        }
    }
}

@Composable
private fun PersonalInfoForm(draft: PersonalInfoDraft, onEvent: (ProfileEvent) -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SheetTitle(stringResource(R.string.profile_personal_info), stringResource(R.string.profile_personal_info_hint))
        SkypassTextField(
            value = draft.fullName,
            onValueChange = { onEvent(ProfileEvent.NameChanged(it)) },
            label = stringResource(R.string.profile_full_name),
            placeholder = stringResource(R.string.profile_full_name_hint),
            error = stringResource(R.string.profile_problem_name).takeIf { draft.problem == ProfileProblem.NameTooShort },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        FormSection(stringResource(R.string.profile_email)) {
            InfoCard {
                Text(
                    draft.email ?: stringResource(R.string.profile_guest_email),
                    style = SkypassTheme.typography.bodyStrong,
                    color = colors.ink,
                )
                Text(stringResource(R.string.profile_email_hint), style = SkypassTheme.typography.caption, color = colors.text2)
            }
        }
        FormSection(stringResource(R.string.profile_home_airport)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(Shapes.field)
                    .background(colors.surface)
                    .border(1.5.dp, if (draft.problem == ProfileProblem.NoHomeAirport) colors.danger else colors.border, Shapes.field)
                    .clickable(role = Role.Button) { onEvent(ProfileEvent.PickHomeAirport) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(draft.homeCode, style = SkypassTheme.typography.title, color = colors.ink)
                Text(
                    draft.homeAirport?.let { "${it.city} · ${it.country}" }.orEmpty(),
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(SkypassIcons.ChevronDown, contentDescription = null, tint = colors.text2, modifier = Modifier.size(16.dp))
            }
            Text(stringResource(R.string.profile_home_airport_hint), style = SkypassTheme.typography.caption, color = colors.text2)
        }
        if (draft.failed) Text(stringResource(R.string.profile_save_failed), style = SkypassTheme.typography.bodySm, color = colors.danger)
        PrimaryButton(
            stringResource(R.string.profile_save),
            onClick = { onEvent(ProfileEvent.SavePersonalInfo) },
            loading = draft.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AirportPicker(draft: PersonalInfoDraft, onEvent: (ProfileEvent) -> Unit) {
    val colors = SkypassTheme.colors
    Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(stringResource(R.string.profile_home_airport), stringResource(R.string.profile_pick_airport_hint))
            SkypassTextField(
                value = draft.airportQuery,
                onValueChange = { onEvent(ProfileEvent.AirportQueryChanged(it)) },
                label = stringResource(R.string.profile_search_airports),
                placeholder = stringResource(R.string.profile_search_airports_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            )
        }
        if (draft.airports.isEmpty()) {
            Text(
                stringResource(R.string.profile_no_airport, draft.airportQuery),
                style = SkypassTheme.typography.bodySm,
                color = colors.text2,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.heightIn(max = 440.dp)) {
            items(draft.airports, key = { it.code }) { airport ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(Shapes.sm)
                        .clickable(role = Role.Button) { onEvent(ProfileEvent.AirportPicked(airport)) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(airport.code, style = SkypassTheme.typography.title, color = colors.ink, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(airport.city, style = SkypassTheme.typography.label, color = colors.ink)
                        Text(airport.name, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
                    }
                    if (airport.code == draft.homeCode) Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Help centre
// ---------------------------------------------------------------------------------------------

private val Faq = listOf(
    R.string.profile_faq_cancel_q to R.string.profile_faq_cancel_a,
    R.string.profile_faq_pay_q to R.string.profile_faq_pay_a,
    R.string.profile_faq_pass_q to R.string.profile_faq_pass_a,
    R.string.profile_faq_gate_q to R.string.profile_faq_gate_a,
    R.string.profile_faq_miles_q to R.string.profile_faq_miles_a,
    R.string.profile_faq_travellers_q to R.string.profile_faq_travellers_a,
)

@Composable
internal fun HelpSheet(onDismiss: () -> Unit) {
    val colors = SkypassTheme.colors
    var open by remember { mutableIntStateOf(0) }
    Sheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(R.string.profile_help), stringResource(R.string.profile_help_hint))
            Column(Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface)) {
                Faq.forEachIndexed { index, (question, answer) ->
                    if (index > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(colors.borderSoft))
                    val expanded = open == index
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.SCREEN), label = "faqChevron")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { open = if (expanded) -1 else index }
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(question), style = SkypassTheme.typography.label, color = colors.ink, modifier = Modifier.weight(1f))
                            Icon(SkypassIcons.ChevronDown, contentDescription = null, tint = colors.text2, modifier = Modifier.size(16.dp).rotate(turn))
                        }
                        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Text(
                                stringResource(answer),
                                style = SkypassTheme.typography.bodySm,
                                color = colors.text2,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            Eyebrow(stringResource(R.string.profile_help_demo))
            OutlinedButton(stringResource(R.string.profile_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
