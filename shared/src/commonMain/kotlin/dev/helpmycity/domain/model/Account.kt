package dev.helpmycity.domain.model

/**
 * A person as the identity provider knows them: who signed in, not what they may
 * do here.
 *
 * The counterpart to [User]. An account proves an identity; a [User] record is
 * what this deployment decided about it, and the two are joined by
 * [dev.helpmycity.data.session.UserSession]. Keeping them apart is
 * what lets an admin change a role without the next sign-in undoing it.
 */
data class Account(
    val id: String,
    val displayName: String,
    /** Null for a guest, who has no identity to prove and nothing to store. */
    val email: String?,

    /**
     * What the provider says this person may do -- a role and a scope claim
     * carried on the account, in the `:cityConfig` example.
     *
     * Read exactly once, when an account is first registered as a [User]. After
     * that this app's own record wins, so an assignment made here is not
     * overwritten every time its holder signs in. Null when the provider says
     * nothing, which leaves a new user a resident.
     */
    val claimedRole: UserRole? = null,
    val claimedScope: ManagerScope? = null,
) {
    val isGuest: Boolean get() = email == null
}
