package dev.helpmycity.data.repository

import dev.helpmycity.data.local.InMemoryUserLocalDataSource
import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.AssignmentOutcome
import dev.helpmycity.domain.repository.ProfileOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultUserRepositoryTest {

    private val local = InMemoryUserLocalDataSource()
    private val repository = DefaultUserRepository(local)

    private val admin = User("admin-1", "Admin", "admin@example.com", UserRole.ADMIN)
    private val manager = User(
        id = "manager-1",
        displayName = "Jane",
        email = "jane@example.com",
        role = UserRole.MANAGER,
        scope = ManagerScope.Citywide,
    )
    private val resident = User("resident-1", "Ana", "ana@example.com", UserRole.RESIDENT)

    private suspend fun seed(vararg people: User) = people.forEach { local.upsert(it) }

    @Test
    fun `registering an unknown account opens a user from its claims`() = runTest {
        val opened = repository.register(
            Account(
                id = "account-1",
                displayName = "Jane",
                email = "jane@example.com",
                claimedRole = UserRole.MANAGER,
                claimedScope = ManagerScope.Citywide,
            )
        )

        assertEquals(UserRole.MANAGER, opened.role)
        assertTrue(opened.scope.citywide)
        assertEquals(opened, local.getById("account-1"))
    }

    @Test
    fun `an account that claims nothing opens a resident`() = runTest {
        val opened = repository.register(
            Account(id = "account-1", displayName = "Ana", email = "ana@example.com")
        )

        assertEquals(UserRole.RESIDENT, opened.role)
        assertTrue(opened.scope.isEmpty)
    }

    @Test
    fun `a returning account keeps the role and areas an admin gave it`() = runTest {
        seed(manager.copy(scope = ManagerScope(districts = setOf("District 2"))))

        // A new session, so the mock provider minted a new id and claimed a role.
        val returning = Account(
            id = "fresh-id",
            displayName = "Jane",
            email = "JANE@example.com",
            claimedRole = UserRole.RESIDENT,
        )
        val user = repository.register(returning)

        assertEquals(manager.id, user.id)
        assertEquals(UserRole.MANAGER, user.role)
        assertEquals(setOf("District 2"), user.scope.districts)
        assertEquals(1, local.count())
    }

    @Test
    fun `only an admin may assign`() = runTest {
        seed(manager, resident)

        val outcome = repository.assign(
            actor = manager,
            userId = resident.id,
            role = UserRole.ADMIN,
            scope = ManagerScope.None,
        )

        assertEquals(AssignmentOutcome.NotPermitted, outcome)
        assertEquals(UserRole.RESIDENT, local.getById(resident.id)?.role)
    }

    @Test
    fun `an admin cannot change their own role`() = runTest {
        seed(admin)

        val outcome = repository.assign(admin, admin.id, UserRole.RESIDENT, ManagerScope.None)

        assertEquals(AssignmentOutcome.OwnRoleUnchangeable, outcome)
        assertEquals(UserRole.ADMIN, local.getById(admin.id)?.role)
    }

    @Test
    fun `assigns districts and neighborhoods to a manager`() = runTest {
        seed(admin, resident)

        val outcome = repository.assign(
            actor = admin,
            userId = resident.id,
            role = UserRole.MANAGER,
            scope = ManagerScope(
                districts = setOf("District 2"),
                neighborhoodIds = setOf("nbhd-eastside"),
            ),
        )

        assertEquals(AssignmentOutcome.Saved, outcome)
        val updated = local.getById(resident.id)
        assertEquals(UserRole.MANAGER, updated?.role)
        assertEquals(setOf("District 2"), updated?.scope?.districts)
        assertEquals(setOf("nbhd-eastside"), updated?.scope?.neighborhoodIds)
    }

    @Test
    fun `a role that reviews nothing keeps no areas`() = runTest {
        seed(admin, manager)

        val outcome = repository.assign(
            actor = admin,
            userId = manager.id,
            role = UserRole.RESIDENT,
            scope = ManagerScope(districts = setOf("District 2")),
        )

        assertEquals(AssignmentOutcome.Saved, outcome)
        assertTrue(local.getById(manager.id)!!.scope.isEmpty)
    }

    @Test
    fun `assigning what is already there changes nothing`() = runTest {
        seed(admin, manager)

        val outcome = repository.assign(admin, manager.id, UserRole.MANAGER, ManagerScope.Citywide)

        assertEquals(AssignmentOutcome.NoChanges, outcome)
    }

    @Test
    fun `a profile edit refuses a blank name and a broken address`() = runTest {
        seed(resident)

        assertEquals(
            ProfileOutcome.NameRequired,
            repository.updateProfile(resident, "   ", "ana@example.com"),
        )
        assertEquals(
            ProfileOutcome.InvalidEmail,
            repository.updateProfile(resident, "Ana", "ana-at-example"),
        )
    }

    @Test
    fun `a profile edit refuses an address another account signs in with`() = runTest {
        seed(resident, manager)

        val outcome = repository.updateProfile(resident, "Ana", "jane@example.com")

        assertEquals(ProfileOutcome.EmailTaken, outcome)
        assertEquals("ana@example.com", local.getById(resident.id)?.email)
    }

    @Test
    fun `a profile edit leaves the role and areas alone`() = runTest {
        seed(manager)

        val outcome = repository.updateProfile(manager, "  Jane Doe  ", " jane.doe@example.com ")

        assertEquals(ProfileOutcome.Saved, outcome)
        val updated = local.getById(manager.id)
        assertEquals("Jane Doe", updated?.displayName)
        assertEquals("jane.doe@example.com", updated?.email)
        assertEquals(UserRole.MANAGER, updated?.role)
        assertTrue(updated!!.scope.citywide)
    }

    @Test
    fun `nobody signed in has no profile to edit`() = runTest {
        assertEquals(
            ProfileOutcome.UserNotFound,
            repository.updateProfile(null, "Ana", "ana@example.com"),
        )
        assertNull(local.getById(resident.id))
    }
}
