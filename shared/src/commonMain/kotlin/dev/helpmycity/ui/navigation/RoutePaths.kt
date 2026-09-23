package dev.helpmycity.ui.navigation

import androidx.navigation3.runtime.NavKey

/** The address of [route] in the web build: what follows the `#`, or `""` for the issue list. */
fun pathOf(route: Route): String = when (route) {
    IssueListRoute -> ""
    ReviewQueueRoute -> "/review"
    IssueBoardRoute -> "/board"
    IssueMapRoute -> "/map"
    is IssueDetailRoute -> "/issues/${route.issueId}"
    is EditIssueRoute -> "/issues/${route.issueId}/edit"
    NewIssueRoute -> "/report"
    RejectedIssuesRoute -> "/rejected"
    ProfileRoute -> "/profile"
    EditProfileRoute -> "/profile/edit"
    AdminUsersRoute -> "/users"
    is AdminUserRoute -> "/users/${route.userId}"
}

/**
 * The back stack an address opens: its screen on top of the screens you would
 * have come through, so the back arrow still leads somewhere after following a
 * link. Anything unrecognised opens the issue list.
 */
fun backStackFor(path: String): List<NavKey> {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val profile = listOf(IssueListRoute, ProfileRoute)
    return when {
        segments == listOf("review") -> listOf(ReviewQueueRoute)
        segments == listOf("board") -> listOf(IssueBoardRoute)
        segments == listOf("map") -> listOf(IssueMapRoute)
        segments == listOf("report") -> listOf(IssueListRoute, NewIssueRoute)
        segments == listOf("rejected") -> listOf(IssueListRoute, RejectedIssuesRoute)
        segments == listOf("profile") -> profile
        segments == listOf("profile", "edit") -> profile + EditProfileRoute
        segments == listOf("users") -> profile + AdminUsersRoute
        segments.size == 2 && segments[0] == "users" ->
            profile + AdminUsersRoute + AdminUserRoute(segments[1])
        segments.size == 2 && segments[0] == "issues" ->
            listOf(IssueListRoute, IssueDetailRoute(segments[1]))
        segments.size == 3 && segments[0] == "issues" && segments[2] == "edit" ->
            listOf(IssueListRoute, IssueDetailRoute(segments[1]), EditIssueRoute(segments[1]))
        else -> listOf(IssueListRoute)
    }
}
