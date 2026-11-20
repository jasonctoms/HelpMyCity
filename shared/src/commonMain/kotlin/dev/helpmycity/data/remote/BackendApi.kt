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
 * Nothing above this layer knows if the backend is a managed cloud, a self-hosted
 * Postgres box, or nothing at all. That is on purpose, since the project is meant
 * to be forked with the backend swapped out. A new backend is just a new set of
 * implementations bundled into a [dev.helpmycity.deployment.BackendProvider] and
 * picked at the platform entry point. The `supabase` package in `:cityConfig` is a
 * worked example.
 *
 * Every method returns [RemoteResult] instead of throwing. "There is no backend
 * configured yet" and "the phone is in a parking garage" are both totally normal
 * states for this app, so they should not be exceptions.
 */
interface IssueBackendApi {
    /**
     * Pulls issues changed since [sinceMillis] (null = full sync). It is
     * incremental by timestamp so a manager's phone is not re-downloading the
     * whole city every time the app opens.
     */
    suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>>

    /**
     * History for [issueIds] (null = every issue the caller can read). This is
     * keyed on issues and not time, because an issue can become readable long
     * after its earlier entries were written.
     */
    suspend fun fetchStatusChanges(issueIds: Set<String>?): RemoteResult<List<IssueStatusChange>>

    suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck>

    suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck>

    suspend fun deleteIssue(issueId: String): RemoteResult<Unit>

    /**
     * Records one user's star. The backend owns [Issue.supportCount]; it counts
     * each (issue, user) pair once, and a replayed push has to be harmless.
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
     * Stores the photo's bytes, records it against its issue, and returns the
     * durable URL it can be read back from. The outbox replays this like every
     * other push, so uploading the same photo a second time has to be harmless.
     */
    suspend fun uploadPhoto(issueId: String, photo: IssuePhoto): RemoteResult<String>

    /**
     * Photos uploaded since [sinceMillis] (null = all), with [IssuePhoto.remoteUrl]
     * set and no bytes. This is how other devices find out a photo exists.
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
     * No backend is wired up. This is separate from [Failure] on purpose, because
     * the sync engine treats it as "nothing to do" and not as an error to show or
     * retry.
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
