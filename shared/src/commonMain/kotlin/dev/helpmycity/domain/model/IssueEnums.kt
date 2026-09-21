package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * The stages an issue moves through, and the columns of the triage board.
 *
 * [IN_REVIEW] and [REJECTED] follow [IssueReview]: an issue is in review until
 * a manager decides, and rejected if they turn it down. The other three are the
 * city's work. A rejected issue is kept, for its submitter and its managers, but
 * off the list, board and map.
 *
 * [storageKey] is the stable string persisted locally and remotely -- never
 * persist [Enum.name] or the ordinal, so renaming a constant stays a safe
 * refactor and exported CSVs stay stable.
 */
@Serializable
enum class IssueStatus(val storageKey: String) {
    IN_REVIEW("in_review"),
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    /** Always carries [Issue.resolution]. */
    COMPLETE("complete"),
    /** Always carries [IssueReview.rejectionReason]. */
    REJECTED("rejected"),
    ;

    companion object {
        /** Left-to-right order of the manager kanban board, and the list's filters. */
        val boardOrder: List<IssueStatus> = listOf(IN_REVIEW, OPEN, IN_PROGRESS, COMPLETE)

        /** What a manager can move an approved issue between. */
        val workflow: List<IssueStatus> = listOf(OPEN, IN_PROGRESS, COMPLETE)

        fun fromStorageKey(key: String): IssueStatus =
            entries.firstOrNull { it.storageKey == key } ?: IN_REVIEW
    }
}

@Serializable
enum class IssuePriority(val storageKey: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low"),
    ;

    companion object {
        fun fromStorageKey(key: String): IssuePriority =
            entries.firstOrNull { it.storageKey == key } ?: MEDIUM
    }
}

@Serializable
enum class IssueCategory(val storageKey: String) {
    ROAD_SURFACE("road_surface"),
    STREET_LIGHTING("street_lighting"),
    DRAINAGE("drainage"),
    ADA_ACCESS("ada_access"),
    TRAFFIC_SAFETY("traffic_safety"),
    SIDEWALK("sidewalk"),
    PARK_MAINTENANCE("park_maintenance"),
    TRASH_DUMPING("trash_dumping"),
    GRAFFITI("graffiti"),
    WATER_UTILITIES("water_utilities"),
    SIGNAGE("signage"),
    OTHER("other"),
    ;

    companion object {
        fun fromStorageKey(key: String): IssueCategory =
            entries.firstOrNull { it.storageKey == key } ?: OTHER
    }
}

/**
 * Offline-first bookkeeping. Every locally-written row starts [PENDING_UPLOAD]
 * and the sync engine moves it to [SYNCED] once the backend acknowledges it.
 */
@Serializable
enum class SyncState(val storageKey: String) {
    /** Written locally, not yet accepted by the backend. */
    PENDING_UPLOAD("pending_upload"),
    /** Deleted locally, backend not yet told. */
    PENDING_DELETE("pending_delete"),
    /** Local and backend agree as of [SyncMetadata.lastSyncedAtMillis]. */
    SYNCED("synced"),
    /** Upload was rejected; needs a human or a retry with backoff. */
    FAILED("failed"),
    ;

    companion object {
        fun fromStorageKey(key: String): SyncState =
            entries.firstOrNull { it.storageKey == key } ?: PENDING_UPLOAD
    }
}

@Serializable
data class SyncMetadata(
    val state: SyncState = SyncState.PENDING_UPLOAD,
    val lastSyncedAtMillis: Long? = null,
    /** Backend-assigned version/etag, used to detect conflicting edits. */
    val remoteVersion: String? = null,
) {
    companion object {
        val LocalOnly: SyncMetadata = SyncMetadata()
    }
}
