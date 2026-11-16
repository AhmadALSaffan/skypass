package com.skypass.core.domain.repository

import com.skypass.core.model.AccountSettings
import com.skypass.core.model.AppError
import com.skypass.core.model.DeviceSettings
import com.skypass.core.model.Outcome
import com.skypass.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Settings kept on the account (`user_settings`). [settings] emits the copy cached on the phone
 * straight away, then the server's after [refresh].
 */
interface AccountSettingsRepository {
    val settings: Flow<AccountSettings>

    suspend fun refresh(): Outcome<AccountSettings, AppError>

    suspend fun update(settings: AccountSettings): Outcome<Unit, AppError>
}

/** Settings for this phone only. */
interface DeviceSettingsRepository {
    val settings: Flow<DeviceSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setBiometricLock(enabled: Boolean)
}
