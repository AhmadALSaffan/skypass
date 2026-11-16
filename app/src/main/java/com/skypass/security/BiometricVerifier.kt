package com.skypass.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.skypass.core.ui.UserVerifier
import com.skypass.core.ui.VerifyResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [UserVerifier] on Android's BiometricPrompt: a fingerprint or face, falling back to the screen
 * lock (PIN, pattern, password), so it also works on phones without biometrics.
 */
class BiometricVerifier(private val activity: FragmentActivity) : UserVerifier {

    override fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun verify(title: String, subtitle: String): VerifyResult {
        if (!isAvailable()) return VerifyResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(VerifyResult.Verified)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Cancelled, timed out or locked out: nothing verified.
                        if (continuation.isActive) continuation.resume(VerifyResult.Cancelled)
                    }
                    // onAuthenticationFailed (a finger not recognised) lets the person try again.
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build(),
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
