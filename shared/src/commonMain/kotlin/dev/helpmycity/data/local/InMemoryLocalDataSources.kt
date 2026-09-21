package dev.helpmycity.data.local

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Non-persistent local store: the fake unit tests run against. */
class InMemoryIssueLocalDataSource : IssueLocalDataSource {
    private val issues = MutableStateFlow<Map<String, Issue>>(emptyMap())
    private val history = MutableStateFlow<List<IssueStatusChange>>(emptyList())
    private val photos = MutableStateFlow<Map<String, IssuePhoto>>(emptyMap())
    private val supports = MutableStateFlow<Map<Pair<String, String>, IssueSupport>>(emptyMap())
    private val writeLock = Mutex()

    override fun observeAll(): Flow<List<Issue>> =
        issues.map { snapshot -> snapshot.values.sortedByDescending(Issue::updatedAtMillis) }

    override fun observeById(id: String): Flow<Issue?> = issues.map { it[id] }

    override fun observeHistory(issueId: String): Flow<List<IssueStatusChange>> =
        history.map { entries ->
            entries.filter { it.issueId == issueId }.sortedByDescending(IssueStatusChange::changedAtMillis)
        }

    override suspend fun getById(id: String): Issue? = issues.value[id]

    override suspend fun upsert(issue: Issue) = writeLock.withLock {
        issues.update { it + (issue.id to issue) }
    }

    override suspend fun upsertAll(issues: List<Issue>) = writeLock.withLock {
        this.issues.update { current -> current + issues.associateBy(Issue::id) }
    }

    override suspend fun delete(id: String) = writeLock.withLock {
        issues.update { it - id }
        history.update { entries -> entries.filterNot { it.issueId == id } }
    }

    override suspend fun deleteSyncedExcept(keep: Set<String>) = writeLock.withLock {
        val dropped = issues.value.values
            .filter { it.sync.state == SyncState.SYNCED && it.id !in keep }
            .mapTo(mutableSetOf(), Issue::id)
        issues.update { it - dropped }
        history.update { entries -> entries.filterNot { it.issueId in dropped } }
        photos.update { all -> all.filterValues { it.issueId !in dropped } }
    }

    override suspend fun appendStatusChange(change: IssueStatusChange) = writeLock.withLock {
        history.update { entries -> entries.filterNot { it.id == change.id } + change }
    }

    override suspend fun mergeRemoteStatusChanges(changes: List<IssueStatusChange>) = writeLock.withLock {
        val merged = changes
            .filter { it.issueId in issues.value }
            .map { it.copy(sync = it.sync.copy(state = SyncState.SYNCED)) }
        val ids = merged.mapTo(mutableSetOf(), IssueStatusChange::id)
        history.update { entries -> entries.filterNot { it.id in ids } + merged }
    }

    override fun observePhotos(issueId: String): Flow<List<IssuePhoto>> =
        photos.map { all ->
            all.values.filter { it.issueId == issueId }.sortedBy(IssuePhoto::createdAtMillis)
        }

    override suspend fun upsertPhoto(photo: IssuePhoto) {
        photos.update { it + (photo.id to photo) }
    }

    override suspend fun deletePhoto(photoId: String) {
        photos.update { it - photoId }
    }

    override suspend fun pendingPhotos(): List<IssuePhoto> =
        photos.value.values
            .filter { it.sync.state != SyncState.SYNCED }
            .sortedBy(IssuePhoto::createdAtMillis)

    override fun observePendingPhotoCount(): Flow<Int> =
        photos.map { all -> all.values.count { it.sync.state != SyncState.SYNCED } }

    override suspend fun markPhotoSynced(photoId: String, remoteUrl: String, syncedAtMillis: Long) {
        photos.update { current ->
            val existing = current[photoId] ?: return@update current
            current + (photoId to existing.copy(
                remoteUrl = remoteUrl,
                sync = existing.sync.copy(
                    state = SyncState.SYNCED,
                    lastSyncedAtMillis = syncedAtMillis,
                ),
            ))
        }
    }

    override suspend fun mergeRemotePhotos(photos: List<IssuePhoto>) = writeLock.withLock {
        this.photos.update { current ->
            current + photos
                .filter { it.remoteUrl != null && it.issueId in issues.value }
                .associate { remote ->
                    remote.id to remote.copy(
                        bytes = current[remote.id]?.bytes,
                        sync = remote.sync.copy(state = SyncState.SYNCED),
                    )
                }
        }
    }

    override suspend fun pendingSync(): List<Issue> =
        issues.value.values.filter { it.sync.state != SyncState.SYNCED }

    override suspend fun pendingStatusChanges(): List<IssueStatusChange> =
        history.value.filter { it.sync.state != SyncState.SYNCED }
            .sortedBy(IssueStatusChange::changedAtMillis)

