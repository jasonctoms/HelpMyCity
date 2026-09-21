package dev.helpmycity.data.session

import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single

/**
 * Joins the account that authenticated to the user record this deployment keeps
 * for it.
 *
 * The join is a subscription, not a lookup, so a role or an area assigned while
 * someone is signed in reaches them without a sign-out -- including an admin
 * editing themselves. An account signing in for the first time is registered as
 * a user on the way through.
 *
 * Eagerly started on the application scope: `currentUser.value` is read
 * synchronously by [dev.helpmycity.data.repository.DefaultIssueRepository]
 * when a report is filed, and nothing downstream is reachable until the app has
 * a user anyway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [UserSession::class])
class DefaultUserSession(
    accounts: AuthService,
    private val users: UserRepository,
    applicationScope: CoroutineScope,
) : UserSession {

    override val currentUser: StateFlow<User?> = accounts.currentAccount
        .flatMapLatest(::userFor)
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    private fun userFor(account: Account?): Flow<User?> = when {
        account == null -> flowOf(null)

        // Nothing to register and nothing to assign, so the account is the whole
        // of what we know about them.
        account.isGuest -> flowOf(
            User(
                id = account.id,
                displayName = account.displayName,
                email = null,
                role = UserRole.RESIDENT,
            )
        )

        else -> flow {
            val registered = users.register(account)
            // Falls back to the registered record if the row is ever missing, so
            // a signed-in account never reads back as signed out.
            emitAll(users.observeUser(registered.id).map { it ?: registered })
        }
    }
}
