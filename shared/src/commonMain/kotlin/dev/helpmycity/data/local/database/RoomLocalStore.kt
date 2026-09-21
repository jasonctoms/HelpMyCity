package dev.helpmycity.data.local.database

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.IssueLocalDataSource
import dev.helpmycity.data.local.LocalStore
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.data.local.UserLocalDataSource
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/** Room-backed storage. Constructed from a platform builder -- see `getDatabaseBuilder`. */
class RoomLocalStore(database: HelpMyCityDatabase) : LocalStore {
    override val issues: IssueLocalDataSource = RoomIssueLocalDataSource(database)
    override val departments: DepartmentLocalDataSource = RoomDepartmentLocalDataSource(database)
    override val neighborhoods: NeighborhoodLocalDataSource =
        RoomNeighborhoodLocalDataSource(database)
    override val users: UserLocalDataSource = RoomUserLocalDataSource(database)
}

/**
 * Everything about building the database except the two things that differ per
 * platform: where the file lives, and which [SQLiteDriver] to use.
 *
 * A plain function rather than a Koin binding, so the compiler plugin does not
 * emit a hint here as well as in each platform module.
 */
fun RoomDatabase.Builder<HelpMyCityDatabase>.buildHelpMyCityDatabase(
    driver: SQLiteDriver,
    /**
     * Where queries run. Null keeps Room's default, which is what web wants --
     * the browser is single-threaded and the web driver is already async.
     */
    queryCoroutineContext: CoroutineContext? = null,
): HelpMyCityDatabase = this
    .setDriver(driver)
    .apply { queryCoroutineContext?.let { setQueryCoroutineContext(it) } }
    // No production data yet. Swap for real migrations before the first deployment.
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()

internal class RoomIssueLocalDataSource(database: HelpMyCityDatabase) : IssueLocalDataSource {
    private val dao = database.issueDao()

    override fun observeAll(): Flow<List<Issue>> =
        dao.observeAll().map { rows -> rows.map(IssueEntity::toDomain) }

    override fun observeById(id: String): Flow<Issue?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeHistory(issueId: String): Flow<List<IssueStatusChange>> =
        dao.observeHistory(issueId).map { rows -> rows.map(IssueStatusChangeEntity::toDomain) }

    override suspend fun getById(id: String): Issue? = dao.getById(id)?.toDomain()

    override suspend fun upsert(issue: Issue) = dao.upsert(issue.toEntity())

    override suspend fun upsertAll(issues: List<Issue>) = dao.upsertAll(issues.map(Issue::toEntity))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun appendStatusChange(change: IssueStatusChange) =
        dao.upsertStatusChange(change.toEntity())

    override fun observePhotos(issueId: String): Flow<List<IssuePhoto>> =
        dao.observePhotos(issueId).map { rows -> rows.map(IssuePhotoEntity::toDomain) }

    override suspend fun upsertPhoto(photo: IssuePhoto) = dao.upsertPhoto(photo.toEntity())

    override suspend fun deletePhoto(photoId: String) = dao.deletePhoto(photoId)

    override suspend fun pendingPhotos(): List<IssuePhoto> =
        dao.pendingPhotos().map(IssuePhotoEntity::toDomain)

    override fun observePendingPhotoCount(): Flow<Int> = dao.observePendingPhotoCount()

    override suspend fun markPhotoSynced(photoId: String, remoteUrl: String, syncedAtMillis: Long) =
        dao.markPhotoSynced(photoId, remoteUrl, syncedAtMillis)

    override suspend fun mergeRemotePhotos(photos: List<IssuePhoto>) {
        for (photo in photos) {
            val remoteUrl = photo.remoteUrl ?: continue
            dao.mergeRemotePhoto(
                id = photo.id,
                issueId = photo.issueId,
                remoteUrl = remoteUrl,
                caption = photo.caption,
                createdAtMillis = photo.createdAtMillis,
                syncedAtMillis = photo.sync.lastSyncedAtMillis,
            )
        }
    }

    override suspend fun pendingSync(): List<Issue> = dao.pendingSync().map(IssueEntity::toDomain)

    override suspend fun pendingStatusChanges(): List<IssueStatusChange> =
        dao.pendingStatusChanges().map(IssueStatusChangeEntity::toDomain)

    override suspend fun markSynced(issueId: String, syncedAtMillis: Long, remoteVersion: String?) =
        dao.markSynced(issueId, syncedAtMillis, remoteVersion)

    override suspend fun markSyncState(issueId: String, state: SyncState) =
        dao.markSyncState(issueId, state.storageKey)

    override suspend fun count(): Int = dao.count()
}

internal class RoomDepartmentLocalDataSource(
    database: HelpMyCityDatabase,
) : DepartmentLocalDataSource {
    private val dao = database.departmentDao()

    override fun observeAll(): Flow<List<Department>> =
        dao.observeAll().map { rows -> rows.map(DepartmentEntity::toDomain) }

    override suspend fun getById(id: String): Department? = dao.getById(id)?.toDomain()

    override suspend fun upsert(department: Department) = dao.upsert(department.toEntity())

    override suspend fun upsertAll(departments: List<Department>) =
        dao.upsertAll(departments.map(Department::toEntity))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun count(): Int = dao.count()
}

internal class RoomNeighborhoodLocalDataSource(
    database: HelpMyCityDatabase,
) : NeighborhoodLocalDataSource {
    private val dao = database.neighborhoodDao()

    override fun observeAll(): Flow<List<Neighborhood>> =
        dao.observeAll().map { rows -> rows.map(NeighborhoodEntity::toDomain) }

    override suspend fun getById(id: String): Neighborhood? = dao.getById(id)?.toDomain()

    override suspend fun upsert(neighborhood: Neighborhood) = dao.upsert(neighborhood.toEntity())

    override suspend fun upsertAll(neighborhoods: List<Neighborhood>) =
        dao.upsertAll(neighborhoods.map(Neighborhood::toEntity))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun count(): Int = dao.count()
}

internal class RoomUserLocalDataSource(database: HelpMyCityDatabase) : UserLocalDataSource {
    private val dao = database.userDao()

    override fun observeAll(): Flow<List<User>> =
        dao.observeAll().map { rows -> rows.map(UserEntity::toDomain) }

    override fun observeById(id: String): Flow<User?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): User? = dao.getById(id)?.toDomain()

    override suspend fun getByEmail(email: String): User? =
        dao.getByEmail(email)?.toDomain()

    override suspend fun upsert(account: User) = dao.upsert(account.toEntity())

    override suspend fun count(): Int = dao.count()
}
