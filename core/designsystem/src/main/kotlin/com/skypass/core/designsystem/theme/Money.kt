package com.skypass.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.skypass.core.model.DisplayCurrency

/**
 * The currency the traveller chose in Settings. Prices everywhere are US dollars underneath; this
 * only changes how they're shown. Provided once at the root of the app.
 */
val LocalCurrency = staticCompositionLocalOf { DisplayCurrency.USD }

/** [usd] in the traveller's currency: "$1,284", "1.181 €", "£1,014". */
@Composable
@ReadOnlyComposable
fun money(usd: Int): String = LocalCurrency.current.format(usd)
