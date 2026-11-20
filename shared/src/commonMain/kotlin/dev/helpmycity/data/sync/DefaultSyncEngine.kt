package dev.helpmycity.data.sync

import dev.helpmycity.data.local.IssueLocalDataSource
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.RemoteResult
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Outbox-drain sync.
 *
 * Local writes are the source of truth until the backend acknowledges them. So
 * the loop goes like this: collect everything that is not [SyncState.SYNCED], push
 * it, mark whatever the backend accepted, and then pull remote changes since the
 * last successful sync. Photos go after the issues they belong to in both
 * directions. A backend can refuse a photo for an issue it has never seen, and a
 * pulled photo has nowhere to land locally until its issue does.
 *
 * What a pull returns depends on who is asking, so when the user or their role
 * changes, it starts over with a full pull and drops synced rows the new caller
 * can't see.
 *
 * With [dev.helpmycity.data.remote.NoopIssueBackendApi] wired in, every push comes
 * back [RemoteResult.NotConfigured] and nothing gets marked synced. That way the
 * queue keeps building up correctly and drains on the first run against a real
 * backend, instead of quietly throwing writes away.
 */
@Single(binds = [SyncEngine::class])
class DefaultSyncEngine(
    private val localDataSource: IssueLocalDataSource,
    private val backend: IssueBackendApi,
    private val photoBackend: PhotoBackendApi,
    private val time: TimeProvider,
    private val session: UserSession,
) : SyncEngine {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** One sync at a time. A manual "sync now" during an auto-sync waits its turn instead of racing it. */
    private val syncLock = Mutex()
    private var watchJob: Job? = null
    private var lastSuccessfulSyncMillis: Long? = null
    private var pruneOnNextPull = false

    @OptIn(FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        if (watchJob != null) return
        watchJob = scope.launch {
            // The first value doubles as the startup pull. The outbox watcher
            // below only fires when something is queued, so without this a fresh
            // install would never find out what the backend already has.
            launch {
                session.currentUser
                    .map { user -> user?.id to user?.role }
                    .distinctUntilChanged()
                    .collect { sync(newCaller = true) }
            }

            combine(
                localDataSource.observeAll(),
                localDataSource.observePendingPhotoCount(),
                localDataSource.observePendingSupportCount(),
            ) { issues, pendingPhotos, pendingSupports ->
                issues.count { it.sync.state != SyncState.SYNCED } + pendingPhotos + pendingSupports
            }
                .distinctUntilChanged()
                // A manager editing a form causes a burst of writes, so sync once at the end.
                .debounce(SYNC_DEBOUNCE_MILLIS)
                .collect { pendingCount -> if (pendingCount > 0) syncNow() }
        }
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    override suspend fun syncNow(): SyncStatus = sync(newCaller = false)

    private suspend fun sync(newCaller: Boolean): SyncStatus = syncLock.withLock {
        if (newCaller) {
            lastSuccessfulSyncMillis = null
            pruneOnNextPull = true
        }
        _status.value = SyncStatus.Syncing
        val result = runSync()
        _status.value = result
        result
    }

    private suspend fun runSync(): SyncStatus {
        val pendingIssues = localDataSource.pendingSync()
        var pushed = 0

        for (issue in pendingIssues) {
            when (val ack = backend.pushIssue(issue)) {
                is RemoteResult.Success -> {
                    localDataSource.markSynced(
                        issueId = issue.id,
                        syncedAtMillis = ack.value.acknowledgedAtMillis,
                        remoteVersion = ack.value.remoteVersion,
                    )
                    pushed++
                }

                // Nothing to push to. Leave the queue alone and say so.
                RemoteResult.NotConfigured -> return SyncStatus.NoBackend

                is RemoteResult.Failure -> {
                    if (!ack.retryable) localDataSource.markSyncState(issue.id, SyncState.FAILED)
                    return SyncStatus.Failed(ack.message, pendingIssues.size - pushed)
                }
            }
        }

        for (change in localDataSource.pendingStatusChanges()) {
            when (val ack = backend.pushStatusChange(change)) {
                is RemoteResult.Success -> Unit
                RemoteResult.NotConfigured -> return SyncStatus.NoBackend
                is RemoteResult.Failure ->
                    return SyncStatus.Failed(ack.message, pendingIssues.size - pushed)
            }
        }

        // This goes after the issues, so a star on a report filed offline can find its issue.
        val pendingSupports = localDataSource.pendingSupports()
        var supported = 0
        for (support in pendingSupports) {
            when (val ack = backend.pushSupport(support)) {
                is RemoteResult.Success -> {
                    localDataSource.markSupportSynced(support)
                    supported++
                }

                RemoteResult.NotConfigured -> return SyncStatus.NoBackend
                is RemoteResult.Failure ->
                    return SyncStatus.Failed(ack.message, pendingSupports.size - supported)
            }
        }

        val pendingPhotos = localDataSource.pendingPhotos()
        var uploaded = 0
        for (photo in pendingPhotos) {
            when (val ack = photoBackend.uploadPhoto(photo.issueId, photo)) {
                is RemoteResult.Success -> {
                    localDataSource.markPhotoSynced(photo.id, ack.value, time.nowMillis())
                    uploaded++
                }

                RemoteResult.NotConfigured -> return SyncStatus.NoBackend
                is RemoteResult.Failure ->
                    return SyncStatus.Failed(ack.message, pendingPhotos.size - uploaded)
            }
        }

        val since = lastSuccessfulSyncMillis
        val pulledIds = when (val remote = backend.fetchIssuesChangedSince(since)) {
            is RemoteResult.Success -> {
                localDataSource.upsertAll(remote.value)
                remote.value.mapTo(mutableSetOf()) { it.id }
            }

            RemoteResult.NotConfigured -> return SyncStatus.NoBackend
            is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
        }
        if (pruneOnNextPull) {
            localDataSource.deleteSyncedExcept(pulledIds)
            pruneOnNextPull = false
        }

        if (since == null || pulledIds.isNotEmpty()) {
            when (val remote = backend.fetchStatusChanges(if (since == null) null else pulledIds)) {
                is RemoteResult.Success -> localDataSource.mergeRemoteStatusChanges(remote.value)
                RemoteResult.NotConfigured -> return SyncStatus.NoBackend
                is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
            }
        }

        when (val remote = backend.fetchOwnSupports()) {
            is RemoteResult.Success -> localDataSource.mergeRemoteSupports(remote.value)
            RemoteResult.NotConfigured -> return SyncStatus.NoBackend
            is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
        }

        when (val remote = photoBackend.fetchPhotosUploadedSince(since)) {
            is RemoteResult.Success -> localDataSource.mergeRemotePhotos(remote.value)
            RemoteResult.NotConfigured -> return SyncStatus.NoBackend
            is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
        }

        val now = time.nowMillis()
        lastSuccessfulSyncMillis = now
        return SyncStatus.Synced(syncedAtMillis = now, pushedCount = pushed, pulledCount = pulledIds.size)
    }

    private companion object {
        const val SYNC_DEBOUNCE_MILLIS = 1_500L
    }
}
