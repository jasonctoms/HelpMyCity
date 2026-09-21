package dev.helpmycity.data.sync

import dev.helpmycity.data.local.IssueLocalDataSource
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.RemoteResult
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Outbox-drain sync.
 *
 * Local writes are the source of truth until the backend acknowledges them, so
 * the loop is: collect everything not in [SyncState.SYNCED], push it, mark what
 * the backend accepted, then pull remote changes since the last successful sync.
 * Photos go after the issues they belong to, in both directions: a backend can
 * refuse a photo for an issue it has not seen, and a pulled photo has nowhere to
 * land locally until its issue has.
 *
 * With [dev.helpmycity.data.remote.NoopIssueBackendApi] wired in, every
 * push comes back [RemoteResult.NotConfigured] and nothing is marked synced --
 * so the queue keeps building correctly and will drain on the first run against
 * a real backend, rather than silently discarding writes.
 */
@Single(binds = [SyncEngine::class])
class DefaultSyncEngine(
    private val localDataSource: IssueLocalDataSource,
    private val backend: IssueBackendApi,
    private val photoBackend: PhotoBackendApi,
    private val time: TimeProvider,
) : SyncEngine {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** One sync at a time; a manual "sync now" during an auto-sync waits rather than racing. */
    private val syncLock = Mutex()
    private var watchJob: Job? = null
    private var lastSuccessfulSyncMillis: Long? = null

    @OptIn(FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        if (watchJob != null) return
        watchJob = scope.launch {
            // Pull once before watching anything. The watcher below only fires
            // when there is something queued to push, so without this a device
            // with an empty store -- a fresh install, or any first run against
            // an already-populated backend -- would never learn what the
            // backend already has, and would sit on an empty list until its
            // owner happened to file something.
            syncNow()

            combine(
                localDataSource.observeAll(),
                localDataSource.observePendingPhotoCount(),
            ) { issues, pendingPhotos -> issues.count { it.sync.state != SyncState.SYNCED } + pendingPhotos }
                .distinctUntilChanged()
                // A manager editing a form produces a burst of writes; sync once at the end of it.
                .debounce(SYNC_DEBOUNCE_MILLIS)
                .collect { pendingCount -> if (pendingCount > 0) syncNow() }
        }
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    override suspend fun syncNow(): SyncStatus = syncLock.withLock {
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

                // Nothing to push to. Leave the queue intact and report honestly.
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

        val pulled = when (val remote = backend.fetchIssuesChangedSince(lastSuccessfulSyncMillis)) {
            is RemoteResult.Success -> {
                localDataSource.upsertAll(remote.value)
                remote.value.size
            }

            RemoteResult.NotConfigured -> return SyncStatus.NoBackend
            is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
        }

        when (val remote = photoBackend.fetchPhotosUploadedSince(lastSuccessfulSyncMillis)) {
            is RemoteResult.Success -> localDataSource.mergeRemotePhotos(remote.value)
            RemoteResult.NotConfigured -> return SyncStatus.NoBackend
            is RemoteResult.Failure -> return SyncStatus.Failed(remote.message, 0)
        }

        val now = time.nowMillis()
        lastSuccessfulSyncMillis = now
        return SyncStatus.Synced(syncedAtMillis = now, pushedCount = pushed, pulledCount = pulled)
    }

    private companion object {
        const val SYNC_DEBOUNCE_MILLIS = 1_500L
    }
}
