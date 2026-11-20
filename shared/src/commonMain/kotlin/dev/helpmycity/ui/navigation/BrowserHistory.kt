package dev.helpmycity.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The back stack the app opens with. In the browser, that is whatever the address
 * bar says (see [backStackFor]), and everywhere else it is the issue list.
 */
expect fun initialBackStack(): List<NavKey>

/**
 * Lets the browser's back and forward buttons move through [backStack], and keeps
 * the address bar in step with the screen being shown. This does nothing outside
 * the browser, where the platform's own back gesture already reaches `NavDisplay`.
 */
@Composable
expect fun BrowserHistory(backStack: NavBackStack<NavKey>)

/** Names the screen in the browser's tab. Does nothing outside the browser. */
@Composable
expect fun BrowserTitle(title: String)