    override fun observeSupportedIssueIds(userId: String): Flow<Set<String>> =
        supports.map { all -> all.values.filter { it.userId == userId }.mapTo(mutableSetOf(), IssueSupport::issueId) }

    override suspend fun addSupport(support: IssueSupport): Boolean = writeLock.withLock {
        val key = support.issueId to support.userId
        if (key in supports.value) return@withLock false
        supports.update { it + (key to support) }
        issues.update { current ->
            val issue = current[support.issueId] ?: return@update current
            current + (issue.id to issue.copy(supportCount = issue.supportCount + 1))
        }
        true
    }

    override suspend fun pendingSupports(): List<IssueSupport> =
        supports.value.values.filter { it.syncState != SyncState.SYNCED }.sortedBy(IssueSupport::createdAtMillis)

    override fun observePendingSupportCount(): Flow<Int> =
        supports.map { all -> all.values.count { it.syncState != SyncState.SYNCED } }

    override suspend fun markSupportSynced(support: IssueSupport) {
        supports.update { current ->
            val key = support.issueId to support.userId
            val existing = current[key] ?: return@update current
            current + (key to existing.copy(syncState = SyncState.SYNCED))
        }
    }

    override suspend fun mergeRemoteSupports(supports: List<IssueSupport>) {
        this.supports.update { current ->
            current + supports.associate { (it.issueId to it.userId) to it.copy(syncState = SyncState.SYNCED) }
        }
    }

    override suspend fun markSynced(issueId: String, syncedAtMillis: Long, remoteVersion: String?) =
        writeLock.withLock {
            issues.update { current ->
                val existing = current[issueId] ?: return@update current
                current + (
                    issueId to existing.copy(
                        sync = existing.sync.copy(
                            state = SyncState.SYNCED,
                            lastSyncedAtMillis = syncedAtMillis,
                            remoteVersion = remoteVersion,
                        ),
                    )
                    )
            }
        }

    override suspend fun markSyncState(issueId: String, state: SyncState) = writeLock.withLock {
        issues.update { current ->
            val existing = current[issueId] ?: return@update current
            current + (issueId to existing.copy(sync = existing.sync.copy(state = state)))
        }
    }

    override suspend fun count(): Int = issues.value.size
}

class InMemoryDepartmentLocalDataSource : DepartmentLocalDataSource {
    private val departments = MutableStateFlow<Map<String, Department>>(emptyMap())

    override fun observeAll(): Flow<List<Department>> =
        departments.map { it.values.sortedBy(Department::name) }

    override suspend fun getById(id: String): Department? = departments.value[id]

    override suspend fun upsert(department: Department) {
        departments.update { it + (department.id to department) }
    }

    override suspend fun upsertAll(departments: List<Department>) {
        this.departments.update { current -> current + departments.associateBy(Department::id) }
    }

    override suspend fun delete(id: String) {
        departments.update { it - id }
    }

    override suspend fun count(): Int = departments.value.size
}

class InMemoryNeighborhoodLocalDataSource : NeighborhoodLocalDataSource {
    private val neighborhoods = MutableStateFlow<Map<String, Neighborhood>>(emptyMap())

    override fun observeAll(): Flow<List<Neighborhood>> =
        neighborhoods.map { it.values.sortedBy(Neighborhood::name) }

    override suspend fun getById(id: String): Neighborhood? = neighborhoods.value[id]

    override suspend fun upsert(neighborhood: Neighborhood) {
        neighborhoods.update { it + (neighborhood.id to neighborhood) }
    }

    override suspend fun upsertAll(neighborhoods: List<Neighborhood>) {
        this.neighborhoods.update { current -> current + neighborhoods.associateBy(Neighborhood::id) }
    }

    override suspend fun delete(id: String) {
        neighborhoods.update { it - id }
    }

    override suspend fun count(): Int = neighborhoods.value.size
}

class InMemoryUserLocalDataSource : UserLocalDataSource {
    private val users = MutableStateFlow<Map<String, User>>(emptyMap())

    override fun observeAll(): Flow<List<User>> =
        users.map { it.values.sortedBy(User::displayName) }

    override fun observeById(id: String): Flow<User?> = users.map { it[id] }

    override suspend fun getById(id: String): User? = users.value[id]

    override suspend fun getByEmail(email: String): User? =
        users.value.values.firstOrNull { it.email?.equals(email, ignoreCase = true) == true }

    override suspend fun upsert(account: User) {
        users.update { it + (account.id to account) }
    }

    override suspend fun count(): Int = users.value.size
}

/** In-memory storage, for tests. */
class InMemoryLocalStore : LocalStore {
    override val issues: IssueLocalDataSource = InMemoryIssueLocalDataSource()
    override val departments: DepartmentLocalDataSource = InMemoryDepartmentLocalDataSource()
    override val neighborhoods: NeighborhoodLocalDataSource = InMemoryNeighborhoodLocalDataSource()
    override val users: UserLocalDataSource = InMemoryUserLocalDataSource()
}
