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
 * It validates shapes (email looks like an email, password is long enough) so
 * the sign-in screen's error states are real and worth designing against, but it
 * accepts any credential that passes those checks and keeps accounts only for
 * the lifetime of the process. **No credential is ever stored, hashed or
 * transmitted** -- there is nothing here to mistake for a security boundary.
 *
 * The role it claims comes from the email's local part, so all three can be
 * tried on a fresh install: `admin@…` signs in as an admin, `resident@…` as a
 * resident, and anything else as a citywide manager. It is only a claim -- see
 * [Account.claimedRole] -- so it seeds a new user and never overrules an
 * assignment an admin has already made. Real deployments claim roles and
 * [ManagerScope]s from their identity provider, never from the address someone
 * typed.
 *
 * Bound by [dev.helpmycity.deployment.LocalOnlyBackend]; a fork that
 * has a real identity provider supplies its own `BackendProvider.auth` instead.
 *
 * @param seededAccounts sign-ins that exist before anyone signs in, with ids
 *   this service does not mint. A demo build passes one per role, which is what
 *   lets those accounts hold the same id across a restart; every other build
 *   passes none.
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
            // A demo manager looks after the whole city: the mock knows nothing
            // about which districts this deployment has.
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

        /** Enough delay that the sign-in button's loading state is actually visible. */
        const val FAKE_LATENCY_MILLIS = 400L
    }
}
