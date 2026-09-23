package dev.helpmycity.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

/**
 * A reload keeps the history entry, so its stack comes back whole. Otherwise --
 * a link, a typed address -- the stack is rebuilt from the address.
 */
actual fun initialBackStack(): List<NavKey> {
    val restored = decode(window.history.state)
    return if (restored != null && addressOf(restored) == currentAddress()) restored else backStackFor(currentAddress())
}

@Composable
actual fun BrowserHistory(backStack: NavBackStack<NavKey>) {
    val history = remember(backStack) { BackStackHistory(backStack) }
    DisposableEffect(history) {
        window.addEventListener("popstate", history.onPopState)
        onDispose {
            window.removeEventListener("popstate", history.onPopState)
            // Signing out: the next account starts at the issue list, not where this one left off.
            window.history.replaceState(null, "", rootUrl())
        }
    }
    LaunchedEffect(history) {
        snapshotFlow { backStack.toList() }.collect(history::onStackChanged)
    }
}

@Composable
actual fun BrowserTitle(title: String) {
    LaunchedEffect(title) { document.title = title }
}

private val json = Json { serializersModule = routeSerializersModule }
private val stackSerializer = ListSerializer(PolymorphicSerializer(NavKey::class))

private fun encode(stack: List<NavKey>): String = json.encodeToString(stackSerializer, stack)

private fun decode(state: Any?): List<NavKey>? = (state as? String)
    ?.let { runCatching { json.decodeFromString(stackSerializer, it) }.getOrNull() }
    ?.takeIf { it.isNotEmpty() }

private fun currentAddress(): String = window.location.hash.removePrefix("#").trimEnd('/')

private fun addressOf(stack: List<NavKey>): String = (stack.last() as? Route)?.let(::pathOf) ?: ""

private fun rootUrl(): String = window.location.pathname + window.location.search

private fun urlOf(stack: List<NavKey>): String = addressOf(stack).let { if (it.isEmpty()) rootUrl() else "#$it" }

/**
 * Keeps one history entry per back stack depth, each holding the stack up to
 * that depth, so the browser's current entry always matches what is on screen.
 * Popping in the app walks history back rather than pushing, which keeps the
 * forward button meaning what it says.
 */
private class BackStackHistory(private val backStack: NavBackStack<NavKey>) {
    /** The stack the current history entry holds. */
    private var synced: List<NavKey> = emptyList()

    /** Entries to write once a `history.go` started here lands; the first replaces. */
    private var pendingWrites: List<List<NavKey>>? = null

    val onPopState: (Event) -> Unit = { event ->
        val writes = pendingWrites
        if (writes != null) {
            pendingWrites = null
            write(writes)
            onStackChanged(backStack.toList())
        } else {
            // No state means an address typed or edited by hand.
            val stack = decode((event as PopStateEvent).state)
                ?: backStackFor(currentAddress()).also { window.history.replaceState(encode(it), "", urlOf(it)) }
            if (stack != synced) {
                synced = stack
                backStack.clear()
                backStack.addAll(stack)
            }
        }
    }

    fun onStackChanged(stack: List<NavKey>) {
        if (stack.isEmpty() || stack == synced || pendingWrites != null) return
        if (synced.isEmpty()) {
            // A fresh stack rebuilt from an address gets an entry per screen, so
            // the back arrow has somewhere to go; a reloaded one already has them.
            val restored = decode(window.history.state) == stack
            write(if (restored) listOf(stack) else (1..stack.size).map { stack.take(it) })
            return
        }
        val common = synced.zip(stack).takeWhile { (old, new) -> old == new }.size
        // Entries below `kept` already hold the right stacks; the one at `kept - 1` is rewritten
        // in case the root changed, and everything above it is pushed afresh.
        val kept = maxOf(common, 1)
        val writes = (kept..stack.size).map { stack.take(it) }
        val back = synced.size - kept
        if (back > 0) {
            synced = stack
            pendingWrites = writes
            window.history.go(-back)
        } else {
            write(writes)
        }
    }

    private fun write(stacks: List<List<NavKey>>) {
        stacks.forEachIndexed { index, stack ->
            if (index == 0) {
                window.history.replaceState(encode(stack), "", urlOf(stack))
            } else {
                window.history.pushState(encode(stack), "", urlOf(stack))
            }
        }
        synced = stacks.last()
    }
}
