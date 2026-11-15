package com.skypass.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.auth.GoogleIdTokenProvider
import com.skypass.core.domain.usecase.ContinueAsGuestUseCase
import com.skypass.core.domain.usecase.SignInWithGoogleUseCase
import com.skypass.core.domain.usecase.SignOutUseCase
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The board's three states for the Google button. */
enum class SignInStage { Idle, SigningIn, SignedIn }

/** Why sign-in didn't work, for the snackbar. */
enum class SignInProblem { Offline, NoGoogleAccount, NotConfigured, GuestUnavailable, Other }

data class SignInUiState(
    val stage: SignInStage = SignInStage.Idle,
    val account: UserSession? = null,
    val guestInFlight: Boolean = false,
    val problem: SignInProblem? = null,
) {
    val busy: Boolean get() = stage == SignInStage.SigningIn || guestInFlight
}

sealed interface SignInEvent {
    data object ContinueWithGoogle : SignInEvent
    data class GoogleTokenReceived(val result: Outcome<GoogleIdToken, AuthError>) : SignInEvent
    data object ContinueAsGuest : SignInEvent
    data object ContinueToApp : SignInEvent
    data object UseAnotherAccount : SignInEvent
    data object ProblemShown : SignInEvent
}

sealed interface SignInEffect {
    /** Show the Google account sheet (needs the Activity, so the screen does it). */
    data object LaunchGoogleSheet : SignInEffect
    data object ForgetGoogleAccount : SignInEffect
    data object NavigateHome : SignInEffect
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val continueAsGuest: ContinueAsGuestUseCase,
    private val signOut: SignOutUseCase,
    /** Used by the screen, which has the Activity context. */
    val googleIdTokens: GoogleIdTokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SignInEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            SignInEvent.ContinueWithGoogle -> startGoogle()
            is SignInEvent.GoogleTokenReceived -> finishGoogle(event.result)
            SignInEvent.ContinueAsGuest -> guest()
            SignInEvent.ContinueToApp -> send(SignInEffect.NavigateHome)
            SignInEvent.UseAnotherAccount -> useAnotherAccount()
            SignInEvent.ProblemShown -> _uiState.update { it.copy(problem = null) }
        }
    }

    private fun startGoogle() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(stage = SignInStage.SigningIn, problem = null) }
        send(SignInEffect.LaunchGoogleSheet)
    }

    private fun finishGoogle(result: Outcome<GoogleIdToken, AuthError>) {
        when (result) {
            is Outcome.Failure -> fail(result.error)
            is Outcome.Success -> viewModelScope.launch {
                when (val signedIn = signInWithGoogle(result.value)) {
                    is Outcome.Success -> _uiState.update {
                        it.copy(stage = SignInStage.SignedIn, account = signedIn.value)
                    }
                    is Outcome.Failure -> fail(signedIn.error)
                }
            }
        }
    }

    private fun guest() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(guestInFlight = true, problem = null) }
        viewModelScope.launch {
            when (val result = continueAsGuest()) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(guestInFlight = false) }
                    _effects.send(SignInEffect.NavigateHome)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(guestInFlight = false) }
                    if (result.error == AuthError.NotConfigured) {
                        _uiState.update { it.copy(problem = SignInProblem.GuestUnavailable) }
                    } else {
                        fail(result.error)
                    }
                }
            }
        }
    }

    private fun useAnotherAccount() {
        viewModelScope.launch {
            signOut()
            _uiState.update { it.copy(stage = SignInStage.Idle, account = null) }
            _effects.send(SignInEffect.ForgetGoogleAccount)
        }
    }

    private fun fail(error: AuthError) {
        val problem = when (error) {
            AuthError.Cancelled -> null
            AuthError.Network -> SignInProblem.Offline
            AuthError.NoGoogleAccount -> SignInProblem.NoGoogleAccount
            AuthError.NotConfigured -> SignInProblem.NotConfigured
            AuthError.Unknown -> SignInProblem.Other
        }
        _uiState.update { it.copy(stage = SignInStage.Idle, problem = problem) }
    }

    private fun send(effect: SignInEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
