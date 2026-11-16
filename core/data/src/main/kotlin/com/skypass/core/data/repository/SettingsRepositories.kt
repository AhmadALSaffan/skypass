package com.skypass.core.data.repository

import com.skypass.core.datastore.CachedAccountSettings
import com.skypass.core.datastore.SkypassPreferences
import com.skypass.core.domain.repository.AccountSettingsRepository
import com.skypass.core.domain.repository.DeviceSettingsRepository
import com.skypass.core.model.AccountSettings
import com.skypass.core.model.AppError
import com.skypass.core.model.DeviceSettings
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.model.NotificationSettings
import com.skypass.core.model.Outcome
import com.skypass.core.model.ThemeMode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class UserSettingsDto(
    @SerialName("push_notifications") val push: Boolean = true,
    @SerialName("gate_alerts") val gateAlerts: Boolean = true,
    @SerialName("price_alerts") val priceAlerts: Boolean = false,
    val currency: String = "USD",
) {
    fun toModel() = AccountSettings(NotificationSettings(push, gateAlerts, priceAlerts), DisplayCurrency.fromCode(currency))
}

/**
 * What an update writes. No default values on purpose: the serializer leaves out fields that equal
 * their default, so a DTO with defaults would silently skip "USD" or "push on".
 */
@Serializable
internal data class UserSettingsUpdateDto(
    @SerialName("push_notifications") val push: Boolean,
    @SerialName("gate_alerts") val gateAlerts: Boolean,
    @SerialName("price_alerts") val priceAlerts: Boolean,
    val currency: String,
) {
    companion object {
        fun from(s: AccountSettings) = UserSettingsUpdateDto(s.notifications.push, s.notifications.gateAlerts, s.notifications.priceAlerts, s.currency.code)
    }
}

private fun AccountSettings.toCache() =
    CachedAccountSettings(currency.code, notifications.push, notifications.gateAlerts, notifications.priceAlerts)

/**
 * `user_settings`, mirrored on the phone: the cache answers immediately (prices show in the right
 * currency from the first frame) and every change is written to both, cache first.
 */
@Singleton
internal class SupabaseAccountSettingsRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val preferences: SkypassPreferences,
) : AccountSettingsRepository {

    override val settings: Flow<AccountSettings> = preferences.cachedAccountSettings.map { c ->
        val defaults = NotificationSettings()
        AccountSettings(
            notifications = NotificationSettings(
                push = c.push ?: defaults.push,
                gateAlerts = c.gateAlerts ?: defaults.gateAlerts,
                priceAlerts = c.priceAlerts ?: defaults.priceAlerts,
            ),
            currency = DisplayCurrency.fromCode(c.currency),
        )
    }.distinctUntilChanged()

    override suspend fun refresh(): Outcome<AccountSettings, AppError> {
        val userId = supabase.signedInUserId() ?: return Outcome.Failure(AppError.Unauthorized)
        val result = supabase.safeCall {
            supabase.from("user_settings").select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<UserSettingsDto>()?.toModel() ?: AccountSettings()
        }
        if (result is Outcome.Success) preferences.cacheAccountSettings(result.value.toCache())
        return result
    }

    override suspend fun update(settings: AccountSettings): Outcome<Unit, AppError> {
        val before = this.settings.first()
        preferences.cacheAccountSettings(settings.toCache())
        val userId = supabase.signedInUserId()
        if (userId == null) {
            preferences.cacheAccountSettings(before.toCache())
            return Outcome.Failure(AppError.Unauthorized)
        }
        val result = supabase.safeCall {
            supabase.from("user_settings").update(UserSettingsUpdateDto.from(settings)) { filter { eq("user_id", userId) } }
            Unit
        }
        // Refused or offline: put the switch back where the server has it.
        if (result is Outcome.Failure) preferences.cacheAccountSettings(before.toCache())
        return result
    }
}

internal class DataStoreDeviceSettingsRepository @Inject constructor(
    private val preferences: SkypassPreferences,
) : DeviceSettingsRepository {

    override val settings: Flow<DeviceSettings> = kotlinx.coroutines.flow.combine(preferences.themeMode, preferences.biometricLock) { theme, lock ->
        DeviceSettings(themeMode = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.System, biometricLock = lock)
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)

    override suspend fun setBiometricLock(enabled: Boolean) = preferences.setBiometricLock(enabled)
}
