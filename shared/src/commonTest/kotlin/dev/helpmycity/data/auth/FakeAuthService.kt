package dev.helpmycity.data.auth

import dev.helpmycity.domain.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An account session that can be switched at will. [MockAuthService] validates
 * credentials; this one just holds whoever the test says signed in.
 */
class FakeAuthService(
    initialAccount: Account? = null,
    /** What a sign-in hands back. Null keeps every credential failing. */
    var account: Account? = null,
    /** What [continueAsGuest] hands back. */
    var guest: Account? = null,
) : AuthService {

    private val _currentAccount = MutableStateFlow(initialAccount)
    override val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    fun signedInAs(account: Account?) {
        _currentAccount.value = account
    }

    override suspend fun signIn(email: String, password: String): AuthResult = succeedAs(account)

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthResult = succeedAs(account)

    override suspend fun signOut() = signedInAs(null)

    override suspend fun continueAsGuest(): AuthResult = succeedAs(guest)

    private fun succeedAs(account: Account?): AuthResult {
        if (account == null) return AuthResult.Failure(AuthFailureReason.UNKNOWN)
        signedInAs(account)
        return AuthResult.Success(account)
    }
}
