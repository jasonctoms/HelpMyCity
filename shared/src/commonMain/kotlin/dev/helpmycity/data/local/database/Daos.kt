package dev.helpmycity.data.local.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE id = :id")
    fun observeById(id: String): Flow<IssueEntity?>

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun getById(id: String): IssueEntity?

    @Upsert
    suspend fun upsert(issue: IssueEntity)

    @Upsert
    suspend fun upsertAll(issues: List<IssueEntity>)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM issues WHERE sync_state = 'synced'")
    suspend fun syncedIds(): List<String>

    @Query("SELECT COUNT(*) FROM issues")
    suspend fun count(): Int

    @Query("SELECT * FROM issues WHERE sync_state IN ('pending_upload', 'pending_delete', 'failed')")
    suspend fun pendingSync(): List<IssueEntity>

    @Query(
        """
        UPDATE issues
        SET sync_state = 'synced', last_synced_at = :syncedAtMillis, remote_version = :remoteVersion
        WHERE id = :id
        """
    )
    suspend fun markSynced(id: String, syncedAtMillis: Long, remoteVersion: String?)

    @Query("UPDATE issues SET sync_state = :state WHERE id = :id")
    suspend fun markSyncState(id: String, state: String)

    @Query("SELECT * FROM issue_photos WHERE issue_id = :issueId ORDER BY created_at ASC")
    fun observePhotos(issueId: String): Flow<List<IssuePhotoEntity>>

    @Upsert
    suspend fun upsertPhoto(photo: IssuePhotoEntity)

    @Query("DELETE FROM issue_photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("SELECT * FROM issue_photos WHERE sync_state != 'synced' ORDER BY created_at ASC")
    suspend fun pendingPhotos(): List<IssuePhotoEntity>

    @Query("SELECT COUNT(*) FROM issue_photos WHERE sync_state != 'synced'")
    fun observePendingPhotoCount(): Flow<Int>

    @Query(
        """
        UPDATE issue_photos
        SET sync_state = 'synced',
            remote_url = :remoteUrl,
            last_synced_at = :syncedAtMillis
        WHERE id = :id
        """
    )
    suspend fun markPhotoSynced(id: String, remoteUrl: String, syncedAtMillis: Long)

    // The conflict branch leaves `bytes` alone, so the device that filed a photo
    // keeps its local copy.
    @Query(
        """
        INSERT INTO issue_photos
            (id, issue_id, bytes, remote_url, caption, created_at, sync_state, last_synced_at, remote_version)
        SELECT :id, :issueId, NULL, :remoteUrl, :caption, :createdAtMillis, 'synced', :syncedAtMillis, NULL
        WHERE EXISTS (SELECT 1 FROM issues WHERE id = :issueId)
        ON CONFLICT(id) DO UPDATE SET
            remote_url = excluded.remote_url,
            caption = excluded.caption,
            sync_state = 'synced',
            last_synced_at = excluded.last_synced_at
        """
    )
    suspend fun mergeRemotePhoto(
        id: String,
        issueId: String,
        remoteUrl: String,
        caption: String?,
        createdAtMillis: Long,
        syncedAtMillis: Long?,
    )

    @Query("SELECT * FROM issue_status_changes WHERE issue_id = :issueId ORDER BY changed_at DESC")
    fun observeHistory(issueId: String): Flow<List<IssueStatusChangeEntity>>

    @Upsert
    suspend fun upsertStatusChange(change: IssueStatusChangeEntity)

    @Query("SELECT * FROM issue_status_changes WHERE sync_state != 'synced' ORDER BY changed_at ASC")
    suspend fun pendingStatusChanges(): List<IssueStatusChangeEntity>

    @Query(
        """
        INSERT INTO issue_status_changes
            (id, issue_id, from_status, to_status, note, changed_by_user_id, changed_by_display_name,
             changed_at, sync_state, last_synced_at, remote_version)
        SELECT :id, :issueId, :fromStatus, :toStatus, :note, :changedByUserId, :changedByDisplayName,
               :changedAtMillis, 'synced', NULL, NULL
        WHERE EXISTS (SELECT 1 FROM issues WHERE id = :issueId)
        ON CONFLICT(id) DO UPDATE SET sync_state = 'synced'
        """
    )
    suspend fun mergeRemoteStatusChange(
        id: String,
        issueId: String,
        fromStatus: String?,
        toStatus: String,
        note: String?,
        changedByUserId: String?,
        changedByDisplayName: String?,
        changedAtMillis: Long,
    )

    @Query("SELECT issue_id FROM issue_supports WHERE user_id = :userId")
    fun observeSupportedIssueIds(userId: String): Flow<List<String>>

    /** -1 when this user has already starred the issue. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSupport(support: IssueSupportEntity): Long

    @Query("UPDATE issues SET support_count = support_count + 1 WHERE id = :issueId")
    suspend fun incrementSupportCount(issueId: String)

    @Upsert
    suspend fun upsertSupports(supports: List<IssueSupportEntity>)

    @Query("SELECT * FROM issue_supports WHERE sync_state != 'synced' ORDER BY created_at ASC")
    suspend fun pendingSupports(): List<IssueSupportEntity>

    @Query("SELECT COUNT(*) FROM issue_supports WHERE sync_state != 'synced'")
    fun observePendingSupportCount(): Flow<Int>

    @Query("UPDATE issue_supports SET sync_state = 'synced' WHERE issue_id = :issueId AND user_id = :userId")
    suspend fun markSupportSynced(issueId: String, userId: String)
}

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments ORDER BY name ASC")
    fun observeAll(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE id = :id")
    suspend fun getById(id: String): DepartmentEntity?

    @Upsert
    suspend fun upsert(department: DepartmentEntity)

    @Upsert
    suspend fun upsertAll(departments: List<DepartmentEntity>)

    @Query("DELETE FROM departments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM departments")
    suspend fun count(): Int
}

@Dao
interface NeighborhoodDao {
    @Query("SELECT * FROM neighborhoods ORDER BY name ASC")
    fun observeAll(): Flow<List<NeighborhoodEntity>>

    @Query("SELECT * FROM neighborhoods WHERE id = :id")
    suspend fun getById(id: String): NeighborhoodEntity?

    @Upsert
    suspend fun upsert(neighborhood: NeighborhoodEntity)

    @Upsert
    suspend fun upsertAll(neighborhoods: List<NeighborhoodEntity>)

    @Query("DELETE FROM neighborhoods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM neighborhoods")
    suspend fun count(): Int
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY display_name ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email)")
    suspend fun getByEmail(email: String): UserEntity?

    @Upsert
    suspend fun upsert(account: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}
