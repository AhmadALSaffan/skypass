package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.datastore.SkypassPreferences
import com.skypass.core.domain.repository.AuthRepository
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** Supabase Auth. The SDK persists and refreshes the session itself. */
internal class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val preferences: SkypassPreferences,
) : AuthRepository {

    override val session: Flow<UserSession?> = supabase.auth.sessionStatus
        .filterNot { it is SessionStatus.Initializing }
        .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.toModel() }
        .distinctUntilChanged()

    /**
     * Who is signed in, without waiting on the network. Supabase's own initialization also refreshes
     * a stale token, a round trip that would hold the splash screen, so this reads the session saved
     * on the phone. Data calls wait for the refresh themselves (see safeCall).
     */
    override suspend fun currentSession(): UserSession? {
        (supabase.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.let { return it.toModel() }
        val saved = supabase.auth.sessionManager.loadSessionOrNull() ?: return null
        saved.user?.let { return it.toModel() }
        supabase.auth.awaitInitialization()
        return supabase.auth.currentUserOrNull()?.toModel()
    }

    override suspend fun signInWithGoogle(token: GoogleIdToken): Outcome<UserSession, AuthError> = attempt {
        supabase.auth.signInWith(IDToken) {
            idToken = token.idToken
            provider = Google
            nonce = token.rawNonce
        }
    }

    override suspend fun signInAsGuest(): Outcome<UserSession, AuthError> = attempt {
        supabase.auth.signInAnonymously()
    }

    override suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
            .onFailure { if (it is CancellationException) throw it }
        // The next account must not see this one's trip.
        preferences.setCachedUpcomingTrip(null)
    }

    private suspend fun attempt(block: suspend () -> Unit): Outcome<UserSession, AuthError> = try {
        block()
        supabase.auth.currentUserOrNull()?.toModel()?.let { Outcome.Success(it) }
            ?: Outcome.Failure(AuthError.Unknown)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestException) {
        Outcome.Failure(AuthError.Network)
    } catch (e: RestException) {
        Log.w(TAG, "Supabase auth refused: ${e.error} ${e.description}", e)
        val text = "${e.error} ${e.description}".lowercase()
        val notConfigured = "disabled" in text || "not enabled" in text || "unsupported" in text
        Outcome.Failure(if (notConfigured) AuthError.NotConfigured else AuthError.Unknown)
    } catch (e: Exception) {
        Log.w(TAG, "Sign-in failed", e)
        Outcome.Failure(AuthError.Unknown)
    }

    private fun UserInfo.toModel() = UserSession(
        id = id,
        email = email?.takeIf { it.isNotBlank() },
        fullName = userMetadata.string("full_name") ?: userMetadata.string("name"),
        isGuest = email.isNullOrBlank() && identities.isNullOrEmpty(),
    )

    private fun JsonObject?.string(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "SupabaseAuth"
    }
}
