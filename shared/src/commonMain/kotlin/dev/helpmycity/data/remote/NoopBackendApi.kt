package dev.helpmycity.data.remote

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.Neighborhood

/**
 * The default backend, which is no backend at all.
 *
 * This does not throw. Returning [RemoteResult.NotConfigured] keeps the whole
 * offline-first path (write locally, queue it for sync, sync says there is
 * nothing to do) running end to end with no backend configured.
 *
 * [dev.helpmycity.deployment.LocalOnlyBackend] bundles these together.
 */
class NoopIssueBackendApi : IssueBackendApi {
    override suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>> =
        RemoteResult.NotConfigured

    override suspend fun fetchStatusChanges(issueIds: Set<String>?): RemoteResult<List<IssueStatusChange>> =
        RemoteResult.NotConfigured

    override suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck> =
        RemoteResult.NotConfigured

    override suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck> =
        RemoteResult.NotConfigured

    override suspend fun deleteIssue(issueId: String): RemoteResult<Unit> =
        RemoteResult.NotConfigured

    override suspend fun pushSupport(support: IssueSupport): RemoteResult<Unit> =
        RemoteResult.NotConfigured

    override suspend fun fetchOwnSupports(): RemoteResult<List<IssueSupport>> =
        RemoteResult.NotConfigured
}

class NoopReferenceDataBackendApi : ReferenceDataBackendApi {
    override suspend fun fetchDepartments(): RemoteResult<List<Department>> =
        RemoteResult.NotConfigured

    override suspend fun fetchNeighborhoods(): RemoteResult<List<Neighborhood>> =
        RemoteResult.NotConfigured
}

class NoopPhotoBackendApi : PhotoBackendApi {
    override suspend fun uploadPhoto(issueId: String, photo: IssuePhoto): RemoteResult<String> =
        RemoteResult.NotConfigured

    override suspend fun fetchPhotosUploadedSince(sinceMillis: Long?): RemoteResult<List<IssuePhoto>> =
        RemoteResult.NotConfigured
}
