package com.skypass.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.component.PaymentLogo
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.component.SkypassToggle
import com.skypass.core.designsystem.theme.DmMono
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.CardField
import com.skypass.core.domain.usecase.CardProblem
import com.skypass.core.model.CardBrand
import com.skypass.core.model.CardDraft

/** The add-card form as a screen holds it. */
data class CardForm(
    val draft: CardDraft = CardDraft(),
    val problems: Map<CardField, CardProblem> = emptyMap(),
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /** [changed] with the errors it fixed removed, and each field trimmed to what its brand allows. */
    fun edit(changed: CardDraft): CardForm {
        val brand = changed.brand
        val tidy = changed.copy(
            number = changed.digits.take(brand?.maxLength ?: MAX_DIGITS),
            expiry = changed.expiry.filter(Char::isDigit).take(4),
            cvc = changed.cvc.filter(Char::isDigit).take(brand?.cvcLength ?: 4),
            holder = changed.holder.take(MAX_HOLDER),
        )
        val stillWrong = problems.filterKeys { field -> field.valueIn(draft) == field.valueIn(tidy) }
        return copy(draft = tidy, problems = stillWrong, failed = false)
    }

    private fun CardField.valueIn(d: CardDraft): Any = when (this) {
        CardField.Number -> d.digits
        CardField.Expiry -> d.expiry
        CardField.Cvc -> d.cvc to d.brand
        CardField.Holder -> d.holder
    }

    private companion object {
        const val MAX_DIGITS = 19
        const val MAX_HOLDER = 80
    }
}

