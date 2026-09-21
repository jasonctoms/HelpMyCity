package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * Append-only audit entry. Drives issue history in the UI and "how long has this
 * been sitting?" reporting, and it is the reason the app can be the system of
 * record when the city's own tracking is not.
 */
@Serializable
data class IssueStatusChange(
    val id: String,
    val issueId: String,
    val fromStatus: IssueStatus?,
    val toStatus: IssueStatus,
    val note: String? = null,
    val changedByUserId: String? = null,
    val changedByDisplayName: String? = null,
    val changedAtMillis: Long,
    val sync: SyncMetadata = SyncMetadata.LocalOnly,
)
