package dev.helpmycity.cityconfig.supabase

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.EditableField
import dev.helpmycity.domain.model.ExternalReference
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssueEdit
import dev.helpmycity.domain.model.IssueLocation
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueReview
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.ReporterContact
import dev.helpmycity.domain.model.ReviewState
import dev.helpmycity.domain.model.SyncMetadata
import dev.helpmycity.domain.model.SyncState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Rows are `internal`: the column contract is this module's business, and a
// fork that changes the schema should not break anything that imports it.
//
// Enums travel as their `storageKey`, never `Enum.name` -- renaming a constant
// has to stay a safe refactor, and these strings are in someone's database.
//
// Value objects with no enums in them (location, reporter, external reference)
// are reused from the domain as-is and land in `jsonb` columns, which is why
// their keys are camelCase where the columns around them are snake_case. They
// are opaque payload; nothing queries inside them.

@Serializable
internal data class IssueRow(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("requested_action") val requestedAction: String,
    val category: String,
    val status: String,
    val priority: String,
    val location: IssueLocation,
    @SerialName("department_id") val departmentId: String? = null,
    val reporter: ReporterContact? = null,
    @SerialName("submitted_by_user_id") val submittedByUserId: String? = null,
    @SerialName("review_state") val reviewState: String,
    @SerialName("reviewed_by_user_id") val reviewedByUserId: String? = null,
    @SerialName("reviewed_by_display_name") val reviewedByDisplayName: String? = null,
    @SerialName("reviewed_at_millis") val reviewedAtMillis: Long? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("last_edit") val lastEdit: EditRow? = null,
    @SerialName("notes_source") val notesSource: String,
    @SerialName("external_reference") val externalReference: ExternalReference? = null,
    @SerialName("support_count") val supportCount: Int,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
)

@Serializable
internal data class EditRow(
    @SerialName("edited_by_user_id") val editedByUserId: String? = null,
    @SerialName("edited_by_display_name") val editedByDisplayName: String? = null,
    @SerialName("edited_at_millis") val editedAtMillis: Long,
    val revision: Int,
    val fields: List<String>,
)

@Serializable
internal data class StatusChangeRow(
    val id: String,
    @SerialName("issue_id") val issueId: String,
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String,
    val note: String? = null,
    @SerialName("changed_by_user_id") val changedByUserId: String? = null,
    @SerialName("changed_by_display_name") val changedByDisplayName: String? = null,
    @SerialName("changed_at_millis") val changedAtMillis: Long,
)

@Serializable
internal data class SupportRow(
    @SerialName("issue_id") val issueId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at_millis") val createdAtMillis: Long,
)

/**
 * One photo as `issue_photos` holds it. The object path is stored rather than a
 * URL, so the URL can be rebuilt if the project or bucket moves.
 */
@Serializable
internal data class PhotoRow(
    val id: String,
    @SerialName("issue_id") val issueId: String,
    @SerialName("storage_path") val storagePath: String,
    val caption: String? = null,
    @SerialName("created_at_millis") val createdAtMillis: Long,
)

/** A [PhotoRow] read back, with the server-stamped time it arrived. */
@Serializable
internal data class StoredPhotoRow(
    val id: String,
    @SerialName("issue_id") val issueId: String,
    @SerialName("storage_path") val storagePath: String,
    val caption: String? = null,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("uploaded_at_millis") val uploadedAtMillis: Long,
)

@Serializable
internal data class DepartmentRow(
    val id: String,
    val name: String,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("handles_categories") val handlesCategories: List<String> = emptyList(),
)

@Serializable
internal data class NeighborhoodRow(
    val id: String,
    val name: String,
    @SerialName("council_district") val councilDistrict: String? = null,
    @SerialName("boundary_geojson") val boundaryGeoJson: String? = null,
)

// --- domain -> row -------------------------------------------------------
//
// `SyncMetadata` is deliberately dropped on the way out. It is local
// bookkeeping about *this device's* queue; writing it to a shared table would
// hand every other device a meaningless `pending_upload` and keep the sync
// engine pushing rows forever.

