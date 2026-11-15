package com.skypass.core.domain.repository

import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserSession
import kotlinx.coroutines.flow.Flow

/** Who is signed in. Sessions survive app restarts. */
interface AuthRepository {
    /** Emits the current user, or null when signed out. */
    val session: Flow<UserSession?>

    /** The restored session, after the stored one has been loaded. */
    suspend fun currentSession(): UserSession?

    suspend fun signInWithGoogle(token: GoogleIdToken): Outcome<UserSession, AuthError>

    /** An anonymous account: browse and book without signing in. */
    suspend fun signInAsGuest(): Outcome<UserSession, AuthError>

    suspend fun signOut()
}
