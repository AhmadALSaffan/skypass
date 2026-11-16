package com.skypass.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.FieldProblem
import com.skypass.core.domain.usecase.PassengerField
import com.skypass.core.model.Gender
import com.skypass.core.model.PassportType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateLabel = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Everything the airline needs, as on the passport: names, gender, passport type, number, issue and
 * expiry dates, plus a phone and email for gate alerts. Every field is required; each shows its own
 * problem in plain words.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PassengerSheet(
    editor: PassengerEditor,
    onChange: (PassengerEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun update(e: PassengerEditor) = onChange(e)
    val problems = editor.problems
    var picking by remember { mutableStateOf<PassengerField?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SkypassTheme.colors.bg,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.passenger_editor_title), style = SkypassTheme.typography.headline, color = SkypassTheme.colors.ink)
            Text(stringResource(R.string.passenger_editor_hint), style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkypassTextField(
                    value = editor.firstName,
                    onValueChange = { update(editor.copy(firstName = it)) },
                    label = stringResource(R.string.passenger_first_name),
                    error = problems[PassengerField.FirstName]?.let { problemText(it) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                SkypassTextField(
                    value = editor.lastName,
                    onValueChange = { update(editor.copy(lastName = it)) },
                    label = stringResource(R.string.passenger_last_name),
                    error = problems[PassengerField.LastName]?.let { problemText(it) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            Labeled(stringResource(R.string.passenger_gender), problems[PassengerField.Gender]) {
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.passenger_gender_female),
                        stringResource(R.string.passenger_gender_male),
                        stringResource(R.string.passenger_gender_unspecified),
                    ),
                    selectedIndex = editor.gender?.ordinal ?: -1,
                    onSelect = { update(editor.copy(gender = Gender.entries[it])) },
                )
            }

            Labeled(stringResource(R.string.passenger_passport_type), problems[PassengerField.PassportType]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PassportType.entries.forEach { type ->
                        FilterChip(passportTypeLabel(type), editor.passportType == type, onClick = { update(editor.copy(passportType = type)) })
                    }
                }
            }

            SkypassTextField(
                value = editor.passportNumber,
                onValueChange = { v -> update(editor.copy(passportNumber = v.uppercase().filter(Char::isLetterOrDigit).take(9))) },
                label = stringResource(R.string.passenger_passport_number),
                placeholder = "LA1234471",
                error = problems[PassengerField.PassportNumber]?.let { problemText(it) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(
                    label = stringResource(R.string.passenger_issue_date),
                    date = editor.issueDate,
                    problem = problems[PassengerField.IssueDate],
                    onClick = { picking = PassengerField.IssueDate },
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = stringResource(R.string.passenger_expiry_date),
                    date = editor.expiryDate,
                    problem = problems[PassengerField.ExpiryDate],
                    onClick = { picking = PassengerField.ExpiryDate },
                    modifier = Modifier.weight(1f),
                )
            }

            SkypassTextField(
                value = editor.phone,
                onValueChange = { v -> update(editor.copy(phone = v.filter { it.isDigit() || it in "+ ()-" }.take(20))) },
                label = stringResource(R.string.passenger_phone),
                placeholder = "+64 21 555 0199",
                error = problems[PassengerField.Phone]?.let { problemText(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            SkypassTextField(
                value = editor.email,
                onValueChange = { update(editor.copy(email = it.trim())) },
                label = stringResource(R.string.passenger_email),
                placeholder = "name@example.com",
                error = problems[PassengerField.Email]?.let { problemText(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )

            PrimaryButton(
                text = stringResource(R.string.passenger_editor_save),
                onClick = onSave,
                loading = editor.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    picking?.let { field ->
        val today = LocalDate.now()
        val isIssue = field == PassengerField.IssueDate
        PassportDatePicker(
            initial = (if (isIssue) editor.issueDate else editor.expiryDate) ?: if (isIssue) today.minusYears(2) else today.plusYears(5),
            earliest = if (isIssue) today.minusYears(15) else today,
            latest = if (isIssue) today else today.plusYears(15),
            onPicked = { date ->
                update(if (isIssue) editor.copy(issueDate = date) else editor.copy(expiryDate = date))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun Labeled(label: String, problem: FieldProblem?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        content()
        if (problem != null) Text(problemText(problem), style = SkypassTheme.typography.caption, color = SkypassTheme.colors.danger)
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, problem: FieldProblem?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.surface, Shapes.field)
                .border(1.5.dp, if (problem != null) colors.danger else colors.border, Shapes.field)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date?.format(DateLabel) ?: stringResource(R.string.passenger_pick_date),
                style = SkypassTheme.typography.bodyStrong,
                color = if (date == null) colors.text2 else colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(SkypassIcons.ChevronDown, contentDescription = null, tint = colors.text2, modifier = Modifier.size(16.dp))
        }
        if (problem != null) Text(problemText(problem), style = SkypassTheme.typography.caption, color = colors.danger)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassportDatePicker(
    initial: LocalDate,
    earliest: LocalDate,
    latest: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SkypassTheme.colors
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.coerceIn(earliest, latest).millis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.date().let { !it.isBefore(earliest) && !it.isAfter(latest) }
            override fun isSelectableYear(year: Int) = year in earliest.year..latest.year
        },
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.surface,
        selectedDayContainerColor = colors.ink,
        selectedDayContentColor = colors.onInk,
        todayDateBorderColor = colors.brand,
        todayContentColor = colors.brand,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SkypassTextButton(stringResource(R.string.passenger_ok), { state.selectedDateMillis?.let { onPicked(it.date()) } ?: onDismiss() }, color = colors.brand)
        },
        dismissButton = { SkypassTextButton(stringResource(R.string.passenger_cancel), onDismiss) },
        colors = pickerColors,
        shape = Shapes.hero,
    ) {
        // Typing a date is quicker than scrolling back years for an issue date.
        DatePicker(state = state, colors = pickerColors, showModeToggle = true)
    }
}

@Composable
private fun passportTypeLabel(type: PassportType) = stringResource(
    when (type) {
        PassportType.Ordinary -> R.string.passenger_passport_ordinary
        PassportType.Diplomatic -> R.string.passenger_passport_diplomatic
        PassportType.Service -> R.string.passenger_passport_service
        PassportType.Official -> R.string.passenger_passport_official
    },
)

@Composable
fun problemText(problem: FieldProblem) = stringResource(
    when (problem) {
        FieldProblem.Required -> R.string.passenger_problem_required
        FieldProblem.Invalid -> R.string.passenger_problem_invalid
        FieldProblem.NotYetIssued -> R.string.passenger_problem_not_issued
        FieldProblem.BeforeIssue -> R.string.passenger_problem_before_issue
        FieldProblem.ExpiresBeforeTrip -> R.string.passenger_problem_expires_before_trip
        FieldProblem.ExpiresWithinSixMonths -> R.string.passenger_problem_six_months
    },
)

private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate) = if (isBefore(min)) min else if (isAfter(max)) max else this
