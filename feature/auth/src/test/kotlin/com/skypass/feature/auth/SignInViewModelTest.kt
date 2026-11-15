package com.skypass.feature.auth

import android.content.Context
import app.cash.turbine.test
import com.skypass.core.auth.GoogleIdTokenProvider
import com.skypass.core.domain.usecase.ContinueAsGuestUseCase
import com.skypass.core.domain.usecase.SignInWithGoogleUseCase
import com.skypass.core.domain.usecase.SignOutUseCase
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.testing.FakeAuthRepository
import com.skypass.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SignInViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val auth = FakeAuthRepository()
    private val viewModel = SignInViewModel(
        signInWithGoogle = SignInWithGoogleUseCase(auth),
        continueAsGuest = ContinueAsGuestUseCase(auth),
        signOut = SignOutUseCase(auth),
        googleIdTokens = object : GoogleIdTokenProvider {
            override suspend fun requestIdToken(activityContext: Context) = error("screen-only")
            override suspend fun clearChosenAccount(context: Context) = Unit
        },
    )
    private val token = GoogleIdToken("id-token", "raw-nonce")

    @Test
    fun `google asks the screen for the account sheet and shows signing in`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(SignInEvent.ContinueWithGoogle)
            assertEquals(SignInEffect.LaunchGoogleSheet, awaitItem())
        }
        assertEquals(SignInStage.SigningIn, viewModel.uiState.value.stage)
    }

    @Test
    fun `a google token signs in with the nonce and shows the account`() {
        viewModel.onEvent(SignInEvent.ContinueWithGoogle)
        viewModel.onEvent(SignInEvent.GoogleTokenReceived(Outcome.Success(token)))

        assertEquals(token, auth.lastGoogleToken)
        assertEquals(SignInStage.SignedIn, viewModel.uiState.value.stage)
        assertEquals(FakeAuthRepository.TINA, viewModel.uiState.value.account)
    }

    @Test
    fun `closing the google sheet quietly returns to idle`() {
        viewModel.onEvent(SignInEvent.ContinueWithGoogle)
        viewModel.onEvent(SignInEvent.GoogleTokenReceived(Outcome.Failure(AuthError.Cancelled)))

        assertEquals(SignInStage.Idle, viewModel.uiState.value.stage)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun `server refusal is explained`() {
        auth.googleResult = Outcome.Failure(AuthError.Network)
        viewModel.onEvent(SignInEvent.ContinueWithGoogle)
        viewModel.onEvent(SignInEvent.GoogleTokenReceived(Outcome.Success(token)))

        assertEquals(SignInStage.Idle, viewModel.uiState.value.stage)
        assertEquals(SignInProblem.Offline, viewModel.uiState.value.problem)
    }

    @Test
    fun `continue to skypass goes home`() = runTest {
        viewModel.onEvent(SignInEvent.ContinueWithGoogle)
        viewModel.onEvent(SignInEvent.GoogleTokenReceived(Outcome.Success(token)))
        viewModel.effects.test {
            skipItems(1) // LaunchGoogleSheet
            viewModel.onEvent(SignInEvent.ContinueToApp)
            assertEquals(SignInEffect.NavigateHome, awaitItem())
        }
    }

    @Test
    fun `use another account signs out and forgets the google account`() = runTest {
        viewModel.onEvent(SignInEvent.ContinueWithGoogle)
        viewModel.onEvent(SignInEvent.GoogleTokenReceived(Outcome.Success(token)))
        viewModel.effects.test {
            skipItems(1)
            viewModel.onEvent(SignInEvent.UseAnotherAccount)
            assertEquals(SignInEffect.ForgetGoogleAccount, awaitItem())
        }
        assertEquals(1, auth.signOutCalls)
        assertEquals(SignInStage.Idle, viewModel.uiState.value.stage)
        assertNull(viewModel.uiState.value.account)
    }

    @Test
    fun `guest access switched off is explained as such`() {
        auth.guestResult = Outcome.Failure(AuthError.NotConfigured)
        viewModel.onEvent(SignInEvent.ContinueAsGuest)
        assertEquals(SignInProblem.GuestUnavailable, viewModel.uiState.value.problem)
    }

    @Test
    fun `guest signs in anonymously and goes home`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(SignInEvent.ContinueAsGuest)
            assertEquals(SignInEffect.NavigateHome, awaitItem())
        }
        assertEquals(FakeAuthRepository.GUEST, auth.session.value)
    }
}
