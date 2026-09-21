package dev.helpmycity.data.local

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * The offline store the repositories read and write. Room backs it on every
 * target; [InMemoryIssueLocalDataSource] backs tests.
 *
 * Filtering happens in Kotlin rather than SQL: the dataset is a few thousand
 * issues for one city, and keeping
 * [dev.helpmycity.domain.model.IssueFilter] out of the storage layer
 * lets another store be dropped in without reimplementing query translation.
 */
interface IssueLocalDataSource {
    fun observeAll(): Flow<List<Issue>>
    fun observeById(id: String): Flow<Issue?>
    fun observeHistory(issueId: String): Flow<List<IssueStatusChange>>

    /** Read per-issue, never as part of [observeAll] -- photo bytes are the big values here. */
    fun observePhotos(issueId: String): Flow<List<IssuePhoto>>

    suspend fun getById(id: String): Issue?
    suspend fun upsert(issue: Issue)
    suspend fun upsertAll(issues: List<Issue>)
    suspend fun delete(id: String)

    suspend fun appendStatusChange(change: IssueStatusChange)

    suspend fun upsertPhoto(photo: IssuePhoto)
    suspend fun deletePhoto(photoId: String)

    /** Outbox drain: everything the backend has not acknowledged yet. */
    suspend fun pendingSync(): List<Issue>
    suspend fun pendingStatusChanges(): List<IssueStatusChange>
    suspend fun pendingPhotos(): List<IssuePhoto>
    fun observePendingPhotoCount(): Flow<Int>

    suspend fun markPhotoSynced(photoId: String, remoteUrl: String, syncedAtMillis: Long)

    /**
     * Records photos the backend holds. Bytes already stored here are kept, and a
     * photo whose issue is not stored here is skipped rather than orphaned.
     */
    suspend fun mergeRemotePhotos(photos: List<IssuePhoto>)
    suspend fun markSynced(issueId: String, syncedAtMillis: Long, remoteVersion: String?)
    suspend fun markSyncState(issueId: String, state: SyncState)
    suspend fun count(): Int
}

interface DepartmentLocalDataSource {
    fun observeAll(): Flow<List<Department>>
    suspend fun getById(id: String): Department?
    suspend fun upsert(department: Department)
    suspend fun upsertAll(departments: List<Department>)
    suspend fun delete(id: String)
    suspend fun count(): Int
}

interface NeighborhoodLocalDataSource {
    fun observeAll(): Flow<List<Neighborhood>>
    suspend fun getById(id: String): Neighborhood?
    suspend fun upsert(neighborhood: Neighborhood)
    suspend fun upsertAll(neighborhoods: List<Neighborhood>)
    suspend fun delete(id: String)
    suspend fun count(): Int
}

/**
 * The users the admin screen edits.
 *
 * No `delete`: creating and closing accounts belongs to whoever authenticates
 * them, and this table only holds what *this deployment* decided about an
 * account it has already seen.
 */
interface UserLocalDataSource {
    fun observeAll(): Flow<List<User>>
    fun observeById(id: String): Flow<User?>
    suspend fun getById(id: String): User?

    /** Case-insensitive: an address is one person however it was typed. */
    suspend fun getByEmail(email: String): User?

    suspend fun upsert(account: User)
    suspend fun count(): Int
}
