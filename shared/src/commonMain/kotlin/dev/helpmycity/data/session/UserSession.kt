package dev.helpmycity.data.session

import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * Who the app is working with.
 *
 * The single place anything above the data layer asks "who is this, and what may
 * they do". [dev.helpmycity.data.auth.AuthService] answers a narrower
 * question -- which account authenticated -- and screens and repositories
 * deliberately do not read it: an account says nothing about roles or areas.
 *
 * Null when nobody is signed in. A guest is *not* null: they are a real [User]
 * with no stored record, which is why the app can still take their report.
 */
interface UserSession {
    val currentUser: StateFlow<User?>
}
