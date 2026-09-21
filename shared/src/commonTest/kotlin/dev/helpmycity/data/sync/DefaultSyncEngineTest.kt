package dev.helpmycity.data.sync

import dev.helpmycity.data.local.InMemoryIssueLocalDataSource
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.NoopIssueBackendApi
import dev.helpmycity.data.remote.NoopPhotoBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.RemoteAck
import dev.helpmycity.data.remote.RemoteResult
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueLocation
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueReview
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.SyncMetadata
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSyncEngineTest {

    private val time = TimeProvider { 5_000L }

    private fun issue(id: String) = Issue(
        id = id,
        title = "Pothole",
        description = "Deep one",
        requestedAction = "",
        category = IssueCategory.ROAD_SURFACE,
        status = IssueStatus.SUBMITTED,
        priority = IssuePriority.MEDIUM,
        location = IssueLocation(description = "Main St"),
        departmentId = null,
        reporter = null,
        submittedByUserId = null,
        review = IssueReview.Pending,
        notesSource = "",
        externalReference = null,
        supportCount = 0,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        sync = SyncMetadata(state = SyncState.PENDING_UPLOAD),
    )

    /**
     * A fresh install has nothing queued, and the outbox watcher only fires
     * when something is. Without a pull at startup the store would stay empty
     * against a backend that already has rows -- which is exactly what a public
     * demo looks like to every first-time visitor.
     */
    @Test
    fun startingWithAnEmptyStorePullsWhatTheBackendAlreadyHas() = runTest {
        val local = InMemoryIssueLocalDataSource()
        // A pulled row arrives already agreed with the backend, the way every
        // real implementation rebuilds it. Handing back a PENDING_UPLOAD row
        // instead would make the store look dirty and kick off a second sync.
        val onTheBackend = issue("already-there")
            .copy(sync = SyncMetadata(state = SyncState.SYNCED))
        val backend = PopulatedBackend(onTheBackend)
        val engine = DefaultSyncEngine(local, backend, AcceptingPhotoBackend(), time)

        engine.start(this)
        testScheduler.advanceUntilIdle()
        engine.stop()

        assertEquals(1, backend.fetchCount)
        assertEquals("already-there", local.getById("already-there")?.id)
    }

    @Test
    fun withNoBackendTheQueueIsLeftIntact() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        val engine = DefaultSyncEngine(local, NoopIssueBackendApi(), NoopPhotoBackendApi(), time)

        val status = engine.syncNow()

        assertEquals(SyncStatus.NoBackend, status)
        // The whole point: nothing is silently dropped just because there is
        // nowhere to send it yet.
        assertEquals(1, local.pendingSync().size)
    }

    @Test
    fun acknowledgedIssuesAreMarkedSynced() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        val engine = DefaultSyncEngine(local, AcceptingBackend(), AcceptingPhotoBackend(), time)

        val status = engine.syncNow()

        val synced = assertIs<SyncStatus.Synced>(status)
        assertEquals(1, synced.pushedCount)
        assertTrue(local.pendingSync().isEmpty())
        assertEquals(SyncState.SYNCED, local.getById("a")?.sync?.state)
    }

    @Test
    fun aNonRetryableFailureIsRecordedOnTheRow() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        val engine = DefaultSyncEngine(local, RejectingBackend(retryable = false), AcceptingPhotoBackend(), time)

        val status = engine.syncNow()

        assertIs<SyncStatus.Failed>(status)
        assertEquals(SyncState.FAILED, local.getById("a")?.sync?.state)
    }

    @Test
    fun aRetryableFailureLeavesTheRowQueuedForAnotherAttempt() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        val engine = DefaultSyncEngine(local, RejectingBackend(retryable = true), AcceptingPhotoBackend(), time)

        engine.syncNow()

        assertEquals(SyncState.PENDING_UPLOAD, local.getById("a")?.sync?.state)
    }

    private fun photo(id: String, issueId: String) = IssuePhoto(
        id = id,
        issueId = issueId,
        bytes = byteArrayOf(1, 2, 3),
        createdAtMillis = 2L,
        sync = SyncMetadata(state = SyncState.PENDING_UPLOAD),
    )

    @Test
    fun anUploadedPhotoIsMarkedSyncedAndKeepsItsLocalBytes() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        local.upsertPhoto(photo("p", issueId = "a"))
        val photos = AcceptingPhotoBackend()
        val engine = DefaultSyncEngine(local, AcceptingBackend(), photos, time)

        assertIs<SyncStatus.Synced>(engine.syncNow())

        assertEquals(listOf("p"), photos.uploaded)
        val stored = local.observePhotos("a").first().single()
        assertEquals(SyncState.SYNCED, stored.sync.state)
        assertEquals("https://photos.example/a/p", stored.remoteUrl)
        // The backend's own copy comes back without bytes; this device's stays.
        assertTrue(stored.bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun photosAnotherDeviceUploadedArePulledOntoTheirIssues() = runTest {
        val local = InMemoryIssueLocalDataSource()
        val onTheBackend = issue("a").copy(sync = SyncMetadata(state = SyncState.SYNCED))
        val photos = AcceptingPhotoBackend(
            IssuePhoto(id = "p", issueId = "a", remoteUrl = "https://photos.example/a/p"),
            // Its issue is not readable here, so there is nowhere to put it.
            IssuePhoto(id = "q", issueId = "hidden", remoteUrl = "https://photos.example/hidden/q"),
        )
        val engine = DefaultSyncEngine(local, PopulatedBackend(onTheBackend), photos, time)

        engine.syncNow()

        val pulled = local.observePhotos("a").first().single()
        assertEquals("https://photos.example/a/p", pulled.remoteUrl)
        assertNull(pulled.bytes)
        assertTrue(local.observePhotos("hidden").first().isEmpty())
    }

    @Test
    fun aPhotoAddedToASyncedIssueIsUploadedWithoutAnythingElseQueued() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a").copy(sync = SyncMetadata(state = SyncState.SYNCED)))
        val photos = AcceptingPhotoBackend()
        val engine = DefaultSyncEngine(local, AcceptingBackend(), photos, time)
        engine.start(this)
        testScheduler.advanceUntilIdle()

        local.upsertPhoto(photo("p", issueId = "a"))
        testScheduler.advanceUntilIdle()
        engine.stop()

        assertEquals(listOf("p"), photos.uploaded)
    }

    @Test
    fun aPhotoWaitsUntilItsIssueHasBeenAccepted() = runTest {
        val local = InMemoryIssueLocalDataSource()
        local.upsert(issue("a"))
        local.upsertPhoto(photo("p", issueId = "a"))
        val photos = AcceptingPhotoBackend()
        val engine = DefaultSyncEngine(local, RejectingBackend(retryable = true), photos, time)

        engine.syncNow()

        assertTrue(photos.uploaded.isEmpty())
        assertEquals(1, local.pendingPhotos().size)
    }

    /** A backend that already holds rows, and counts how often it is read. */
    private class PopulatedBackend(private vararg val remote: Issue) : IssueBackendApi {
        var fetchCount: Int = 0
            private set

        override suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>> {
            fetchCount++
            return RemoteResult.Success(remote.toList())
        }

        override suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck> =
            RemoteResult.Success(RemoteAck(issue.id, "v1", acknowledgedAtMillis = 5_000L))

        override suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck> =
            RemoteResult.Success(RemoteAck(change.id, "v1", acknowledgedAtMillis = 5_000L))

        override suspend fun deleteIssue(issueId: String): RemoteResult<Unit> =
            RemoteResult.Success(Unit)
    }

    private class AcceptingBackend : IssueBackendApi {
        override suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>> =
            RemoteResult.Success(emptyList())

        override suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck> =
            RemoteResult.Success(RemoteAck(issue.id, "v1", acknowledgedAtMillis = 5_000L))

        override suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck> =
            RemoteResult.Success(RemoteAck(change.id, "v1", acknowledgedAtMillis = 5_000L))

        override suspend fun deleteIssue(issueId: String): RemoteResult<Unit> =
            RemoteResult.Success(Unit)
    }

    private class RejectingBackend(private val retryable: Boolean) : IssueBackendApi {
        private val failure = RemoteResult.Failure("nope", retryable = retryable)

        override suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>> =
            failure

        override suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck> = failure
        override suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck> =
            failure

        override suspend fun deleteIssue(issueId: String): RemoteResult<Unit> = failure
    }

    /** Accepts every upload, and already holds [remote]. */
    private class AcceptingPhotoBackend(private vararg val remote: IssuePhoto) : PhotoBackendApi {
        val uploaded = mutableListOf<String>()

        override suspend fun uploadPhoto(issueId: String, photo: IssuePhoto): RemoteResult<String> {
            uploaded += photo.id
            return RemoteResult.Success("https://photos.example/$issueId/${photo.id}")
        }

        override suspend fun fetchPhotosUploadedSince(sinceMillis: Long?): RemoteResult<List<IssuePhoto>> =
            RemoteResult.Success(
                remote.toList() + uploaded.map { id ->
                    IssuePhoto(id = id, issueId = "a", remoteUrl = "https://photos.example/a/$id")
                },
            )
    }
}
