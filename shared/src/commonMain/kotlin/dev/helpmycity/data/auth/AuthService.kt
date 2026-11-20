package dev.helpmycity.data.auth

import dev.helpmycity.domain.model.Account
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication, without caring who provides it.
 *
 * This only deals with [Account]s, meaning credentials and identity. What the
 * signed-in person is actually allowed to do is up to
 * [dev.helpmycity.data.session.UserSession], and that is what screens and
 * repositories read from.
 *
 * [MockAuthService] backs the app until a deployment supplies something else
 * through [dev.helpmycity.deployment.BackendProvider]. A hosted identity provider
 * plugs in behind this interface without the UI changing at all, and the
 * `supabase` package in `:cityConfig` is a worked example.
 *
 * Submitting an issue does _not_ need an account, on purpose. Sign-in is there so
 * managers and admins can triage, and so a resident with an account can follow
 * their own report through review.
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
