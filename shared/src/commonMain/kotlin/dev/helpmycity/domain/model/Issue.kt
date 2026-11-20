package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * The core record of the app, one neighborhood issue.
 *
 * This is the portable shape that does not care about the backend. The Room
 * entities in `data.local.database` and a backend's own row types both map to and
 * from it, so swapping out either side never touches the domain or the UI.
 */
@Serializable
data class Issue(
    val id: String,
    val title: String,
    val description: String,
    val requestedAction: String,
    val category: IssueCategory,
    val status: IssueStatus,
    val priority: IssuePriority,
    val location: IssueLocation,
    val departmentId: String?,
    /** Optional, since residents can stay anonymous. */
    val reporter: ReporterContact?,
    /**
     * The account that filed this, if there was one. Null for a report filed with
     * no session at all, and for imported or seeded rows.
     *
     * This is different from [reporter], which is whatever contact details the
     * submitter decided to type in. This one is identity, and it is what lets
     * someone follow their own report through triage (including reading why it got
     * turned down). See [dev.helpmycity.domain.access.canSee].
     */
    val submittedByUserId: String?,
    /** Triage decision. A report is not public until a manager approves it. */
    val review: IssueReview,
    /**
     * Null until a manager fixes one of the fields above. Everyone who can see the
     * issue can see this, not just managers (see [IssueEdit]).
     */
    val lastEdit: IssueEdit? = null,
    /** What the city did about it. Set exactly when [status] is [IssueStatus.COMPLETE]. */
    val resolution: String? = null,
    /** "resident request", "walk audit", "ownership unclear", ... */
    val notesSource: String,
    /** Set once the issue has been filed with the city's own request system. */
    val externalReference: ExternalReference?,
    /** "Is this already reported near you?" Residents can pile on instead of filing a duplicate. */
    val supportCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sync: SyncMetadata,
)

/**
 * Where the issue is. [description] is always filled in, because that is the one
 * thing a resident can reliably give us. A [point] comes from geocoding the
 * description, or from whoever drops the pin.
 *
 * [neighborhood] holds a [Neighborhood.id], and [councilDistrict] gets worked out
 * from it when the issue is submitted. Both of them help decide which manager an
 * issue belongs to, so an issue with neither one can only be reviewed by a
 * citywide manager or an admin.
 */
@Serializable
data class IssueLocation(
    val description: String,
    val point: GeoPoint? = null,
    val geocodedAddress: String? = null,
    val neighborhood: String? = null,
    val councilDistrict: String? = null,
    val censusTract: String? = null,
    val policePrecinct: String? = null,
)

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class ReporterContact(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
) {
    val isEmpty: Boolean get() = name.isNullOrBlank() && email.isNullOrBlank() && phone.isNullOrBlank()
}

/**
 * A pointer into the city's own request system, whichever one this deployment
 * hands off to. Municipal tracking is usually pretty unreliable, so we record what
 * we submitted and when we last checked, instead of assuming their status is the
 * truth.
 *
 * [system] is the gateway's stable
 * [dev.helpmycity.data.remote.external.ExternalRequestGateway.systemId] and never
 * a display name, so exported data still makes sense after a rename.
 */
@Serializable
data class ExternalReference(
    val system: String,
    val referenceNumber: String? = null,
    val submittedAtMillis: Long? = null,
    val lastCheckedAtMillis: Long? = null,
    val reportedStatus: String? = null,
)

/**
 * One photo attached to an issue.
 *
 * This is not a field on [Issue]. Photos are read per issue, the same way
 * [IssueStatusChange] history is, so listing a city's issues never drags image
 * bytes off the disk.
 *
 * [bytes] is the image itself, kept locally until a backend accepts it. It is what
 * [dev.helpmycity.data.remote.PhotoBackendApi.uploadPhoto] uploads, and it is why
 * the bytes live in the database instead of behind a platform file URI. On
 * Android, a picked `content://` URI stops being readable once the picker's grant
 * runs out, so keeping only the URI would leave rows pointing at nothing.
 *
 * [remoteUrl] gets filled in once the photo is uploaded. Readers use it over
 * [bytes] when they can and let the image loader cache it (see
 * `ui/components/IssuePhotos.kt`).
 */
@Serializable
data class IssuePhoto(
    val id: String,
    val issueId: String,
    /** Null only for a row whose bytes have been dropped after a successful upload. */
    val bytes: ByteArray? = null,
    /** Backend URL, present once the photo is uploaded. */
    val remoteUrl: String? = null,
    val caption: String? = null,
    val createdAtMillis: Long = 0L,
    val sync: SyncMetadata = SyncMetadata.LocalOnly,
) {
    /** Nothing to show and nothing to upload, so the row can be skipped. */
    val isEmpty: Boolean get() = bytes == null && remoteUrl == null

    // A data class compares ByteArray by identity, which would make two equal
    // photos read as different. Compare contents instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuePhoto) return false
        return id == other.id &&
            issueId == other.issueId &&
            remoteUrl == other.remoteUrl &&
            caption == other.caption &&
            createdAtMillis == other.createdAtMillis &&
            sync == other.sync &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + issueId.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (remoteUrl?.hashCode() ?: 0)
        result = 31 * result + (caption?.hashCode() ?: 0)
        result = 31 * result + createdAtMillis.hashCode()
        result = 31 * result + sync.hashCode()
        return result
    }
}

/**
 * One user's star on one issue. A user can only star an issue once, so the pair
 * is the identity, and [Issue.supportCount] is the backend's count of these rows.
 */
data class IssueSupport(
    val issueId: String,
    val userId: String,
    val createdAtMillis: Long,
    val syncState: SyncState = SyncState.PENDING_UPLOAD,
)
