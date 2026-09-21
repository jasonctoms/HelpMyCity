package dev.helpmycity.data.auth

import dev.helpmycity.domain.model.Account
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication, abstracted away from whoever provides it.
 *
 * Deals only in [Account]s -- credentials and identity. What the signed-in
 * person may actually do is
 * [dev.helpmycity.data.session.UserSession]'s answer, which is where
 * screens and repositories read from.
 *
 * [MockAuthService] backs the app until a deployment supplies something else
 * through [dev.helpmycity.deployment.BackendProvider]. A hosted
 * identity provider drops in behind this interface without the UI changing;
 * the `supabase` package in `:cityConfig` is a worked example.
 *
 * Submitting an issue deliberately does *not* require an account. Sign-in
 * exists so managers and admins can triage, and so a resident who has an
 * account can follow their own report through review.
 */
interface AuthService {
    val currentAccount: StateFlow<Account?>

    val isSignedIn: Boolean get() = currentAccount.value != null

    suspend fun signIn(email: String, password: String): AuthResult

    suspend fun signUp(displayName: String, email: String, password: String): AuthResult

    suspend fun signOut()

    /** Anonymous session for residents who only want to report and browse. */
    suspend fun continueAsGuest(): AuthResult
}

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Failure(val reason: AuthFailureReason) : AuthResult
}

enum class AuthFailureReason {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}
