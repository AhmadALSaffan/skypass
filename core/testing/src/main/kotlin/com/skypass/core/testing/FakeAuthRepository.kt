package com.skypass.core.testing

import com.skypass.core.domain.repository.AuthRepository
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository(initial: UserSession? = null) : AuthRepository {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<UserSession?> = state

    /** What the next Google sign-in returns. */
    var googleResult: Outcome<UserSession, AuthError> = Outcome.Success(TINA)
    var guestResult: Outcome<UserSession, AuthError> = Outcome.Success(GUEST)
    var lastGoogleToken: GoogleIdToken? = null
        private set
    var signOutCalls = 0
        private set

    override suspend fun currentSession(): UserSession? = state.value

    override suspend fun signInWithGoogle(token: GoogleIdToken): Outcome<UserSession, AuthError> {
        lastGoogleToken = token
        return googleResult.also { if (it is Outcome.Success) state.value = it.value }
    }

    override suspend fun signInAsGuest(): Outcome<UserSession, AuthError> =
        guestResult.also { if (it is Outcome.Success) state.value = it.value }

    override suspend fun signOut() {
        signOutCalls++
        state.value = null
    }

    companion object {
        val TINA = UserSession("u-1", "tina.anderson@gmail.com", "Tina Anderson", isGuest = false)
        val GUEST = UserSession("u-guest", null, null, isGuest = true)
    }
}
