package com.skypass.core.model

import java.text.NumberFormat
import java.util.Locale

/**
 * Currencies prices can be shown in. Fares are priced and charged in US dollars; other currencies
 * are a display conversion at fixed demo rates, and checkout says so.
 */
enum class DisplayCurrency(val code: String, val label: String, val perUsd: Double) {
    USD("USD", "US dollar", 1.0),
    EUR("EUR", "Euro", 0.92),
    GBP("GBP", "British pound", 0.79),
    NZD("NZD", "New Zealand dollar", 1.68);

    /**
     * "$1,284", "€1,181", "£1,014", "NZ$2,157": whole units, like the board. Formatted the US way so
     * a New Zealand dollar never reads as a US one.
     */
    fun format(usd: Int): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US).apply {
            currency = java.util.Currency.getInstance(code)
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return format.format(Math.round(usd * perUsd))
    }

    companion object {
        fun fromCode(code: String?): DisplayCurrency = entries.firstOrNull { it.code == code } ?: USD
    }
}

/** Light, dark, or whatever the phone uses. */
enum class ThemeMode { System, Light, Dark }

/** What Skypass may notify about. Kept on the account, so it follows the traveller between phones. */
data class NotificationSettings(
    val push: Boolean = true,
    val gateAlerts: Boolean = true,
    val priceAlerts: Boolean = false,
)

/** Settings stored on the account. */
data class AccountSettings(
    val notifications: NotificationSettings = NotificationSettings(),
    val currency: DisplayCurrency = DisplayCurrency.USD,
)

/** Settings that belong to this phone only (a theme or a lock makes no sense on another device). */
data class DeviceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val biometricLock: Boolean = false,
)

data class AppSettings(
    val account: AccountSettings = AccountSettings(),
    val device: DeviceSettings = DeviceSettings(),
)
