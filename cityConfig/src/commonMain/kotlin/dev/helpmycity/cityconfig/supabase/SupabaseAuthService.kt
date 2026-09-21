package dev.helpmycity.cityconfig.supabase

import dev.helpmycity.data.auth.AuthFailureReason
import dev.helpmycity.data.auth.AuthResult
import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.data.auth.MockAuthService
import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.util.UuidIdGenerator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Sign-in backed by Supabase Auth.
 *
 * [currentAccount] tracks Supabase's own session, so a restored session on app
 * start, a token refresh and a sign-out on another tab all land here without
 * the app polling for them.
 *
 * Roles and scopes are read from **`app_metadata`**, never `user_metadata`. A
 * signed-in user can write their own `user_metadata` through the API, so a role
 * kept there would let anyone make themselves an admin; `app_metadata` is
 * writable only with the service key, from a trusted server. Both are only a
 * *claim* either way -- see [Account.claimedRole]: the app reads them once to
 * seed a new [dev.helpmycity.domain.model.User] and its own record
 * wins from then on.
 *
 * Given no configured client this delegates to [MockAuthService], so selecting
 * the Supabase backend before the project exists leaves a usable app rather
 * than one that refuses every sign-in.
 */
internal class SupabaseAuthService(
    private val client: SupabaseClient?,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthService {

    private val fallback: AuthService? = if (client == null) MockAuthService(UuidIdGenerator) else null

    override val currentAccount: StateFlow<Account?> =
        fallback?.currentAccount ?: client!!.auth.sessionStatus
            .map { status ->
                (status as? SessionStatus.Authenticated)?.session?.user?.toAccount()
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    override suspend fun signIn(email: String, password: String): AuthResult {
        val supabase = client ?: return fallback!!.signIn(email, password)
        return attempt {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthResult {
        val supabase = client ?: return fallback!!.signUp(displayName, email, password)
        return attempt {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Read back as the account's display name. Self-declared, which
                // is why it is a name and not a role.
                data = buildJsonObject { put("display_name", displayName) }
            }
        }
    }

    override suspend fun signOut() {
        val supabase = client ?: return fallback!!.signOut()
        // A failure here means the token is already gone server-side; the local
        // session is cleared either way, so there is nothing to report.
        runCatching { supabase.auth.signOut() }
    }

    override suspend fun continueAsGuest(): AuthResult {
        val supabase = client ?: return fallback!!.continueAsGuest()
        return attempt { supabase.auth.signInAnonymously() }
    }

    // The session is read back rather than taken from a return value, because
    // signInWith and signInAnonymously both return Unit.
    private suspend inline fun attempt(block: () -> Unit): AuthResult = try {
        block()
        val account = (client!!.auth.sessionStatus.value as? SessionStatus.Authenticated)
            ?.session?.user?.toAccount()
        account?.let(AuthResult::Success)
            ?: AuthResult.Failure(AuthFailureReason.UNKNOWN)
    } catch (e: AuthRestException) {
        AuthResult.Failure(e.errorCode.asFailureReason())
    } catch (e: RestException) {
        AuthResult.Failure(AuthFailureReason.UNKNOWN)
    } catch (e: HttpRequestException) {
        AuthResult.Failure(AuthFailureReason.NETWORK_UNAVAILABLE)
    }
}

private fun AuthErrorCode?.asFailureReason(): AuthFailureReason = when (this) {
    AuthErrorCode.InvalidCredentials -> AuthFailureReason.INVALID_CREDENTIALS
    AuthErrorCode.WeakPassword -> AuthFailureReason.WEAK_PASSWORD
    AuthErrorCode.ValidationFailed -> AuthFailureReason.INVALID_EMAIL
    AuthErrorCode.EmailExists,
    AuthErrorCode.UserAlreadyExists,
    -> AuthFailureReason.EMAIL_ALREADY_REGISTERED
    else -> AuthFailureReason.UNKNOWN
}

/**
 * A Supabase user as an [Account].
 *
 * An anonymous user has no email, which is exactly what [Account.isGuest]
 * means, so a guest session needs no special case here.
 */
private fun UserInfo.toAccount(): Account = Account(
    id = id,
    displayName = userMetadata?.string("display_name")
        ?: email?.substringBefore('@')
        ?: "Guest",
    email = email,
    claimedRole = appMetadata?.string("role")?.let { UserRole.fromStorageKey(it) },
    claimedScope = appMetadata?.managerScope(),
)

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * The manager scope claimed in `app_metadata`, if any.
 *
 * Shape, all optional:
 * ```json
 * { "scope": { "citywide": false, "districts": ["3"],
 *              "neighborhood_ids": ["south-oceanside"], "department_ids": ["streets"] } }
 * ```
 */
private fun JsonObject.managerScope(): ManagerScope? {
    val scope = this["scope"]?.jsonObject ?: return null
    return ManagerScope(
        citywide = scope["citywide"]?.jsonPrimitive?.booleanOrNull == true,
        districts = scope.stringSet("districts"),
        neighborhoodIds = scope.stringSet("neighborhood_ids"),
        departmentIds = scope.stringSet("department_ids"),
    ).takeUnless { it.isEmpty }
}

private fun JsonObject.stringSet(key: String): Set<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.toSet()
        .orEmpty()
