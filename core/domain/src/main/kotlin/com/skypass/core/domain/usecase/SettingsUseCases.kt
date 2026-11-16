package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.AccountSettingsRepository
import com.skypass.core.domain.repository.DeviceSettingsRepository
import com.skypass.core.domain.repository.EditProfileRepository
import com.skypass.core.model.AccountSettings
import com.skypass.core.model.AppError
import com.skypass.core.model.AppSettings
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.model.NotificationSettings
import com.skypass.core.model.Outcome
import com.skypass.core.model.ThemeMode
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Everything Settings and the app shell need, as one stream. */
class ObserveAppSettingsUseCase @Inject constructor(
    private val account: AccountSettingsRepository,
    private val device: DeviceSettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> =
        combine(account.settings, device.settings) { a, d -> AppSettings(a, d) }.distinctUntilChanged()
}

class RefreshAccountSettingsUseCase @Inject constructor(private val account: AccountSettingsRepository) {
    suspend operator fun invoke(): Outcome<AccountSettings, AppError> = account.refresh()
}

/** Changes one account setting, leaving the others as they are. */
class UpdateAccountSettingsUseCase @Inject constructor(private val account: AccountSettingsRepository) {
    suspend fun notifications(change: (NotificationSettings) -> NotificationSettings): Outcome<Unit, AppError> {
        val current = account.settings.first()
        return account.update(current.copy(notifications = change(current.notifications)))
    }

    suspend fun currency(currency: DisplayCurrency): Outcome<Unit, AppError> =
        account.update(account.settings.first().copy(currency = currency))
}

class SetThemeModeUseCase @Inject constructor(private val device: DeviceSettingsRepository) {
    suspend operator fun invoke(mode: ThemeMode) = device.setThemeMode(mode)
}

class SetBiometricLockUseCase @Inject constructor(private val device: DeviceSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = device.setBiometricLock(enabled)
}

/** Settings' Home airport row: the same field as Personal information, without touching the name. */
class UpdateHomeAirportUseCase @Inject constructor(private val profiles: EditProfileRepository) {
    suspend operator fun invoke(code: String): Outcome<UserProfile, AppError> {
        if (!Regex("^[A-Z]{3}$").matches(code)) return Outcome.Failure(AppError.Unknown("invalid_airport"))
        return profiles.updateHomeAirport(code)
    }
}
