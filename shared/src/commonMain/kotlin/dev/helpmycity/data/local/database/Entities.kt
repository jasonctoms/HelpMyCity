package dev.helpmycity.data.local.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Storage shape for [dev.helpmycity.domain.model.Issue].
 *
 * Every column is a SQLite primitive and enums are stored by their stable
 * `storageKey` rather than through a type converter: the schema stays readable
 * in a plain `sqlite3` shell, export stays trivial, and renaming a Kotlin enum
 * constant is never a migration.
 */
@Entity(
    tableName = "issues",
    indices = [
        Index("status"),
        Index("category"),
        Index("neighborhood"),
        Index("council_district"),
        Index("review_state"),
        Index("sync_state"),
        Index("updated_at"),
    ],
)
data class IssueEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "requested_action") val requestedAction: String,
    val category: String,
    val status: String,
    val priority: String,

    @ColumnInfo(name = "location_description") val locationDescription: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "geocoded_address") val geocodedAddress: String?,
    val neighborhood: String?,
    @ColumnInfo(name = "council_district") val councilDistrict: String?,
    @ColumnInfo(name = "census_tract") val censusTract: String?,
    @ColumnInfo(name = "police_precinct") val policePrecinct: String?,

    @ColumnInfo(name = "department_id") val departmentId: String?,
    @ColumnInfo(name = "reporter_name") val reporterName: String?,
    @ColumnInfo(name = "reporter_email") val reporterEmail: String?,
    @ColumnInfo(name = "reporter_phone") val reporterPhone: String?,
    @ColumnInfo(name = "submitted_by_user_id") val submittedByUserId: String?,
    @ColumnInfo(name = "notes_source") val notesSource: String,

    /** [dev.helpmycity.domain.model.ReviewState.storageKey]. */
    @ColumnInfo(name = "review_state") val reviewState: String,
    @ColumnInfo(name = "reviewed_by_user_id") val reviewedByUserId: String?,
    @ColumnInfo(name = "reviewed_by_display_name") val reviewedByDisplayName: String?,
    @ColumnInfo(name = "reviewed_at") val reviewedAtMillis: Long?,
    /** Set exactly when `review_state` is `rejected`; shown to the submitter. */
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String?,

    /**
     * The last manager correction, null until there is one. `edited_fields` is
     * a comma-separated list of
     * [dev.helpmycity.domain.model.EditableField.storageKey].
     */
    @ColumnInfo(name = "edited_by_user_id") val editedByUserId: String?,
    @ColumnInfo(name = "edited_by_display_name") val editedByDisplayName: String?,
    @ColumnInfo(name = "edited_at") val editedAtMillis: Long?,
    @ColumnInfo(name = "edit_revision") val editRevision: Int?,
    @ColumnInfo(name = "edited_fields") val editedFields: String?,

    @ColumnInfo(name = "external_system") val externalSystem: String?,
    @ColumnInfo(name = "external_reference_number") val externalReferenceNumber: String?,
    @ColumnInfo(name = "external_submitted_at") val externalSubmittedAtMillis: Long?,
    @ColumnInfo(name = "external_last_checked_at") val externalLastCheckedAtMillis: Long?,
    @ColumnInfo(name = "external_reported_status") val externalReportedStatus: String?,

    @ColumnInfo(name = "support_count") val supportCount: Int,

    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,

    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAtMillis: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
)

/**
 * Storage shape for [dev.helpmycity.domain.model.IssuePhoto].
 *
 * A child table rather than a column on `issues`: the list screen reads every
 * issue row on every render, and image bytes in that row would make scrolling
 * read megabytes.
 */
@Entity(
    tableName = "issue_photos",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issue_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("issue_id"), Index("sync_state")],
)
class IssuePhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "issue_id") val issueId: String,
    /** The image. Null once uploaded and the local copy has been reclaimed. */
    val bytes: ByteArray?,
    @ColumnInfo(name = "remote_url") val remoteUrl: String?,
    val caption: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAtMillis: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
)

/**
 * Storage shape for [dev.helpmycity.domain.model.IssueSupport]. No foreign key:
 * a pulled star can arrive before the issue it belongs to, and a star on an
 * issue that is gone is harmless.
 */
@Entity(
    tableName = "issue_supports",
    primaryKeys = ["issue_id", "user_id"],
    indices = [Index("user_id"), Index("sync_state")],
)
data class IssueSupportEntity(
    @ColumnInfo(name = "issue_id") val issueId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

@Entity(
    tableName = "issue_status_changes",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issue_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("issue_id"), Index("sync_state")],
)
data class IssueStatusChangeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "issue_id") val issueId: String,
    @ColumnInfo(name = "from_status") val fromStatus: String?,
    @ColumnInfo(name = "to_status") val toStatus: String,
    val note: String?,
    @ColumnInfo(name = "changed_by_user_id") val changedByUserId: String?,
    @ColumnInfo(name = "changed_by_display_name") val changedByDisplayName: String?,
    @ColumnInfo(name = "changed_at") val changedAtMillis: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAtMillis: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
)

@Entity(tableName = "departments")
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "contact_email") val contactEmail: String?,
    @ColumnInfo(name = "contact_phone") val contactPhone: String?,
    /** Comma-separated `IssueCategory.storageKey` values. */
    @ColumnInfo(name = "handles_categories") val handlesCategories: String,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAtMillis: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
)

@Entity(tableName = "neighborhoods")
data class NeighborhoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "council_district") val councilDistrict: String?,
    /** GeoJSON polygon, for a deployment that has one from the city's GIS source. */
    @ColumnInfo(name = "boundary_geojson") val boundaryGeoJson: String?,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAtMillis: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
)

/**
 * Storage shape for [dev.helpmycity.domain.model.User] -- who this
 * deployment knows and what each of them may do.
 *
 * The only table with no sync columns. In a real deployment roles and manager
 * scopes belong to the identity provider rather than to this app's data, so
 * there is nothing here to queue for upload; the table exists because the mock
 * provider has nothing to read them from, and it retires behind
 * [dev.helpmycity.domain.repository.UserRepository] the day one does.
 */
@Entity(tableName = "users", indices = [Index("email")])
data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** Never null in practice: a guest is never written here. */
    val email: String?,
    /** [dev.helpmycity.domain.model.UserRole.storageKey]. */
    val role: String,

    /** [dev.helpmycity.domain.model.ManagerScope], as four columns. */
    @ColumnInfo(name = "scope_citywide") val scopeCitywide: Boolean,
    /** Comma-separated council district ids. */
    @ColumnInfo(name = "scope_districts") val scopeDistricts: String,
    @ColumnInfo(name = "scope_neighborhoods") val scopeNeighborhoods: String,
    @ColumnInfo(name = "scope_departments") val scopeDepartments: String,
)
