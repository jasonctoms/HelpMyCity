package dev.helpmycity.data.remote

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.Neighborhood

/**
 * The whole backend contract, in three small interfaces.
 *
 * Nothing above this layer knows whether the backend is a managed cloud, a
 * self-hosted Postgres box or nothing at all -- which is the point: the project
 * is meant to be forkable with the backend swapped out. A new backend is a new
 * set of implementations gathered into a
 * [dev.helpmycity.deployment.BackendProvider], chosen at the platform
 * entry point. See the `supabase` package in `:cityConfig` for a worked example.
 *
 * Every method returns [RemoteResult] rather than throwing, because "there is no
 * backend configured yet" and "the phone is in a parking garage" are both normal
 * states for this app, not exceptional ones.
 */
interface IssueBackendApi {
    /**
     * Pulls issues changed since [sinceMillis] (null = full sync). Incremental
     * by timestamp so a manager's phone does not re-download the city on every
     * app open.
     */
    suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>>

    suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck>

    suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck>

    suspend fun deleteIssue(issueId: String): RemoteResult<Unit>

    /**
     * Records one user's star. The backend owns [Issue.supportCount]: it counts
     * each (issue, user) pair once, and a replayed push must be harmless.
     */
    suspend fun pushSupport(support: IssueSupport): RemoteResult<Unit>

    /** The signed-in user's stars, so a new device knows what it already starred. */
    suspend fun fetchOwnSupports(): RemoteResult<List<IssueSupport>>
}

interface ReferenceDataBackendApi {
    suspend fun fetchDepartments(): RemoteResult<List<Department>>
    suspend fun fetchNeighborhoods(): RemoteResult<List<Neighborhood>>
}

interface PhotoBackendApi {
    /**
     * Stores the photo's bytes and records it against its issue, returning the
     * durable URL it can be read back from. Replayed by the outbox like every
     * other push, so a second upload of the same photo must be harmless.
     */
    suspend fun uploadPhoto(issueId: String, photo: IssuePhoto): RemoteResult<String>

    /**
     * Photos uploaded since [sinceMillis] (null = all), with [IssuePhoto.remoteUrl]
     * set and no bytes. How other devices learn a photo exists.
     */
    suspend fun fetchPhotosUploadedSince(sinceMillis: Long?): RemoteResult<List<IssuePhoto>>
}

/** What the backend hands back after accepting a write. */
data class RemoteAck(
    val id: String,
    /** Version/etag used to detect a conflicting edit on the next push. */
    val remoteVersion: String?,
    val acknowledgedAtMillis: Long,
)

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>

    /**
     * No backend is wired up. Distinct from [Failure] on purpose: the sync
     * engine treats it as "nothing to do", not as an error to surface or retry.
     */
    data object NotConfigured : RemoteResult<Nothing>

    data class Failure(
        val message: String,
        val retryable: Boolean = true,
        val cause: Throwable? = null,
    ) : RemoteResult<Nothing>
}

inline fun <T, R> RemoteResult<T>.fold(
    onSuccess: (T) -> R,
    onNotConfigured: () -> R,
    onFailure: (RemoteResult.Failure) -> R,
): R = when (this) {
    is RemoteResult.Success -> onSuccess(value)
    is RemoteResult.NotConfigured -> onNotConfigured()
    is RemoteResult.Failure -> onFailure(this)
}
