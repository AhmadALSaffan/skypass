package com.skypass.core.testing

import com.skypass.core.domain.repository.AccountSettingsRepository
import com.skypass.core.domain.repository.DeviceSettingsRepository
import com.skypass.core.model.AccountSettings
import com.skypass.core.model.AppError
import com.skypass.core.model.DeviceSettings
import com.skypass.core.model.Outcome
import com.skypass.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAccountSettingsRepository(
    initial: AccountSettings = AccountSettings(),
    /** What the server holds; [refresh] copies it into [settings]. */
    var server: AccountSettings = initial,
    var fail: Boolean = false,
) : AccountSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AccountSettings> = state

    override suspend fun refresh(): Outcome<AccountSettings, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        state.value = server
        return Outcome.Success(server)
    }

    override suspend fun update(settings: AccountSettings): Outcome<Unit, AppError> {
        if (fail) return Outcome.Failure(AppError.Network)
        server = settings
        state.value = settings
        return Outcome.Success(Unit)
    }
}

class FakeDeviceSettingsRepository(initial: DeviceSettings = DeviceSettings()) : DeviceSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<DeviceSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setBiometricLock(enabled: Boolean) = state.update { it.copy(biometricLock = enabled) }
}
