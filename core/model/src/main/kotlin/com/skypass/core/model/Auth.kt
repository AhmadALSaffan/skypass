package com.skypass.core.model

/** The signed-in traveller. Guests are anonymous Supabase users: they can book, but have no name. */
data class UserSession(
    val id: String,
    val email: String?,
    val fullName: String?,
    val isGuest: Boolean,
) {
    /** "Tina Anderson" → "TA"; falls back to the email's first letter. */
    val initials: String
        get() {
            val fromName = fullName.orEmpty().split(' ').filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
            return fromName.ifEmpty { email?.firstOrNull()?.uppercase() ?: "G" }
        }
}

/**
 * A Google ID token plus the raw nonce it was requested with. Google signs the SHA-256 of the
 * nonce into the token; Supabase hashes [rawNonce] again and compares, so a stolen token can't be
 * replayed by someone else.
 */
data class GoogleIdToken(val idToken: String, val rawNonce: String)

enum class AuthError {
    /** The user closed the Google sheet. Not an error to show. */
    Cancelled,
    /** No Google account on the device. */
    NoGoogleAccount,
    Network,
    /** Missing client ID, or the provider is switched off in Supabase. */
    NotConfigured,
    Unknown,
}
