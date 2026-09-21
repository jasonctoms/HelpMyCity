package dev.helpmycity.data.repository

import dev.helpmycity.data.local.UserLocalDataSource
import dev.helpmycity.domain.model.Account
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.AssignmentOutcome
import dev.helpmycity.domain.repository.ProfileOutcome
import dev.helpmycity.domain.repository.UserRepository
import dev.helpmycity.domain.util.looksLikeEmail
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single(binds = [UserRepository::class])
class DefaultUserRepository(
    private val local: UserLocalDataSource,
) : UserRepository {

    override fun observeUsers(): Flow<List<User>> = local.observeAll()

    override fun observeUser(id: String): Flow<User?> = local.observeById(id)

    override suspend fun getUser(id: String): User? = local.getById(id)

    override suspend fun register(account: Account): User {
        local.getById(account.id)?.let { return it }
        // Matched on the address as well as the id, so a provider that mints a
        // new id every run -- the mock does -- still lands on the record it
        // opened last time, and with it the role and areas it was given.
        val email = account.email?.trim().orEmpty()
        local.getByEmail(email)?.let { return it }

        val role = account.claimedRole ?: UserRole.RESIDENT
        val opened = User(
            id = account.id,
            displayName = account.displayName,
            email = email,
            role = role,
            scope = account.claimedScope?.takeIf { role == UserRole.MANAGER } ?: ManagerScope.None,
        )
        local.upsert(opened)
        return opened
    }

    override suspend fun assign(
        actor: User?,
        userId: String,
        role: UserRole,
        scope: ManagerScope,
    ): AssignmentOutcome {
        if (actor?.role?.canManageConfiguration != true) return AssignmentOutcome.NotPermitted
        val existing = local.getById(userId) ?: return AssignmentOutcome.UserNotFound
        if (userId == actor.id && role != existing.role) {
            return AssignmentOutcome.OwnRoleUnchangeable
        }

        // Only a manager carries areas: an admin already reaches everything, and
        // a resident reaching anything would be a bug waiting to be read back.
        val updated = existing.copy(
            role = role,
            scope = if (role == UserRole.MANAGER) scope else ManagerScope.None,
        )
        if (updated == existing) return AssignmentOutcome.NoChanges

        local.upsert(updated)
        return AssignmentOutcome.Saved
    }

    override suspend fun updateProfile(
        actor: User?,
        displayName: String,
        email: String,
    ): ProfileOutcome {
        val existing = actor?.let { local.getById(it.id) } ?: return ProfileOutcome.UserNotFound

        val name = displayName.trim()
        if (name.isEmpty()) return ProfileOutcome.NameRequired

        val address = email.trim()
        if (!address.looksLikeEmail()) return ProfileOutcome.InvalidEmail
        val clash = local.getByEmail(address)
        if (clash != null && clash.id != existing.id) return ProfileOutcome.EmailTaken

        val updated = existing.copy(displayName = name, email = address)
        if (updated == existing) return ProfileOutcome.NoChanges

        local.upsert(updated)
        return ProfileOutcome.Saved
    }
}
