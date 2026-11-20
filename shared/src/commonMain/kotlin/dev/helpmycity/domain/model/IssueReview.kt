package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * Where an issue sits in triage.
 *
 * A resident's report does not go public the moment it is filed. A manager
 * responsible for that area reads it first, and until then only the submitter and
 * those managers can see it. That keeps the public map trustworthy without making
 * residents jump through a bunch of hoops just to report something.
 *
 * [PENDING] is [IssueStatus.IN_REVIEW] and [REJECTED] is [IssueStatus.REJECTED].
 * The review still lives here instead of in the status, because a status has no
 * way to say who decided or why.
 */
@Serializable
enum class ReviewState(val storageKey: String) {
    /** Filed, waiting for a manager. Visible to the submitter and its managers. */
    PENDING("pending_review"),

    /** A manager accepted it. This is the only state the public sees. */
    APPROVED("approved"),

    /** A manager turned it down. Visible to the submitter, with the reason. */
    REJECTED("rejected"),
    ;

    companion object {
        fun fromStorageKey(key: String): ReviewState =
            entries.firstOrNull { it.storageKey == key } ?: PENDING
    }
}

/**
 * The triage decision on one issue, and who made it.
 *
 * [rejectionReason] is really the point of this whole record. A manager can't
 * reject a report without saying why, because the submitter gets shown the reason,
 * and a plain "no" teaches a resident nothing except not to bother next time. The
 * constructor enforces that instead of trusting whoever calls it (see [rejected]).
 */
@Serializable
data class IssueReview(
    val state: ReviewState = ReviewState.PENDING,
    val reviewedByUserId: String? = null,
    val reviewedByDisplayName: String? = null,
    val reviewedAtMillis: Long? = null,
    /** Non-null exactly when [state] is [ReviewState.REJECTED]. */
    val rejectionReason: String? = null,
) {
    init {
        require(state != ReviewState.REJECTED || !rejectionReason.isNullOrBlank()) {
            "A rejected issue must carry a reason."
        }
    }

    /** Only approved issues are public. Everything else is limited to a few people. */
    val isPublic: Boolean get() = state == ReviewState.APPROVED

    val isPending: Boolean get() = state == ReviewState.PENDING

    companion object {
        /** What every newly submitted issue starts as. */
        val Pending: IssueReview = IssueReview()

        fun approved(
            reviewerId: String?,
            reviewerName: String?,
            atMillis: Long,
        ): IssueReview = IssueReview(
            state = ReviewState.APPROVED,
            reviewedByUserId = reviewerId,
            reviewedByDisplayName = reviewerName,
            reviewedAtMillis = atMillis,
        )

        /** @throws IllegalArgumentException if [reason] is blank. */
        fun rejected(
            reviewerId: String?,
            reviewerName: String?,
            atMillis: Long,
            reason: String,
        ): IssueReview = IssueReview(
            state = ReviewState.REJECTED,
            reviewedByUserId = reviewerId,
            reviewedByDisplayName = reviewerName,
            reviewedAtMillis = atMillis,
            rejectionReason = reason.trim(),
        )
    }
}
