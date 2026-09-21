package dev.helpmycity.cityconfig.supabase

import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.ReferenceDataBackendApi
import dev.helpmycity.data.remote.RemoteAck
import dev.helpmycity.data.remote.RemoteResult
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.Neighborhood
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/**
 * The issue table, read and written incrementally.
 *
 * Every write is an upsert keyed on the issue id, because the sync engine
 * replays its outbox: a push that succeeded but whose acknowledgement was lost
 * has to be harmless the second time.
 *
 * Which rows a caller may actually see or change is decided by the row-level
 * security policies in `schema.sql`, not here -- a resident holding the
 * publishable key gets the public map plus their own reports, whatever this class asks for.
 */
internal class SupabaseIssueBackendApi(
    private val client: SupabaseClient?,
) : IssueBackendApi {

    override suspend fun fetchIssuesChangedSince(sinceMillis: Long?): RemoteResult<List<Issue>> =
        remote(client) { supabase ->
            supabase.from(ISSUES)
                .select {
                    if (sinceMillis != null) filter { gt("updated_at_millis", sinceMillis) }
                }
                .decodeList<IssueRow>()
                .map(IssueRow::toIssue)
        }

    override suspend fun pushIssue(issue: Issue): RemoteResult<RemoteAck> =
        remote(client) { supabase ->
            val stored = supabase.from(ISSUES)
                .upsert(issue.toRow()) { select() }
                .decodeSingle<IssueRow>()
            // Acknowledge what the table holds, not what was sent: a policy
            // or trigger may have adjusted it on the way in.
            RemoteAck(
                id = stored.id,
                remoteVersion = stored.updatedAtMillis.toString(),
                acknowledgedAtMillis = stored.updatedAtMillis,
            )
        }

    /**
     * Appends one audit entry, or does nothing if it is already there.
     *
     * `ignoreDuplicates` makes this an `ON CONFLICT DO NOTHING` rather than an
     * update, for two reasons that point the same way: the table is append-only
     * -- rewriting history is the one thing an audit trail must not allow, so
     * `schema.sql` grants no update policy on it -- and the sync engine re-pushes
     * entries it has already sent, which a merging upsert would turn into an
     * update and a permanent, unretryable failure.
     */
    override suspend fun pushStatusChange(change: IssueStatusChange): RemoteResult<RemoteAck> =
        remote(client) { supabase ->
            supabase.from(STATUS_CHANGES).upsert(change.toRow()) { ignoreDuplicates = true }
            // Nothing comes back from an ignored duplicate, so the ack is built
            // from what was sent. An append has no version to carry.
            RemoteAck(
                id = change.id,
                remoteVersion = null,
                acknowledgedAtMillis = change.changedAtMillis,
            )
        }

    override suspend fun deleteIssue(issueId: String): RemoteResult<Unit> =
        remote(client) { supabase ->
            supabase.from(ISSUES).delete { filter { eq("id", issueId) } }
        }

    /**
     * `ignoreDuplicates` for the same reason as [pushStatusChange]: the table
     * is insert-only, and the (issue, user) key is what keeps a user to one star.
     */
    override suspend fun pushSupport(support: IssueSupport): RemoteResult<Unit> =
        remote(client) { supabase ->
            supabase.from(SUPPORTS).upsert(support.toRow()) { ignoreDuplicates = true }
        }

    /** Row-level security returns only the caller's own stars. */
    override suspend fun fetchOwnSupports(): RemoteResult<List<IssueSupport>> =
        remote(client) { supabase ->
            supabase.from(SUPPORTS).select().decodeList<SupportRow>().map(SupportRow::toIssueSupport)
        }

    private companion object {
        const val ISSUES = "issues"
        const val STATUS_CHANGES = "issue_status_changes"
        const val SUPPORTS = "issue_supports"
    }
}

/**
 * Departments and neighborhoods as the backend holds them.
 *
 * A deployment that never edits either can leave both tables empty and let the
 * `CityProfile` stay authoritative: empty lists and
 * [RemoteResult.NotConfigured] are handled the same way upstream.
 */
