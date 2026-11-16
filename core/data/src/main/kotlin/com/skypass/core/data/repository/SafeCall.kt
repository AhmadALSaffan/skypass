package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * Runs a Supabase call and turns exceptions into [AppError]s the UI can explain.
 *
 * The app opens on the session saved on the phone without waiting for Supabase to refresh it, so
 * every call first waits for Auth to finish loading: the request then carries a fresh token.
 */
internal suspend fun <T> SupabaseClient.safeCall(block: suspend () -> T): Outcome<T, AppError> = try {
    auth.awaitInitialization()
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpRequestException) {
    Outcome.Failure(AppError.Network)
} catch (e: HttpRequestTimeoutException) {
    Outcome.Failure(AppError.Network)
} catch (e: RestException) {
    Log.w("Skypass", "Supabase refused: ${e.error}", e)
    Outcome.Failure(if (e.statusCode == 401) AppError.Unauthorized else AppError.Unknown(e.error))
} catch (e: Exception) {
    Log.w("Skypass", "Request failed", e)
    Outcome.Failure(AppError.Unknown(e.message))
}

/** The signed-in user's id, once Auth has loaded (and if needed refreshed) the saved session. */
internal suspend fun SupabaseClient.signedInUserId(): String? {
    auth.awaitInitialization()
    return auth.currentUserOrNull()?.id
}
