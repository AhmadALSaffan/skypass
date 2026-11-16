package com.skypass.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class VerifyResult { Verified, Cancelled, Unavailable }

/**
 * Asks the person holding the phone to prove it's them: fingerprint, face, or the screen lock.
 * The app provides a BiometricPrompt-backed one at its root; screens read it from [LocalUserVerifier].
 */
interface UserVerifier {
    /** False when the phone has no biometrics and no screen lock set up. */
    fun isAvailable(): Boolean

    suspend fun verify(title: String, subtitle: String): VerifyResult
}

/** Previews and tests have no lock: nothing to verify against. */
val LocalUserVerifier = staticCompositionLocalOf<UserVerifier> {
    object : UserVerifier {
        override fun isAvailable() = false
        override suspend fun verify(title: String, subtitle: String) = VerifyResult.Unavailable
    }
}