/** Shows the digits in the brand's groups: "4242 4242 4242 4242", or "3782 822463 10005" for Amex. */
private class CardNumberTransformation(private val brand: CardBrand?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val groups = brand?.groups ?: listOf(4, 4, 4, 4, 3)
        // Where a space goes, counted in original characters.
        val breaks = groups.runningFold(0) { acc, g -> acc + g }.drop(1)
        val out = StringBuilder()
        text.text.forEachIndexed { i, c ->
            if (i > 0 && i in breaks) out.append(' ')
            out.append(c)
        }
        // A space sits before each break that has a digit after it; spaces[i] is its position in the shown text.
        val inserted = breaks.filter { it in 1 until text.length }
        val spaces = inserted.mapIndexed { i, b -> b + i }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + inserted.count { it < offset }
            override fun transformedToOriginal(offset: Int) = (offset - spaces.count { it < offset }).coerceIn(0, text.length)
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** "0828" shows as "08/28". */
private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val shown = if (t.length > 2) t.take(2) + "/" + t.drop(2) else t
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > 2) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = (if (offset > 2) offset - 1 else offset).coerceAtMost(t.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

@Composable
fun cardProblemText(field: CardField, problem: CardProblem, brand: CardBrand?): String = when (problem) {
    CardProblem.Required -> stringResource(R.string.card_problem_required)
    CardProblem.Unsupported -> stringResource(R.string.card_problem_unsupported)
    CardProblem.Expired -> stringResource(R.string.card_problem_expired)
    CardProblem.Invalid -> when (field) {
        CardField.Number -> stringResource(R.string.card_problem_number)
        CardField.Expiry -> stringResource(R.string.card_problem_expiry)
        CardField.Cvc -> stringResource(R.string.card_problem_cvc, brand?.cvcLength ?: 3)
        CardField.Holder -> stringResource(R.string.card_problem_holder)
    }
}

/**
 * "Add a card". The card on top fills in as you type, the network is recognised from the first
 * digits, and nothing but the last four digits, the expiry and the name is ever saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardSheet(
    form: CardForm,
    onChange: (CardForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveLabel: String = stringResource(R.string.card_save),
) {
    val colors = SkypassTheme.colors
    val draft = form.draft
    val brand = draft.brand
    fun problem(field: CardField) = form.problems[field]
    fun update(d: CardDraft) = onChange(form.edit(d))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.card_add_title), style = SkypassTheme.typography.figure, color = colors.ink)
                Text(stringResource(R.string.card_add_hint), style = SkypassTheme.typography.bodySm, color = colors.text2)
            }
            LiveCard(draft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CardBrand.entries.forEach { b ->
                    PaymentLogo(b.paymentType, width = 44.dp, height = 30.dp, dimmed = brand != null && brand != b)
                }
            }
            SkypassTextField(
                value = draft.digits,
                onValueChange = { update(draft.copy(number = it)) },
                label = stringResource(R.string.card_number),
                placeholder = "1234 5678 9012 3456",
                error = problem(CardField.Number)?.let { cardProblemText(CardField.Number, it, brand) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                visualTransformation = CardNumberTransformation(brand),
                trailing = {
                    AnimatedContent(brand, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() }, label = "brand") { b ->
                        if (b != null) PaymentLogo(b.paymentType, width = 40.dp, height = 26.dp) else Spacer(Modifier.height(26.dp))
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkypassTextField(
                    value = draft.expiry,
                    onValueChange = { update(draft.copy(expiry = it)) },
                    label = stringResource(R.string.card_expiry),
                    placeholder = "MM/YY",
                    error = problem(CardField.Expiry)?.let { cardProblemText(CardField.Expiry, it, brand) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    visualTransformation = ExpiryTransformation,
                    modifier = Modifier.weight(1f),
                )
                SkypassTextField(
                    value = draft.cvc,
                    onValueChange = { update(draft.copy(cvc = it)) },
                    label = stringResource(R.string.card_cvc),
                    placeholder = if (brand == CardBrand.Amex) "1234" else "123",
                    error = problem(CardField.Cvc)?.let { cardProblemText(CardField.Cvc, it, brand) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            SkypassTextField(
                value = draft.holder,
                onValueChange = { update(draft.copy(holder = it)) },
                label = stringResource(R.string.card_holder),
                placeholder = stringResource(R.string.card_holder_hint),
                error = problem(CardField.Holder)?.let { cardProblemText(CardField.Holder, it, brand) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.card_default), style = SkypassTheme.typography.label, color = colors.ink)
                    Text(stringResource(R.string.card_default_hint), style = SkypassTheme.typography.caption, color = colors.text2)
                }
                SkypassToggle(checked = draft.makeDefault, onCheckedChange = { update(draft.copy(makeDefault = it)) })
            }
            PolicyNote(stringResource(R.string.card_security), positive = true)
            if (form.failed) Text(stringResource(R.string.card_save_failed), style = SkypassTheme.typography.bodySm, color = colors.danger)
            PrimaryButton(saveLabel, onClick = onSave, loading = form.saving, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val CardNavy = Color(0xFF1C2550)
private val CardTeal = Color(0xFF0F5B56)

/** A small navy card that fills in as the traveller types. */
@Composable
private fun LiveCard(draft: CardDraft) {
    val brand = draft.brand
    val digits = draft.digits
    val groups = brand?.groups ?: listOf(4, 4, 4, 4)
    // The usual length for the brand (16, or 15 for Amex), growing if a longer number is typed.
    val length = maxOf(digits.length, if (brand == CardBrand.Amex) 15 else 16)
    val padded = digits.padEnd(length, '•')
    val shownNumber = buildString {
        var i = 0
        groups.forEach { g ->
            if (i >= length) return@forEach
            if (isNotEmpty()) append("  ")
            append(padded.substring(i, (i + g).coerceAtMost(length)))
            i += g
        }
    }
    val expiry = draft.expiry.padEnd(4, '•').let { it.take(2) + "/" + it.drop(2) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(Shapes.hero)
            .background(CardNavy)
            .drawBehind {
                drawCircle(CardTeal.copy(alpha = 0.85f), radius = 90.dp.toPx(), center = Offset(size.width + 10.dp.toPx(), 25.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.18f), radius = 70.dp.toPx(), center = Offset(30.dp.toPx(), size.height + 25.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(Modifier.align(Alignment.TopEnd)) {
            AnimatedContent(brand, transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f)) togetherWith fadeOut(tween(120)) }, label = "liveBrand") { b ->
                if (b != null) PaymentLogo(b.paymentType, width = 52.dp, height = 34.dp) else Spacer(Modifier.height(34.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(shownNumber, style = TextStyle(fontFamily = DmMono, fontSize = 16.sp, letterSpacing = 0.04.em), color = Color.White, maxLines = 1)
            Row {
                Text(
                    draft.holder.ifBlank { stringResource(R.string.card_holder_placeholder) }.uppercase(),
                    style = SkypassTheme.typography.captionStrong.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(expiry, style = TextStyle(fontFamily = DmMono, fontSize = 13.sp), color = Color.White)
            }
        }
    }
}
