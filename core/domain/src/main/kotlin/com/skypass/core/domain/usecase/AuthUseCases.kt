package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.AuthRepository
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(token: GoogleIdToken): Outcome<UserSession, AuthError> = auth.signInWithGoogle(token)
}

class ContinueAsGuestUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(): Outcome<UserSession, AuthError> = auth.signInAsGuest()
}

class SignOutUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.signOut()
}

class ObserveSessionUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): Flow<UserSession?> = auth.session
}
