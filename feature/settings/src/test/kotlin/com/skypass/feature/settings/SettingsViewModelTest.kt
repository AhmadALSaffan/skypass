package com.skypass.feature.settings

import app.cash.turbine.test
import com.skypass.core.domain.usecase.GetAirportsUseCase
import com.skypass.core.domain.usecase.GetProfileUseCase
import com.skypass.core.domain.usecase.ObserveAppSettingsUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.SearchAirportsUseCase
import com.skypass.core.domain.usecase.SetBiometricLockUseCase
import com.skypass.core.domain.usecase.SetThemeModeUseCase
import com.skypass.core.domain.usecase.SignOutUseCase
import com.skypass.core.domain.usecase.UpdateAccountSettingsUseCase
import com.skypass.core.domain.usecase.UpdateHomeAirportUseCase
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.model.ThemeMode
import com.skypass.core.model.UserSession
import com.skypass.core.testing.FakeAccountSettingsRepository
import com.skypass.core.testing.FakeAirportRepository
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.FakeDeviceSettingsRepository
import com.skypass.core.testing.FakeEditProfileRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestAirports
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val account = FakeAccountSettingsRepository()
    private val device = FakeDeviceSettingsRepository()
    private val edits = FakeEditProfileRepository()

    private fun viewModel(session: UserSession = FakeAuthRepository.TINA, auth: FakeAuthRepository = FakeAuthRepository(session)) = SettingsViewModel(
        observeSettings = ObserveAppSettingsUseCase(account, device),
        observeSession = ObserveSessionUseCase(auth),
        updateAccount = UpdateAccountSettingsUseCase(account),
        setThemeMode = SetThemeModeUseCase(device),
        setBiometricLock = SetBiometricLockUseCase(device),
        updateHomeAirport = UpdateHomeAirportUseCase(edits),
        getProfile = GetProfileUseCase(FakeProfileRepository()),
        getAirports = GetAirportsUseCase(FakeAirportRepository()),
        searchAirports = SearchAirportsUseCase(),
        signOut = SignOutUseCase(auth),
    )

    @Test
    fun `shows the board's defaults - push and gate on, price drops off, USD, JFK`() {
        val state = viewModel().uiState.value
        assertTrue(state.settings.account.notifications.push)
        assertTrue(state.settings.account.notifications.gateAlerts)
        assertFalse(state.settings.account.notifications.priceAlerts)
        assertEquals(DisplayCurrency.USD, state.settings.account.currency)
        assertEquals("JFK", state.homeAirport)
        assertFalse(state.isGuest)
    }

    @Test
    fun `switches save to the account, one at a time`() {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.SetPriceAlerts(true))
        vm.onEvent(SettingsEvent.SetGateAlerts(false))
        assertTrue(account.server.notifications.priceAlerts)
        assertFalse(account.server.notifications.gateAlerts)
        assertTrue(account.server.notifications.push)
    }

    @Test
    fun `a refused change is reported and the switch stays put`() {
        account.fail = true
        val vm = viewModel()
        vm.onEvent(SettingsEvent.SetPush(false))
        assertTrue(vm.uiState.value.saveFailed)
        assertTrue(vm.uiState.value.settings.account.notifications.push)
    }

    @Test
    fun `currency, appearance and home airport come from their sheets`() {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.OpenCurrency)
        assertEquals(SettingsSheet.Currency, vm.uiState.value.sheet)
        vm.onEvent(SettingsEvent.CurrencyPicked(DisplayCurrency.NZD))
        assertNull(vm.uiState.value.sheet)
        assertEquals(DisplayCurrency.NZD, vm.uiState.value.settings.account.currency)

        vm.onEvent(SettingsEvent.ThemePicked(ThemeMode.Dark))
        assertEquals(ThemeMode.Dark, vm.uiState.value.settings.device.themeMode)

        vm.onEvent(SettingsEvent.OpenHomeAirport)
        vm.onEvent(SettingsEvent.AirportQueryChanged("auck"))
        vm.onEvent(SettingsEvent.AirportPicked(TestAirports.AKL))
        assertEquals("AKL", vm.uiState.value.homeAirport)
        assertEquals(listOf("AKL"), edits.homeAirports)
    }

    @Test
    fun `the biometric lock is saved on this phone once confirmed`() {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.SetBiometricLock(true))
        assertTrue(vm.uiState.value.settings.device.biometricLock)
    }

    @Test
    fun `a Google account signs out at once, a guest is warned first`() = runTest {
        val google = FakeAuthRepository(FakeAuthRepository.TINA)
        val vm = viewModel(auth = google)
        vm.effects.test {
            vm.onEvent(SettingsEvent.SignOut)
            assertEquals(SettingsEffect.SignedOut, awaitItem())
        }
        assertEquals(1, google.signOutCalls)

        val guestAuth = FakeAuthRepository(FakeAuthRepository.GUEST)
        val guest = viewModel(FakeAuthRepository.GUEST, guestAuth)
        guest.onEvent(SettingsEvent.SignOut)
        assertEquals(SettingsSheet.ConfirmGuestSignOut, guest.uiState.value.sheet)
        assertEquals(0, guestAuth.signOutCalls)
        guest.effects.test {
            guest.onEvent(SettingsEvent.ConfirmSignOut)
            assertEquals(SettingsEffect.SignedOut, awaitItem())
        }
        assertEquals(1, guestAuth.signOutCalls)
    }

    @Test
    fun `prices are shown in the chosen currency at the demo rates`() {
        assertEquals("$1,284", DisplayCurrency.USD.format(1284))
        assertEquals("NZ$1,510", DisplayCurrency.NZD.format(899))
        assertEquals("€827", DisplayCurrency.EUR.format(899))
        assertEquals("£710", DisplayCurrency.GBP.format(899))
    }
}
