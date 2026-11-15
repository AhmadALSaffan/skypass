package com.skypass.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.skypass.core.model.AuthError
import com.skypass.core.model.GoogleIdToken
import com.skypass.core.model.Outcome
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

/**
 * Gets a Google ID token from the system account sheet. Needs an Activity context because it
 * shows UI, so screens call it and hand the result to their ViewModel.
 */
interface GoogleIdTokenProvider {
    suspend fun requestIdToken(activityContext: Context): Outcome<GoogleIdToken, AuthError>

    /** Forget the chosen account so "Use another account" shows the picker again. */
    suspend fun clearChosenAccount(context: Context)
}

/** Credential Manager implementation ("Sign in with Google" button flow). */
internal class CredentialManagerGoogleIdTokenProvider @Inject constructor() : GoogleIdTokenProvider {

    override suspend fun requestIdToken(activityContext: Context): Outcome<GoogleIdToken, AuthError> {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) return Outcome.Failure(AuthError.NotConfigured)

        // Google signs the hash of the nonce into the token; Supabase gets the raw value and checks it.
        val rawNonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(sha256(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                Outcome.Success(GoogleIdToken(idToken = idToken, rawNonce = rawNonce))
            } else {
                Outcome.Failure(AuthError.Unknown)
            }
        } catch (_: GetCredentialCancellationException) {
            Outcome.Failure(AuthError.Cancelled)
        } catch (_: NoCredentialException) {
            Outcome.Failure(AuthError.NoGoogleAccount)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${e.type}", e)
            Outcome.Failure(AuthError.Unknown)
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Invalid Google ID token", e)
            Outcome.Failure(AuthError.Unknown)
        }
    }

    override suspend fun clearChosenAccount(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}
