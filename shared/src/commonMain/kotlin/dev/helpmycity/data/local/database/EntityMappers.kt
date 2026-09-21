package dev.helpmycity.data.local.database

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.EditableField
import dev.helpmycity.domain.model.ExternalReference
import dev.helpmycity.domain.model.GeoPoint
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
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.ReporterContact
import dev.helpmycity.domain.model.ReviewState
import dev.helpmycity.domain.model.SyncMetadata
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole


internal fun IssueEntity.toDomain(): Issue = Issue(
    id = id,
    title = title,
    description = description,
    requestedAction = requestedAction,
    category = IssueCategory.fromStorageKey(category),
    status = IssueStatus.fromStorageKey(status),
    priority = IssuePriority.fromStorageKey(priority),
    location = IssueLocation(
        description = locationDescription,
        point = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
        geocodedAddress = geocodedAddress,
        neighborhood = neighborhood,
        councilDistrict = councilDistrict,
        censusTract = censusTract,
        policePrecinct = policePrecinct,
    ),
    departmentId = departmentId,
    reporter = ReporterContact(reporterName, reporterEmail, reporterPhone)
        .takeUnless { it.isEmpty },
    submittedByUserId = submittedByUserId,
    review = toReview(),
    lastEdit = toLastEdit(),
    notesSource = notesSource,
    externalReference = externalSystem?.let {
        ExternalReference(
            system = it,
            referenceNumber = externalReferenceNumber,
            submittedAtMillis = externalSubmittedAtMillis,
            lastCheckedAtMillis = externalLastCheckedAtMillis,
            reportedStatus = externalReportedStatus,
        )
    },
    supportCount = supportCount,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    sync = SyncMetadata(
        state = SyncState.fromStorageKey(syncState),
        lastSyncedAtMillis = lastSyncedAtMillis,
        remoteVersion = remoteVersion,
    ),
)

/**
 * [IssueReview] refuses to hold a rejection with no reason. A row that carries
 * one anyway reads back as awaiting review, rather than throwing and taking the
 * whole list down with it.
 */
private fun IssueEntity.toReview(): IssueReview {
    val state = ReviewState.fromStorageKey(reviewState)
    val reason = rejectionReason?.takeUnless { it.isBlank() }
    if (state == ReviewState.REJECTED && reason == null) return IssueReview.Pending
    return IssueReview(
        state = state,
        reviewedByUserId = reviewedByUserId,
        reviewedByDisplayName = reviewedByDisplayName,
        reviewedAtMillis = reviewedAtMillis,
        rejectionReason = reason,
    )
}

/** Null unless the row carries a timestamp and at least one readable field. */
private fun IssueEntity.toLastEdit(): IssueEdit? {
    val at = editedAtMillis ?: return null
    val fields = editedFields.orEmpty()
        .split(',')
        .mapNotNull { EditableField.fromStorageKeyOrNull(it.trim()) }
        .toSet()
    if (fields.isEmpty()) return null
    return IssueEdit(
        editedByUserId = editedByUserId,
        editedByDisplayName = editedByDisplayName,
        editedAtMillis = at,
        revision = editRevision?.coerceAtLeast(1) ?: 1,
        fields = fields,
    )
}

internal fun Issue.toEntity(): IssueEntity = IssueEntity(
    id = id,
    title = title,
    description = description,
    requestedAction = requestedAction,
    category = category.storageKey,
    status = status.storageKey,
    priority = priority.storageKey,
    locationDescription = location.description,
    latitude = location.point?.latitude,
    longitude = location.point?.longitude,
    geocodedAddress = location.geocodedAddress,
    neighborhood = location.neighborhood,
    councilDistrict = location.councilDistrict,
    censusTract = location.censusTract,
    policePrecinct = location.policePrecinct,
    departmentId = departmentId,
    reporterName = reporter?.name,
    reporterEmail = reporter?.email,
    reporterPhone = reporter?.phone,
    submittedByUserId = submittedByUserId,
    notesSource = notesSource,
    reviewState = review.state.storageKey,
    reviewedByUserId = review.reviewedByUserId,
    reviewedByDisplayName = review.reviewedByDisplayName,
    reviewedAtMillis = review.reviewedAtMillis,
    rejectionReason = review.rejectionReason,
    editedByUserId = lastEdit?.editedByUserId,
    editedByDisplayName = lastEdit?.editedByDisplayName,
    editedAtMillis = lastEdit?.editedAtMillis,
    editRevision = lastEdit?.revision,
    editedFields = lastEdit?.fields?.joinToString(",") { it.storageKey },
    externalSystem = externalReference?.system,
    externalReferenceNumber = externalReference?.referenceNumber,
    externalSubmittedAtMillis = externalReference?.submittedAtMillis,
    externalLastCheckedAtMillis = externalReference?.lastCheckedAtMillis,
    externalReportedStatus = externalReference?.reportedStatus,
    supportCount = supportCount,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    syncState = sync.state.storageKey,
    lastSyncedAtMillis = sync.lastSyncedAtMillis,
    remoteVersion = sync.remoteVersion,
)

