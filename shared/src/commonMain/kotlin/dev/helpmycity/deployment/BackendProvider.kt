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
 * This is the other half of the fork seam ([CityProfile] is the first half).
 * Nothing above `data/remote` knows which cloud is behind it, and that is kind of
 * the point. A fork picks a city and a backend separately, and neither choice ever
 * reaches the UI.
 *
 * The `supabase` package in `:cityConfig` is the worked example. Moving to another
 * provider only means implementing this interface and the four APIs it exposes.
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
 * Reads and writes stay in the local Room database, the sync queue builds up
 * without ever draining, and sign-in is [MockAuthService]. This is a real mode and
 * not a broken one; the offline-first path runs end to end without a server.
 */
object LocalOnlyBackend : BackendProvider {
    override val issues: IssueBackendApi = NoopIssueBackendApi()
    override val referenceData: ReferenceDataBackendApi = NoopReferenceDataBackendApi()
    override val photos: PhotoBackendApi = NoopPhotoBackendApi()
    override val auth: AuthService = MockAuthService(UuidIdGenerator)
}
