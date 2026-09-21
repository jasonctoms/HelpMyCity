package dev.helpmycity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.domain.access.isReviewer
import dev.helpmycity.ui.admin.AdminUserScreen
import dev.helpmycity.ui.admin.AdminUsersScreen
import dev.helpmycity.ui.auth.SignInScreen
import dev.helpmycity.ui.components.CityHeader
import dev.helpmycity.ui.components.SyncIndicator
import dev.helpmycity.ui.issues.EditIssueScreen
import dev.helpmycity.ui.issues.IssueBoardScreen
import dev.helpmycity.ui.issues.IssueDetailScreen
import dev.helpmycity.ui.issues.IssueListScreen
import dev.helpmycity.ui.issues.IssueWorkspaceScreen
import dev.helpmycity.ui.issues.NewIssueScreen
import dev.helpmycity.ui.issues.ReviewQueueScreen
import dev.helpmycity.ui.map.IssueMapScreen
import dev.helpmycity.ui.profile.EditProfileScreen
import dev.helpmycity.ui.profile.ProfileScreen
import dev.helpmycity.ui.navigation.AdminUserRoute
import dev.helpmycity.ui.navigation.AdminUsersRoute
import dev.helpmycity.ui.navigation.EditIssueRoute
import dev.helpmycity.ui.navigation.EditProfileRoute
import dev.helpmycity.ui.navigation.IssueBoardRoute
import dev.helpmycity.ui.navigation.IssueDetailRoute
import dev.helpmycity.ui.navigation.IssueListRoute
import dev.helpmycity.ui.navigation.IssueMapRoute
import dev.helpmycity.ui.navigation.NewIssueRoute
import dev.helpmycity.ui.navigation.ProfileRoute
import dev.helpmycity.ui.navigation.ReviewQueueRoute
import dev.helpmycity.ui.navigation.TopLevelRoute
import dev.helpmycity.ui.navigation.navigationSavedStateConfiguration
import dev.helpmycity.ui.navigation.topLevelRoutesFor
import dev.helpmycity.ui.session.SessionViewModel
import dev.helpmycity.ui.theme.HelpMyCityTheme
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.admin_user_title
import helpmycity.shared.generated.resources.admin_users_title
import helpmycity.shared.generated.resources.board_title
import helpmycity.shared.generated.resources.detail_title
import helpmycity.shared.generated.resources.edit_issue_title
import helpmycity.shared.generated.resources.edit_profile_title
import helpmycity.shared.generated.resources.ic_add
import helpmycity.shared.generated.resources.issues_add
import helpmycity.shared.generated.resources.issues_title
import helpmycity.shared.generated.resources.map_title
import helpmycity.shared.generated.resources.new_issue_title
import helpmycity.shared.generated.resources.profile_title
import helpmycity.shared.generated.resources.review_queue_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun App() {
    // Coil ships no network fetcher of its own, so without this every remote
    // image -- the city's header photo, an issue's uploaded picture -- fails
    // silently. Ktor rather than OkHttp because it is the one engine set that
    // covers all three targets, and the project already carries it.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    HelpMyCityTheme {
        val session: SessionViewModel = koinViewModel()
        val currentUser by session.currentUser.collectAsStateWithLifecycle()

        // Submitting requires no account, but the app opens at sign-in so the
        // auth flow is reachable.
        val user = currentUser
        if (user == null) {
            SignInScreen()
        } else {
            SignedInApp(session = session, canReview = user.isReviewer)
        }
    }
}

/**
 * The one breakpoint: above it the tabs and the report button move into the
 * header, and the list and the map sit side by side. Staging that across two
 * thresholds would only surprise someone dragging a window edge twice.
 */
private val WideWindowWidth = 900.dp

/** How wide a pushed screen lets its text run. Forms and prose, not browsing. */
private val ReadingMaxWidth = 900.dp

