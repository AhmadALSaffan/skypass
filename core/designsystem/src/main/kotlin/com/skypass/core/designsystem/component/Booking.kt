package com.skypass.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.R
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.ContactDetails

/** A titled block of a booking form or a booking's details: mono eyebrow, then the content. */
@Composable
fun FormSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** A white rounded card for summaries and receipts. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, Shapes.card)
            .border(1.dp, colors.borderSoft, Shapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** One line of a price breakdown: what it is on the left, the amount on the right. */
@Composable
fun PriceLine(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    total: Boolean = false,
    amountColor: Color = SkypassTheme.colors.ink,
) {
    val colors = SkypassTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (total) SkypassTheme.typography.title else SkypassTheme.typography.bodySm,
                color = if (total) colors.ink else colors.text2,
            )
            if (detail != null) Text(detail, style = SkypassTheme.typography.caption, color = colors.text3)
        }
        Text(
            amount,
            style = if (total) SkypassTheme.typography.figureSm else SkypassTheme.typography.labelMd,
            color = amountColor,
        )
    }
}

/** A hairline between breakdown lines and the total. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SkypassTheme.colors.borderSoft))
}

/**
 * A booking policy in one line: a teal check for good news (free cancellation), a muted dot for
 * a rule the traveller should know (non-refundable, bring your licence).
 */
@Composable
fun PolicyNote(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .background(if (positive) colors.mint else colors.surfaceSunken, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (positive) {
                Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(11.dp))
            } else {
                Box(Modifier.size(5.dp).background(colors.text2, CircleShape))
            }
        }
        Text(
            text,
            style = SkypassTheme.typography.bodySm.copy(fontWeight = if (positive) FontWeight.SemiBold else FontWeight.Normal),
            color = if (positive) colors.brand else colors.text2,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A label above its value, as on the board's ticket grid ("CHECK-IN / Sat 21 Nov"). */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = SkypassTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Eyebrow(label, small = true)
        Text(value, style = SkypassTheme.typography.label, color = colors.ink)
        if (detail != null) Text(detail, style = SkypassTheme.typography.caption, color = colors.text2)
    }
}

/**
 * Name, email and optional phone for whoever the stay or car is booked for. Errors show under the
 * field they belong to.
 */
@Composable
fun ContactFields(
    contact: ContactDetails,
    onChange: (ContactDetails) -> Unit,
    modifier: Modifier = Modifier,
    nameError: String? = null,
    emailError: String? = null,
    phoneError: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkypassTextField(
            value = contact.name,
            onValueChange = { onChange(contact.copy(name = it.take(MAX_NAME))) },
            label = stringResource(R.string.contact_name),
            placeholder = stringResource(R.string.contact_name_hint),
            error = nameError,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        SkypassTextField(
            value = contact.email,
            onValueChange = { onChange(contact.copy(email = it.take(MAX_EMAIL).trim())) },
            label = stringResource(R.string.contact_email),
            placeholder = stringResource(R.string.contact_email_hint),
            error = emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        SkypassTextField(
            value = contact.phone,
            onValueChange = { onChange(contact.copy(phone = it.take(MAX_PHONE))) },
            label = stringResource(R.string.contact_phone),
            placeholder = stringResource(R.string.contact_phone_hint),
            error = phoneError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
    }
}

private const val MAX_NAME = 120
private const val MAX_EMAIL = 200
private const val MAX_PHONE = 24
