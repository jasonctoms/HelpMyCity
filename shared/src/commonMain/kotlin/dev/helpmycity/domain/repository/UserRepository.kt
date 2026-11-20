package dev.helpmycity.domain.repository

import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * The people this deployment knows, and what each of them may do.
 *
 * An [Account] proves an identity, and a [User] is what we decided about it: their
 * role, and for a manager, the part of the city they are responsible for. This is
 * where that decision gets read and written. With a hosted identity provider, the
 * provider owns it (in the `:cityConfig` example, as a role and scope claim on the
 * account) and plugs in behind this interface. Without one, the local database
 * holds it, which is what lets the admin screen work with no backend.
 *
 * The two write paths take the acting [User] as an argument instead of reading the
 * session, because [dev.helpmycity.data.session.UserSession] is _built from_ this
 * repository, and injecting the session here would create a cycle. The rules are
 * still enforced here and not in the screens, so a screen that forgets can't grant
 * anybody anything.
 */
interface UserRepository {
    fun observeUsers(): Flow<List<User>>

    fun observeUser(id: String): Flow<User?>

    suspend fun getUser(id: String): User?

    /**
     * The user record for [account], creating one the first time that account
     * shows up.
     *
     * An existing record comes back untouched, so an admin's assignment outlasts
     * the session it was made in and never gets overwritten by whatever the
     * provider claims. Only a brand-new user gets seeded from [Account.claimedRole]
     * and [Account.claimedScope].
     */
    suspend fun register(account: Account): User

    /** Admin-only. [scope] is only kept for a [UserRole.MANAGER], since the other roles do not have one. */
    suspend fun assign(
        actor: User?,
        userId: String,
        role: UserRole,
        scope: ManagerScope,
    ): AssignmentOutcome

    /** Someone editing their own name and address. Nobody edits anyone else's. */
    suspend fun updateProfile(
        actor: User?,
        displayName: String,
        email: String,
    ): ProfileOutcome
}

/** Why a role or scope assignment did or did not land. */
sealed interface AssignmentOutcome {
    data object Saved : AssignmentOutcome

    /** Saved nothing, because nothing was different. */
    data object NoChanges : AssignmentOutcome

    /** The user is gone, or was never registered. */
    data object UserNotFound : AssignmentOutcome

    /** The caller is not an admin. */
    data object NotPermitted : AssignmentOutcome

    /**
     * An admin tried to change their own role, which is not allowed. If the last
     * admin demoted themselves, the deployment would have no way back into this
     * screen.
     */
    data object OwnRoleUnchangeable : AssignmentOutcome
}

/** Why a profile edit did or did not land. */
sealed interface ProfileOutcome {
    data object Saved : ProfileOutcome

    data object NoChanges : ProfileOutcome

    /** Signed out, or a guest, who has no stored record to edit. */
    data object UserNotFound : ProfileOutcome

    data object NameRequired : ProfileOutcome

    data object InvalidEmail : ProfileOutcome

    /** Somebody else already signs in with that address. */
    data object EmailTaken : ProfileOutcome
}
