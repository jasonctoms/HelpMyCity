package dev.helpmycity.data.session

import dev.helpmycity.data.auth.FakeAuthService
import dev.helpmycity.data.local.InMemoryUserLocalDataSource
import dev.helpmycity.data.repository.DefaultUserRepository
import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUserSessionTest {

    private val local = InMemoryUserLocalDataSource()
    private val users = DefaultUserRepository(local)

    private val admin = User("admin-1", "Admin", "admin@example.com", UserRole.ADMIN)

    /** What the identity provider hands back for jane@example.com. */
    private val janesAccount = Account(
        id = "account-id",
        displayName = "Jane",
        email = "jane@example.com",
        claimedRole = UserRole.MANAGER,
        claimedScope = ManagerScope.Citywide,
    )

    private val accounts = FakeAuthService(account = janesAccount)

    /** The session resolves on the scope it was given, so let it run before asserting. */
    private suspend fun TestScope.signInAndSettle(session: DefaultUserSession): User? {
        accounts.signIn("jane@example.com", "password")
        runCurrent()
        return session.currentUser.value
    }

    private fun TestScope.session() = DefaultUserSession(accounts, users, backgroundScope)

    @Test
    fun `a first sign-in opens a user seeded from what the provider claims`() = runTest {
        val session = session()

        val user = signInAndSettle(session)

        assertEquals("Jane", user?.displayName)
        assertEquals(UserRole.MANAGER, user?.role)
        assertEquals(true, user?.scope?.citywide)
        assertEquals(user, local.getById(janesAccount.id))
    }

    @Test
    fun `a claim never overrules the user record an admin already set`() = runTest {
        local.upsert(
            User(
                id = "user-id",
                displayName = "Jane",
                email = "jane@example.com",
                role = UserRole.MANAGER,
                scope = ManagerScope(districts = setOf("District 2")),
            )
        )
        val session = session()

        val user = signInAndSettle(session)

        // Matched on the address: the account's own id is a fresh one this run.
        assertEquals("user-id", user?.id)
        assertEquals(setOf("District 2"), user?.scope?.districts)
        assertEquals(false, user?.scope?.citywide)
        assertEquals(1, local.count())
    }

    @Test
    fun `an assignment made while signed in reaches the open session`() = runTest {
        val session = session()
        signInAndSettle(session)

        users.assign(
            actor = admin,
            userId = janesAccount.id,
            role = UserRole.MANAGER,
            scope = ManagerScope(neighborhoodIds = setOf("nbhd-eastside")),
        )
        runCurrent()

        val user = session.currentUser.value
        assertEquals(setOf("nbhd-eastside"), user?.scope?.neighborhoodIds)
        assertEquals(false, user?.scope?.citywide)
    }

    @Test
    fun `a guest is a resident with nothing stored`() = runTest {
        accounts.guest = Account(id = "guest-1", displayName = "Guest", email = null)
        val session = session()

        accounts.continueAsGuest()
        runCurrent()

        val user = session.currentUser.value
        assertEquals(UserRole.RESIDENT, user?.role)
        assertEquals(true, user?.isGuest)
        assertEquals(0, local.count())
    }

    @Test
    fun `the session ends when the account does`() = runTest {
        val session = session()
        signInAndSettle(session)

        // Not a sign-out call on the session: a provider can end an account by
        // itself -- an expired token -- and that has to end this too.
        accounts.signedInAs(null)
        runCurrent()

        assertNull(session.currentUser.value)
    }
}
