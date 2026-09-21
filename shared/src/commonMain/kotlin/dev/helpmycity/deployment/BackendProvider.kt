package dev.helpmycity.deployment

import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.data.auth.MockAuthService
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.NoopIssueBackendApi
import dev.helpmycity.data.remote.NoopPhotoBackendApi
import dev.helpmycity.data.remote.NoopReferenceDataBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.ReferenceDataBackendApi
import dev.helpmycity.domain.util.UuidIdGenerator

/**
 * Where a deployment's data and identities live.
 *
 * The other half of the fork seam (see [CityProfile]). Nothing above
 * `data/remote` knows which cloud is behind this, which is the point: a fork
 * picks a city and a backend independently, and neither choice reaches the UI.
 *
 * The `supabase` package in `:cityConfig` is the worked example. Implementing this
 * interface plus the four APIs it exposes is the entire cost of moving to
 * another provider.
 */
interface BackendProvider {
    val issues: IssueBackendApi
    val referenceData: ReferenceDataBackendApi
    val photos: PhotoBackendApi
    val auth: AuthService
}

/**
 * The default: nothing is hosted anywhere.
 *
 * Reads and writes stay in the local Room database, the sync queue builds
 * without draining, and sign-in is [MockAuthService]. A genuine mode rather
 * than a broken one: the offline-first path runs end to end without a server.
 */
object LocalOnlyBackend : BackendProvider {
    override val issues: IssueBackendApi = NoopIssueBackendApi()
    override val referenceData: ReferenceDataBackendApi = NoopReferenceDataBackendApi()
    override val photos: PhotoBackendApi = NoopPhotoBackendApi()
    override val auth: AuthService = MockAuthService(UuidIdGenerator)
}
