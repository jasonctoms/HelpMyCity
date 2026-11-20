package dev.helpmycity.data.auth

import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.util.IdGenerator
import dev.helpmycity.domain.util.looksLikeEmail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory stand-in for real authentication.
 *
 * It checks shapes (the email looks like an email, the password is long enough)
 * so the sign-in screen's error states are real and worth designing for. Beyond
 * that it accepts any credential that passes, and it only keeps accounts for as
 * long as the process is running. **No credential is ever stored, hashed, or
 * sent anywhere**, so please do not mistake any of this for a security boundary.
 *
 * The role it claims comes from the part of the email before the @, so you can
 * try all three on a fresh install. `admin@…` signs in as an admin, `resident@…`
 * as a resident, and anything else as a citywide manager. It is only a claim (see
 * [Account.claimedRole]), so it seeds a new user and never overrides something an
 * admin has already assigned. Real deployments get roles and [ManagerScope]s from
 * their identity provider, never from whatever address someone typed.
 *
 * [dev.helpmycity.deployment.LocalOnlyBackend] binds this. A fork with a real
 * identity provider supplies its own `BackendProvider.auth` instead.
 *
 * @param seededAccounts sign-ins that exist before anyone signs in, with ids this
 *   service did not generate. A demo build passes one per role, which is how those
 *   accounts keep the same id across a restart. Every other build passes none.
 */
class MockAuthService(
    private val idGenerator: IdGenerator,
    seededAccounts: List<Account> = emptyList(),
) : AuthService {

    private val accounts: MutableMap<String, Account> = seededAccounts
        .associateByTo(mutableMapOf()) { it.email.orEmpty().lowercase() }
    private val _currentAccount = MutableStateFlow<Account?>(null)
    override val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    override suspend fun signIn(email: String, password: String): AuthResult {
        delay(FAKE_LATENCY_MILLIS)
        if (!email.looksLikeEmail()) return AuthResult.Failure(AuthFailureReason.INVALID_EMAIL)
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthResult.Failure(AuthFailureReason.INVALID_CREDENTIALS)
        }

        val account = accounts.getOrPut(email.lowercase()) {
            newAccount(displayName = email.substringBefore('@'), email = email)
        }
        _currentAccount.value = account
        return AuthResult.Success(account)
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthResult {
        delay(FAKE_LATENCY_MILLIS)
        if (!email.looksLikeEmail()) return AuthResult.Failure(AuthFailureReason.INVALID_EMAIL)
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthResult.Failure(AuthFailureReason.WEAK_PASSWORD)
        }
        if (accounts.containsKey(email.lowercase())) {
            return AuthResult.Failure(AuthFailureReason.EMAIL_ALREADY_REGISTERED)
        }

        val account = newAccount(
            displayName = displayName.ifBlank { email.substringBefore('@') },
            email = email,
        )
        accounts[email.lowercase()] = account
        _currentAccount.value = account
        return AuthResult.Success(account)
    }

    override suspend fun signOut() {
        _currentAccount.value = null
    }

    override suspend fun continueAsGuest(): AuthResult {
        val guest = Account(id = idGenerator.newId(), displayName = "Guest", email = null)
        _currentAccount.value = guest
        return AuthResult.Success(guest)
    }

    private fun newAccount(displayName: String, email: String): Account {
        val role = roleFor(email)
        return Account(
            id = idGenerator.newId(),
            displayName = displayName,
            email = email,
            claimedRole = role,
            // A demo manager looks after the whole city, since the mock has no
            // idea which districts this deployment has.
            claimedScope = if (role == UserRole.MANAGER) ManagerScope.Citywide else null,
        )
    }

    private fun roleFor(email: String): UserRole =
        when (email.substringBefore('@').substringBefore('+').lowercase()) {
            ADMIN_LOCAL_PART -> UserRole.ADMIN
            RESIDENT_LOCAL_PART -> UserRole.RESIDENT
            else -> UserRole.MANAGER
        }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val ADMIN_LOCAL_PART = "admin"
        const val RESIDENT_LOCAL_PART = "resident"

        /** Just enough delay that you can actually see the sign-in button's loading state. */
        const val FAKE_LATENCY_MILLIS = 400L
    }
}
