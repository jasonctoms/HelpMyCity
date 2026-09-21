package dev.helpmycity.data.auth

import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.util.IdGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockAuthServiceTest {

    private fun service(seeded: List<Account> = emptyList()) = MockAuthService(
        object : IdGenerator {
            private var next = 0
            override fun newId(): String = "user-${next++}"
        },
        seededAccounts = seeded,
    )

    private val seededManager = Account(
        id = "demo-manager",
        displayName = "Demo Manager",
        email = "manager@example.com",
        claimedRole = UserRole.MANAGER,
        claimedScope = ManagerScope.Citywide,
    )

    @Test
    fun signUpRejectsAMalformedEmail() = runTest {
        val result = service().signUp("Ana", "not-an-email", "longenough")

        assertEquals(AuthFailureReason.INVALID_EMAIL, assertIs<AuthResult.Failure>(result).reason)
    }

    @Test
    fun signUpRejectsAShortPassword() = runTest {
        val result = service().signUp("Ana", "ana@example.com", "short")

        assertEquals(AuthFailureReason.WEAK_PASSWORD, assertIs<AuthResult.Failure>(result).reason)
    }

    @Test
    fun signUpRejectsAnEmailThatAlreadyHasAnAccount() = runTest {
        val service = service()
        service.signUp("Ana", "ana@example.com", "longenough")

        val result = service.signUp("Ana Again", "ana@example.com", "longenough")

        assertEquals(
            AuthFailureReason.EMAIL_ALREADY_REGISTERED,
            assertIs<AuthResult.Failure>(result).reason,
        )
    }

    @Test
    fun aNewAccountClaimsTheWholeCitySoTheReviewFlowIsReachable() = runTest {
        val service = service()

        val result = service.signUp("Ana", "ana@example.com", "longenough")

        val account = assertIs<AuthResult.Success>(result).account
        assertEquals(UserRole.MANAGER, account.claimedRole)
        assertTrue(account.claimedScope?.citywide == true)
        assertEquals("Ana", service.currentAccount.value?.displayName)
    }

    /** The demo needs all three roles reachable on a fresh install. */
    @Test
    fun theEmailLocalPartPicksTheDemoRole() = runTest {
        val service = service()

        service.signIn("admin@example.com", "longenough")
        assertEquals(UserRole.ADMIN, service.currentAccount.value?.claimedRole)

        service.signIn("resident@example.com", "longenough")
        assertEquals(UserRole.RESIDENT, service.currentAccount.value?.claimedRole)
    }

    /**
     * A demo build's accounts have to keep their ids across a restart: the
     * accounts live in memory and their user records in the database.
     */
    @Test
    fun aSeededAccountSignsInAsItselfRatherThanBeingMinted() = runTest {
        val service = service(seeded = listOf(seededManager))

        service.signIn("Manager@Example.com", "longenough")

        assertEquals(seededManager, service.currentAccount.value)
    }

    @Test
    fun signUpCannotClaimASeededAddress() = runTest {
        val service = service(seeded = listOf(seededManager))

        val result = service.signUp("Impostor", "manager@example.com", "longenough")

        assertEquals(
            AuthFailureReason.EMAIL_ALREADY_REGISTERED,
            assertIs<AuthResult.Failure>(result).reason,
        )
    }

    @Test
    fun guestsClaimNothing() = runTest {
        val service = service()

        service.continueAsGuest()

        val guest = service.currentAccount.value
        assertEquals(true, guest?.isGuest)
        assertNull(guest?.claimedRole)
        assertNull(guest?.claimedScope)
    }

    @Test
    fun signOutClearsTheSession() = runTest {
        val service = service()
        service.signIn("ana@example.com", "longenough")

        service.signOut()

        assertNull(service.currentAccount.value)
    }
}
