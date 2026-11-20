package dev.helpmycity.domain.model

/**
 * A person the way the identity provider knows them. This is who signed in, and
 * says nothing about what they are allowed to do here.
 *
 * It is the other side of [User]. An account proves an identity, a [User] record
 * is what this deployment decided about that identity, and
 * [dev.helpmycity.data.session.UserSession] joins the two. Keeping them separate is
 * what lets an admin change someone's role without the next sign-in undoing it.
 */
data class Account(
    val id: String,
    val displayName: String,
    /** Null for a guest, who has no identity to prove and nothing to store. */
    val email: String?,

    /**
     * What the provider says this person is allowed to do. In the `:cityConfig`
     * example, that is a role and a scope claim on the account.
     *
     * This is read exactly once, when an account is first registered as a [User].
     * After that the app's own record wins, so an assignment made here does not get
     * overwritten every time that person signs in. Null when the provider does not
     * say anything, which makes a new user a resident.
     */
    val claimedRole: UserRole? = null,
    val claimedScope: ManagerScope? = null,
) {
    val isGuest: Boolean get() = email == null
}