internal fun Issue.toRow(): IssueRow = IssueRow(
    id = id,
    title = title,
    description = description,
    requestedAction = requestedAction,
    category = category.storageKey,
    status = status.storageKey,
    priority = priority.storageKey,
    location = location,
    departmentId = departmentId,
    reporter = reporter?.takeUnless { it.isEmpty },
    submittedByUserId = submittedByUserId,
    reviewState = review.state.storageKey,
    reviewedByUserId = review.reviewedByUserId,
    reviewedByDisplayName = review.reviewedByDisplayName,
    reviewedAtMillis = review.reviewedAtMillis,
    rejectionReason = review.rejectionReason,
    lastEdit = lastEdit?.toRow(),
    notesSource = notesSource,
    externalReference = externalReference,
    supportCount = supportCount,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

internal fun IssueEdit.toRow(): EditRow = EditRow(
    editedByUserId = editedByUserId,
    editedByDisplayName = editedByDisplayName,
    editedAtMillis = editedAtMillis,
    revision = revision,
    fields = fields.map(EditableField::storageKey),
)

internal fun IssueStatusChange.toRow(): StatusChangeRow = StatusChangeRow(
    id = id,
    issueId = issueId,
    fromStatus = fromStatus?.storageKey,
    toStatus = toStatus.storageKey,
    note = note,
    changedByUserId = changedByUserId,
    changedByDisplayName = changedByDisplayName,
    changedAtMillis = changedAtMillis,
)

internal fun IssueSupport.toRow(): SupportRow = SupportRow(
    issueId = issueId,
    userId = userId,
    createdAtMillis = createdAtMillis,
)

// --- row -> domain -------------------------------------------------------
//
// A row that came back from the backend is by definition agreed with it, so
// every one is rebuilt as [SyncState.SYNCED]. `updated_at_millis` doubles as
// the version: an edit that did not change it did not happen.

internal fun IssueRow.toIssue(): Issue = Issue(
    id = id,
    title = title,
    description = description,
    requestedAction = requestedAction,
    category = IssueCategory.fromStorageKey(category),
    status = IssueStatus.fromStorageKey(status),
    priority = IssuePriority.fromStorageKey(priority),
    location = location,
    departmentId = departmentId,
    reporter = reporter,
    submittedByUserId = submittedByUserId,
    // `schema.sql` carries the matching CHECK constraint, so a rejected row
    // without a reason -- which IssueReview refuses to construct -- cannot
    // exist to be read back.
    review = IssueReview(
        state = ReviewState.fromStorageKey(reviewState),
        reviewedByUserId = reviewedByUserId,
        reviewedByDisplayName = reviewedByDisplayName,
        reviewedAtMillis = reviewedAtMillis,
        rejectionReason = rejectionReason,
    ),
    lastEdit = lastEdit?.toIssueEdit(),
    notesSource = notesSource,
    externalReference = externalReference,
    supportCount = supportCount,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    sync = SyncMetadata(
        state = SyncState.SYNCED,
        lastSyncedAtMillis = updatedAtMillis,
        remoteVersion = updatedAtMillis.toString(),
    ),
)

internal fun EditRow.toIssueEdit(): IssueEdit = IssueEdit(
    editedByUserId = editedByUserId,
    editedByDisplayName = editedByDisplayName,
    editedAtMillis = editedAtMillis,
    revision = revision,
    // An unknown key is a row written by a newer build; drop it rather than crash.
    fields = fields.mapNotNull { EditableField.fromStorageKeyOrNull(it) }.toSet(),
)

internal fun SupportRow.toIssueSupport(): IssueSupport = IssueSupport(
    issueId = issueId,
    userId = userId,
    createdAtMillis = createdAtMillis,
    syncState = SyncState.SYNCED,
)

internal fun StoredPhotoRow.toIssuePhoto(publicUrl: String): IssuePhoto = IssuePhoto(
    id = id,
    issueId = issueId,
    remoteUrl = publicUrl,
    caption = caption,
    createdAtMillis = createdAtMillis,
    sync = SyncMetadata(state = SyncState.SYNCED, lastSyncedAtMillis = uploadedAtMillis),
)

internal fun DepartmentRow.toDepartment(): Department = Department(
    id = id,
    name = name,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    handlesCategories = handlesCategories.map { IssueCategory.fromStorageKey(it) },
    sync = SyncMetadata(state = SyncState.SYNCED),
)

internal fun NeighborhoodRow.toNeighborhood(): Neighborhood = Neighborhood(
    id = id,
    name = name,
    councilDistrict = councilDistrict,
    boundaryGeoJson = boundaryGeoJson,
    sync = SyncMetadata(state = SyncState.SYNCED),
)
