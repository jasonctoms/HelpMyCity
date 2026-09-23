package dev.helpmycity.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutePathsTest {

    private val everyRoute = listOf(
        IssueListRoute,
        ReviewQueueRoute,
        IssueBoardRoute,
        IssueMapRoute,
        IssueDetailRoute("a1b2"),
        EditIssueRoute("a1b2"),
        NewIssueRoute,
        RejectedIssuesRoute,
        ProfileRoute,
        EditProfileRoute,
        AdminUsersRoute,
        AdminUserRoute("c3d4"),
    )

    @Test
    fun `every route opens itself from its own address`() {
        for (route in everyRoute) {
            assertEquals(route, backStackFor(pathOf(route)).last(), "for ${pathOf(route)}")
        }
    }

    @Test
    fun `a pushed screen opens on top of the screens that lead to it`() {
        assertEquals(
            listOf(IssueListRoute, IssueDetailRoute("a1b2"), EditIssueRoute("a1b2")),
            backStackFor("/issues/a1b2/edit"),
        )
        assertEquals(
            listOf(IssueListRoute, ProfileRoute, AdminUsersRoute, AdminUserRoute("c3d4")),
            backStackFor("/users/c3d4"),
        )
    }

    @Test
    fun `an unknown address opens the issue list`() {
        assertEquals(listOf(IssueListRoute), backStackFor("/nowhere/at/all"))
        assertEquals(listOf(IssueListRoute), backStackFor("/issues"))
    }
}