internal class SupabaseReferenceDataBackendApi(
    private val client: SupabaseClient?,
) : ReferenceDataBackendApi {

    override suspend fun fetchDepartments(): RemoteResult<List<Department>> =
        remote(client) { supabase ->
            supabase.from("departments").select().decodeList<DepartmentRow>()
                .map(DepartmentRow::toDepartment)
        }

    override suspend fun fetchNeighborhoods(): RemoteResult<List<Neighborhood>> =
        remote(client) { supabase ->
            supabase.from("neighborhoods").select().decodeList<NeighborhoodRow>()
                .map(NeighborhoodRow::toNeighborhood)
        }
}

/**
 * Issue photos: the bytes in a public Storage bucket, and a row per photo in
 * `issue_photos` so other devices can find them.
 *
 * Both writes are keyed by photo id, so a replayed upload overwrites its own
 * object and leaves its row alone instead of littering either with duplicates.
 * The object goes first: a row is only ever written for bytes that are there.
 */
internal class SupabasePhotoBackendApi(
    private val client: SupabaseClient?,
    private val bucket: String,
) : PhotoBackendApi {

    override suspend fun uploadPhoto(issueId: String, photo: IssuePhoto): RemoteResult<String> {
        val bytes = photo.bytes
            ?: return RemoteResult.Failure("Photo ${photo.id} has no bytes to upload.", retryable = false)
        return remote(client) { supabase ->
            val path = "$issueId/${photo.id}"
            supabase.storage.from(bucket).upload(path, bytes) {
                upsert = true
                contentType = imageContentType(bytes)
            }
            supabase.from(PHOTOS).upsert(
                PhotoRow(
                    id = photo.id,
                    issueId = issueId,
                    storagePath = path,
                    caption = photo.caption,
                    createdAtMillis = photo.createdAtMillis,
                ),
            ) { ignoreDuplicates = true }
            supabase.storage.from(bucket).publicUrl(path)
        }
    }

    override suspend fun fetchPhotosUploadedSince(sinceMillis: Long?): RemoteResult<List<IssuePhoto>> =
        remote(client) { supabase ->
            val storage = supabase.storage.from(bucket)
            supabase.from(PHOTOS)
                .select {
                    if (sinceMillis != null) filter { gt("uploaded_at_millis", sinceMillis) }
                }
                .decodeList<StoredPhotoRow>()
                .map { row -> row.toIssuePhoto(storage.publicUrl(row.storagePath)) }
        }

    private companion object {
        const val PHOTOS = "issue_photos"
    }
}

/**
 * The image type named by [bytes]' signature. The bucket accepts only the
 * types listed in `schema.sql`, so a file that is none of them is sent as
 * `application/octet-stream` and refused there.
 */
private fun imageContentType(bytes: ByteArray): ContentType {
    fun startsWith(offset: Int, vararg signature: Int) =
        bytes.size >= offset + signature.size &&
            signature.indices.all { bytes[offset + it] == signature[it].toByte() }

    return when {
        startsWith(0, 0xFF, 0xD8, 0xFF) -> ContentType.Image.JPEG
        startsWith(0, 0x89, 0x50, 0x4E, 0x47) -> ContentType.Image.PNG
        startsWith(0, 0x47, 0x49, 0x46, 0x38) -> ContentType.Image.GIF
        startsWith(0, 0x52, 0x49, 0x46, 0x46) && startsWith(8, 0x57, 0x45, 0x42, 0x50) ->
            ContentType("image", "webp")
        startsWith(4, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69) ->
            ContentType("image", "heic")
        else -> ContentType.Application.OctetStream
    }
}

/**
 * Runs [block] against a configured client, turning the two failures Supabase
 * reports into the two the sync engine knows how to act on.
 *
 * A null client is [RemoteResult.NotConfigured] rather than an error: a build
 * that selected this backend before the project existed should behave like a
 * build with no backend, not like a broken one.
 */
private suspend inline fun <T> remote(
    client: SupabaseClient?,
    block: (SupabaseClient) -> T,
): RemoteResult<T> {
    if (client == null) return RemoteResult.NotConfigured
    return try {
        RemoteResult.Success(block(client))
    } catch (e: RestException) {
        // Postgres said no -- a constraint, or a policy that does not allow
        // this. Retrying sends the identical request, so it stays no.
        // Not `e.message`: that carries the request headers, session token included.
        RemoteResult.Failure(e.error, retryable = false, cause = e)
    } catch (e: HttpRequestException) {
        RemoteResult.Failure("Could not reach Supabase.", retryable = true, cause = e)
    }
}