internal fun IssuePhotoEntity.toDomain(): IssuePhoto = IssuePhoto(
    id = id,
    issueId = issueId,
    bytes = bytes,
    remoteUrl = remoteUrl,
    caption = caption,
    createdAtMillis = createdAtMillis,
    sync = SyncMetadata(
        state = SyncState.fromStorageKey(syncState),
        lastSyncedAtMillis = lastSyncedAtMillis,
        remoteVersion = remoteVersion,
    ),
)

internal fun IssuePhoto.toEntity(): IssuePhotoEntity = IssuePhotoEntity(
    id = id,
    issueId = issueId,
    bytes = bytes,
    remoteUrl = remoteUrl,
    caption = caption,
    createdAtMillis = createdAtMillis,
    syncState = sync.state.storageKey,
    lastSyncedAtMillis = sync.lastSyncedAtMillis,
    remoteVersion = sync.remoteVersion,
)

internal fun IssueSupportEntity.toDomain(): IssueSupport = IssueSupport(
    issueId = issueId,
    userId = userId,
    createdAtMillis = createdAtMillis,
    syncState = SyncState.fromStorageKey(syncState),
)

internal fun IssueSupport.toEntity(): IssueSupportEntity = IssueSupportEntity(
    issueId = issueId,
    userId = userId,
    createdAtMillis = createdAtMillis,
    syncState = syncState.storageKey,
)

internal fun IssueStatusChangeEntity.toDomain(): IssueStatusChange = IssueStatusChange(
    id = id,
    issueId = issueId,
    fromStatus = fromStatus?.let(IssueStatus::fromStorageKey),
    toStatus = IssueStatus.fromStorageKey(toStatus),
    note = note,
    changedByUserId = changedByUserId,
    changedByDisplayName = changedByDisplayName,
    changedAtMillis = changedAtMillis,
    sync = SyncMetadata(
        state = SyncState.fromStorageKey(syncState),
        lastSyncedAtMillis = lastSyncedAtMillis,
        remoteVersion = remoteVersion,
    ),
)

internal fun IssueStatusChange.toEntity(): IssueStatusChangeEntity = IssueStatusChangeEntity(
    id = id,
    issueId = issueId,
    fromStatus = fromStatus?.storageKey,
    toStatus = toStatus.storageKey,
    note = note,
    changedByUserId = changedByUserId,
    changedByDisplayName = changedByDisplayName,
    changedAtMillis = changedAtMillis,
    syncState = sync.state.storageKey,
    lastSyncedAtMillis = sync.lastSyncedAtMillis,
    remoteVersion = sync.remoteVersion,
)

internal fun DepartmentEntity.toDomain(): Department = Department(
    id = id,
    name = name,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    handlesCategories = handlesCategories.split(',')
        .filter { it.isNotBlank() }
        .map(IssueCategory::fromStorageKey),
    sync = SyncMetadata(
        state = SyncState.fromStorageKey(syncState),
        lastSyncedAtMillis = lastSyncedAtMillis,
        remoteVersion = remoteVersion,
    ),
)

internal fun Department.toEntity(): DepartmentEntity = DepartmentEntity(
    id = id,
    name = name,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    handlesCategories = handlesCategories.joinToString(",") { it.storageKey },
    syncState = sync.state.storageKey,
    lastSyncedAtMillis = sync.lastSyncedAtMillis,
    remoteVersion = sync.remoteVersion,
)

internal fun NeighborhoodEntity.toDomain(): Neighborhood = Neighborhood(
    id = id,
    name = name,
    councilDistrict = councilDistrict,
    boundaryGeoJson = boundaryGeoJson,
    sync = SyncMetadata(
        state = SyncState.fromStorageKey(syncState),
        lastSyncedAtMillis = lastSyncedAtMillis,
        remoteVersion = remoteVersion,
    ),
)

internal fun Neighborhood.toEntity(): NeighborhoodEntity = NeighborhoodEntity(
    id = id,
    name = name,
    councilDistrict = councilDistrict,
    boundaryGeoJson = boundaryGeoJson,
    syncState = sync.state.storageKey,
    lastSyncedAtMillis = sync.lastSyncedAtMillis,
    remoteVersion = sync.remoteVersion,
)

internal fun UserEntity.toDomain(): User = User(
    id = id,
    displayName = displayName,
    email = email,
    role = UserRole.fromStorageKey(role),
    scope = ManagerScope(
        citywide = scopeCitywide,
        districts = scopeDistricts.toIdSet(),
        neighborhoodIds = scopeNeighborhoods.toIdSet(),
        departmentIds = scopeDepartments.toIdSet(),
    ),
)

internal fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    displayName = displayName,
    email = email,
    role = role.storageKey,
    scopeCitywide = scope.citywide,
    scopeDistricts = scope.districts.toIdColumn(),
    scopeNeighborhoods = scope.neighborhoodIds.toIdColumn(),
    scopeDepartments = scope.departmentIds.toIdColumn(),
)

/** Same comma-separated convention as `departments.handles_categories`. */
private fun String.toIdSet(): Set<String> =
    split(',').map(String::trim).filter(String::isNotEmpty).toSet()

private fun Set<String>.toIdColumn(): String = sorted().joinToString(",")
