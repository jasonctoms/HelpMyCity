package dev.helpmycity.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.ic_board
import helpmycity.shared.generated.resources.ic_list
import helpmycity.shared.generated.resources.ic_map
import helpmycity.shared.generated.resources.ic_review
import helpmycity.shared.generated.resources.nav_board
import helpmycity.shared.generated.resources.nav_list
import helpmycity.shared.generated.resources.nav_map
import helpmycity.shared.generated.resources.nav_review
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@Serializable
sealed interface Route : NavKey

/** The destinations reachable from the bottom bar. */
@Serializable
sealed interface TopLevelRoute : Route {
    val labelResource: StringResource
    val iconResource: DrawableResource
}

@Serializable
data object IssueListRoute : TopLevelRoute {
    override val labelResource: StringResource get() = Res.string.nav_list
    override val iconResource: DrawableResource get() = Res.drawable.ic_list
}

@Serializable
data object IssueBoardRoute : TopLevelRoute {
    override val labelResource: StringResource get() = Res.string.nav_board
    override val iconResource: DrawableResource get() = Res.drawable.ic_board
}

@Serializable
data object IssueMapRoute : TopLevelRoute {
    override val labelResource: StringResource get() = Res.string.nav_map
    override val iconResource: DrawableResource get() = Res.drawable.ic_map
}

@Serializable
data object ReviewQueueRoute : TopLevelRoute {
    override val labelResource: StringResource get() = Res.string.nav_review
    override val iconResource: DrawableResource get() = Res.drawable.ic_review
}

@Serializable
data class IssueDetailRoute(val issueId: String) : Route

@Serializable
data object NewIssueRoute : Route

/** Rejected reports, kept out of the tabs. Reached from the profile and the review queue. */
@Serializable
data object RejectedIssuesRoute : Route

/** The manager edit form for one issue. Reached from its detail screen. */
@Serializable
data class EditIssueRoute(val issueId: String) : Route

/**
 * The account hub behind the profile icon: who you are, what that lets you do,
 * the way into user administration and the way out.
 */
@Serializable
data object ProfileRoute : Route

@Serializable
data object EditProfileRoute : Route

/** Admin only. Reached from [ProfileRoute], not from a tab: it is rarely used. */
@Serializable
data object AdminUsersRoute : Route

@Serializable
data class AdminUserRoute(val userId: String) : Route

/**
 * The tabs, for one signed-in user in one window.
 *
 * The review queue is a manager's tab: a resident has nothing to triage, so
 * showing them an empty fourth tab would only raise a question the app then has
 * to answer. Everything else is the same for everyone.
 *
 * [listAndMapCombined] drops the map tab, because on a window that wide
 * [IssueListRoute] already draws the map beside the list -- a tab that switches
 * to what you are already looking at is worse than no tab.
 */
fun topLevelRoutesFor(
    canReview: Boolean,
    listAndMapCombined: Boolean = false,
): List<TopLevelRoute> = buildList {
    add(IssueListRoute)
    if (canReview) add(ReviewQueueRoute)
    add(IssueBoardRoute)
    if (!listAndMapCombined) add(IssueMapRoute)
}

/**
 * Navigation 3 persists the back stack through `SavedState`, which on non-Android
 * targets needs an explicit polymorphic serializer registration for every
 * [NavKey]; the web build's browser history stores back stacks the same way.
 * Forgetting to add a route here fails at runtime on the first save/restore, so
 * keep this list in step with the routes above.
 */
val routeSerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(IssueListRoute::class, IssueListRoute.serializer())
        subclass(IssueBoardRoute::class, IssueBoardRoute.serializer())
        subclass(IssueMapRoute::class, IssueMapRoute.serializer())
        subclass(ReviewQueueRoute::class, ReviewQueueRoute.serializer())
        subclass(IssueDetailRoute::class, IssueDetailRoute.serializer())
        subclass(NewIssueRoute::class, NewIssueRoute.serializer())
        subclass(RejectedIssuesRoute::class, RejectedIssuesRoute.serializer())
        subclass(EditIssueRoute::class, EditIssueRoute.serializer())
        subclass(ProfileRoute::class, ProfileRoute.serializer())
        subclass(EditProfileRoute::class, EditProfileRoute.serializer())
        subclass(AdminUsersRoute::class, AdminUsersRoute.serializer())
        subclass(AdminUserRoute::class, AdminUserRoute.serializer())
    }
}

val navigationSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = routeSerializersModule
}
