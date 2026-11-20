package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * A person the way this app works with them, meaning what they are allowed to do
 * and where.
 *
 * This is the other half of [Account][dev.helpmycity.domain.model.Account], which
 * is the same person the way the identity provider knows them. Signing in is the
 * account's business, but role and scope are ours. That is why an admin can change
 * them and a sign-in can't.
 */
@Serializable
data class User(
    val id: String,
    val displayName: String,
    /**
     * Null only for a guest. Every stored user has one, because it is how an
     * account that signs in again gets matched back to the same person.
     */
    val email: String?,
    val role: UserRole,
    /** Which issues this manager is responsible for. Ignored for other roles. */
    val scope: ManagerScope = ManagerScope.None,
) {
    /**
     * Someone who tapped "continue without an account". Nothing gets stored for
     * them, so there is nothing for an admin to assign and no profile to edit.
     */
    val isGuest: Boolean get() = email == null
}

@Serializable
enum class UserRole(val storageKey: String) {
    /** Anyone, signed in or not. Can submit issues and browse approved ones. */
    RESIDENT("resident"),

    /**
     * Reviews and triages issues for the areas in [User.scope], which can be the
     * whole city, a set of council districts, a set of neighborhoods, or a
     * department.
     */
    MANAGER("manager"),

    /** Manages departments, users, neighborhoods, and everyone else's role. */
    ADMIN("admin"),
    ;

    val canManageConfiguration: Boolean get() = this == ADMIN

    /** True for roles that can review _something_. Deciding which issues is [ManagerScope]'s job. */
    val isReviewer: Boolean get() = this == MANAGER || this == ADMIN

    companion object {
        fun fromStorageKey(key: String): UserRole =
            entries.firstOrNull { it.storageKey == key } ?: RESIDENT
    }
}

/**
 * The part of the city one manager is responsible for.
 *
 * There are four separate ways to be responsible for an issue, because cities
 * delegate in all four of them. There is the citywide coordinator, the council
 * district manager, the volunteer looking after one neighborhood, and the
 * department lead who owns whatever gets routed to them. Matching any one of them
 * is enough (see [covers]).
 *
 * The ids here are the same stable ids a
 * [CityProfile][dev.helpmycity.deployment.CityProfile] publishes: [Neighborhood.id]
 * for neighborhoods, [Neighborhood.councilDistrict] for districts, and
 * [Department.id] for departments.
 */
@Serializable
data class ManagerScope(
    /** The whole deployment. An admin always has this, no matter what is stored here. */
    val citywide: Boolean = false,
    val districts: Set<String> = emptySet(),
    val neighborhoodIds: Set<String> = emptySet(),
    val departmentIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = !citywide && districts.isEmpty() &&
            neighborhoodIds.isEmpty() && departmentIds.isEmpty()

    /** Whether this scope makes [issue] one of its holder's to review. */
    fun covers(issue: Issue): Boolean = citywide ||
        issue.location.councilDistrict?.let { it in districts } == true ||
        issue.location.neighborhood?.let { it in neighborhoodIds } == true ||
        issue.departmentId?.let { it in departmentIds } == true

    companion object {
        /** Responsible for nothing, which is what a resident has. */
        val None: ManagerScope = ManagerScope()

        val Citywide: ManagerScope = ManagerScope(citywide = true)
    }
}