@Composable
private fun SignedInApp(session: SessionViewModel, canReview: Boolean) {
    val city: CityProfile = koinInject()
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, IssueListRoute)
    val syncStatus by session.syncStatus.collectAsStateWithLifecycle()
    val currentRoute = backStack.lastOrNull()
    val topLevelRoute = currentRoute as? TopLevelRoute

    // A role can narrow under the app, and the review queue must not stay on
    // screen when it does.
    LaunchedEffect(canReview, currentRoute) {
        if (!canReview && currentRoute == ReviewQueueRoute) backStack.switchTopLevelTo(IssueListRoute)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideWindowWidth
        val inHeader = wide && topLevelRoute != null
        val tabs = topLevelRoutesFor(canReview, listAndMapCombined = wide)

        // The map tab is gone on a wide window because the list draws the map
        // too, so a back stack left on it would select no tab at all.
        LaunchedEffect(wide, currentRoute) {
            if (wide && currentRoute == IssueMapRoute) backStack.switchTopLevelTo(IssueListRoute)
        }

        Scaffold(
            topBar = {
                CityHeader(
                    cityName = city.displayName,
                    branding = city.branding,
                    screenTitle = titleFor(currentRoute),
                    tabs = tabs,
                    selectedTab = topLevelRoute,
                    onTabClick = { backStack.switchTopLevelTo(it) },
                    showTabs = inHeader,
                    onBack = if (topLevelRoute == null) {
                        { backStack.removeLastOrNull() }
                    } else {
                        null
                    },
                    onProfileClick = if (topLevelRoute != null) {
                        { backStack.add(ProfileRoute) }
                    } else {
                        null
                    },
                    action = if (inHeader) {
                        { ReportButton(onClick = { backStack.add(NewIssueRoute) }) }
                    } else {
                        null
                    },
                    status = { SyncIndicator(syncStatus, onSyncNow = session::syncNow) },
                )
            },
            bottomBar = {
                if (topLevelRoute != null && !inHeader) {
                    NavigationBar {
                        tabs.forEach { route ->
                            NavigationBarItem(
                                selected = route == topLevelRoute,
                                onClick = { backStack.switchTopLevelTo(route) },
                                icon = {
                                    Icon(
                                        painter = painterResource(route.iconResource),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(route.labelResource)) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (topLevelRoute != null && !inHeader) {
                    ExtendedFloatingActionButton(
                        onClick = { backStack.add(NewIssueRoute) },
                        icon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_add),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(Res.string.issues_add)) },
                    )
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            // Scopes each screen's ViewModel to its back stack entry, so
                            // popping a detail screen actually clears its state.
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            entry<IssueListRoute> {
                                if (wide) {
                                    IssueWorkspaceScreen(
                                        listViewModel = koinViewModel(),
                                        mapViewModel = koinViewModel(),
                                        onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                    )
                                } else {
                                    IssueListScreen(
                                        viewModel = koinViewModel(),
                                        onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                    )
                                }
                            }
                            entry<IssueBoardRoute> {
                                IssueBoardScreen(
                                    viewModel = koinViewModel(),
                                    onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                )
                            }
                            entry<IssueMapRoute> {
                                // Reachable for the frame between a window widening and
                                // the redirect above, so it draws the workspace too
                                // rather than flashing a layout on its way out.
                                if (wide) {
                                    IssueWorkspaceScreen(
                                        listViewModel = koinViewModel(),
                                        mapViewModel = koinViewModel(),
                                        onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                    )
                                } else {
                                    IssueMapScreen(
                                        viewModel = koinViewModel(),
                                        onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                    )
                                }
                            }
                            entry<ReviewQueueRoute> {
                                ReviewQueueScreen(
                                    viewModel = koinViewModel(),
                                    onIssueClick = { backStack.add(IssueDetailRoute(it)) },
                                )
                            }
                            entry<IssueDetailRoute> { route ->
                                ReadingPane {
                                    IssueDetailScreen(
                                        viewModel = koinViewModel { parametersOf(route.issueId) },
                                        onEditClick = { backStack.add(EditIssueRoute(it)) },
                                    )
                                }
                            }
                            entry<EditIssueRoute> { route ->
                                ReadingPane {
                                    EditIssueScreen(
                                        viewModel = koinViewModel { parametersOf(route.issueId) },
                                        // Both ways out return to the issue, where the edit
                                        // shows as recorded.
                                        onSaved = { backStack.removeLastOrNull() },
                                        onCancel = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            entry<ProfileRoute> {
                                ReadingPane {
                                    ProfileScreen(
                                        viewModel = koinViewModel(),
                                        onEditProfile = { backStack.add(EditProfileRoute) },
                                        onManageUsers = { backStack.add(AdminUsersRoute) },
                                        // Back to the issue list before the session ends, so
                                        // signing in again does not reopen a stale profile.
                                        onSignOut = {
                                            backStack.switchTopLevelTo(IssueListRoute)
                                            session.signOut()
                                        },
                                    )
                                }
                            }
                            entry<EditProfileRoute> {
                                ReadingPane {
                                    EditProfileScreen(
                                        viewModel = koinViewModel(),
                                        onSaved = { backStack.removeLastOrNull() },
                                        onCancel = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            entry<AdminUsersRoute> {
                                ReadingPane {
                                    AdminUsersScreen(
                                        viewModel = koinViewModel(),
                                        onUserClick = { backStack.add(AdminUserRoute(it)) },
                                    )
                                }
                            }
                            entry<AdminUserRoute> { route ->
                                ReadingPane {
                                    AdminUserScreen(
                                        viewModel = koinViewModel { parametersOf(route.userId) },
                                        onSaved = { backStack.removeLastOrNull() },
                                        onCancel = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            entry<NewIssueRoute> {
                                ReadingPane {
                                    NewIssueScreen(
                                        viewModel = koinViewModel(),
                                        onSubmitted = { issueId ->
                                            // Replace the form with the issue it created, so
                                            // "back" returns to the list rather than a blank form.
                                            backStack.removeLastOrNull()
                                            backStack.add(IssueDetailRoute(issueId))
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * A pushed screen, held to [ReadingMaxWidth] and centered.
 *
 * Per entry rather than around the whole `NavDisplay`: a transition has two
 * screens on it at once, so capping the display would narrow the top-level
 * screen sliding out as well.
 */
@Composable
private fun ReadingPane(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = ReadingMaxWidth).fillMaxSize()) {
            content()
        }
    }
}

/** The primary call to action, colored for sitting on the header photo. */
@Composable
private fun ReportButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            // Fixed rather than themed: on a photograph in both schemes, a
            // scheme-dependent label would wash out in one of them.
            contentColor = Color(0xFF00344C),
        ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.issues_add))
    }
}

/**
 * Tabs replace the stack rather than pushing onto it: with three peer
 * destinations, per-tab history would surprise more than it helps.
 */
private fun NavBackStack<NavKey>.switchTopLevelTo(route: TopLevelRoute) {
    clear()
    add(route)
}

@Composable
private fun titleFor(route: NavKey?): String = when (route) {
    IssueBoardRoute -> stringResource(Res.string.board_title)
    IssueMapRoute -> stringResource(Res.string.map_title)
    ReviewQueueRoute -> stringResource(Res.string.review_queue_title)
    NewIssueRoute -> stringResource(Res.string.new_issue_title)
    is IssueDetailRoute -> stringResource(Res.string.detail_title)
    is EditIssueRoute -> stringResource(Res.string.edit_issue_title)
    ProfileRoute -> stringResource(Res.string.profile_title)
    EditProfileRoute -> stringResource(Res.string.edit_profile_title)
    AdminUsersRoute -> stringResource(Res.string.admin_users_title)
    is AdminUserRoute -> stringResource(Res.string.admin_user_title)
    else -> stringResource(Res.string.issues_title)
}
