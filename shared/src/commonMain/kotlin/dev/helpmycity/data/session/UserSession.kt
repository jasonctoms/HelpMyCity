package dev.helpmycity.data.session

import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * Who the app is working with.
 *
 * This is the one place anything above the data layer asks "who is this, and what
 * are they allowed to do?". [dev.helpmycity.data.auth.AuthService] answers a
 * narrower question (which account signed in), and screens and repositories
 * intentionally do not read it, because an account says nothing about roles or
 * areas.
 *
 * Null when nobody is signed in. A guest is _not_ null, though. They are a real
 * [User] with no stored record, which is why the app can still take their report.
 */
interface UserSession {
    val currentUser: StateFlow<User?>
}
