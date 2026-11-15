package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/** Runs a Supabase call and turns exceptions into [AppError]s the UI can explain. */
internal suspend fun <T> safeCall(block: suspend () -> T): Outcome<T, AppError> = try {
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
