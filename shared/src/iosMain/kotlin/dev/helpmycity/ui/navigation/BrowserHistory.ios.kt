package dev.helpmycity.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

actual fun initialBackStack(): List<NavKey> = listOf(IssueListRoute)

@Composable
actual fun BrowserHistory(backStack: NavBackStack<NavKey>) = Unit

@Composable
actual fun BrowserTitle(title: String) = Unit
