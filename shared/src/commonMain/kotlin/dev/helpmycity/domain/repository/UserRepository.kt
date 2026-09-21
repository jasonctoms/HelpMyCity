package dev.helpmycity.domain.repository

import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * The people this deployment knows, and what each of them may do.
 *
 * An [Account] proves an identity; a [User] is what we decided about it -- their
 * role, and for a manager the slice of the city they answer for. This is where
 * that decision is read and written. A hosted identity provider eventually owns
 * it (a role and scope claim on the account, in the `:cityConfig` example) and drops in
 * behind this interface; until then the local database holds it, which is what
 * lets the admin screen work with no backend.
 *
 * The two write paths take the acting [User] rather than reading the session,
 * because [dev.helpmycity.data.session.UserSession] is *built out of*
 * this repository: injecting the session here would close a cycle. The rules are
 * still enforced here rather than in the screens, so a screen that forgets
 * cannot grant anyone anything.
 */
interface UserRepository {
    fun observeUsers(): Flow<List<User>>

    fun observeUser(id: String): Flow<User?>

    suspend fun getUser(id: String): User?

    /**
     * The user record for [account], opening one the first time that account is
     * seen.
     *
     * An existing record is returned untouched, so an admin's assignment
     * outlives the session it was made in and is never overwritten by what the
     * provider claims. Only a brand-new user is seeded from
     * [Account.claimedRole] and [Account.claimedScope].
     */
    suspend fun register(account: Account): User

    /** Admin-only. [scope] is kept only for a [UserRole.MANAGER]; other roles carry none. */
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
     * An admin tried to change their own role. Refused: a deployment whose last
     * admin demotes themselves has no way back into this screen.
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
