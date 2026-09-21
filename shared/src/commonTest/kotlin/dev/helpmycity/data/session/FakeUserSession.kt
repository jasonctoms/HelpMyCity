package dev.helpmycity.data.session

import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whoever the test says the app is working with, for tests about who may see or
 * do what. No accounts and no registration -- those are
 * [DefaultUserSession]'s job, and it has its own test.
 */
class FakeUserSession(initialUser: User? = null) : UserSession {

    private val _currentUser = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Switches the session, the way signing in and out does at runtime. */
    fun signedInAs(user: User?) {
        _currentUser.value = user
    }
}
