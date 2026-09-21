package dev.helpmycity.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Pushes locally-queued writes to the backend and pulls remote changes back
 * down. Started once at app launch; everything else in the app just writes
 * locally and lets this drain the queue.
 */
interface SyncEngine {
    val status: StateFlow<SyncStatus>

    /** Begins watching the local store and syncing changes as they appear. */
    fun start(scope: CoroutineScope)

    fun stop()

    /** Manual "sync now", e.g. pull-to-refresh. */
    suspend fun syncNow(): SyncStatus
}

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    /** No backend is configured, so there is nothing to sync to. Not an error. */
    data object NoBackend : SyncStatus

    data class Synced(
        val syncedAtMillis: Long,
        val pushedCount: Int,
        val pulledCount: Int,
    ) : SyncStatus

    data class Failed(val message: String, val pendingCount: Int) : SyncStatus
}
