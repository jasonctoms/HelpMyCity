package dev.helpmycity.domain.repository

import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueDraft
import dev.helpmycity.domain.model.IssueEditDraft
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import kotlinx.coroutines.flow.Flow

/**
 * Reads always come from the local database, so the UI renders right away and
 * works without a network. Writes land locally first, and the sync engine queues
 * them up for the backend.
 *
 * Every read is already filtered down to what the signed-in user is allowed to
 * see (see [dev.helpmycity.domain.access.canSee]), so no screen has to remember
 * that an issue waiting on triage is not public yet.
 */
interface IssueRepository {
    /**
     * Leaves out [IssueStatus.REJECTED] issues unless [filter] asks for that
     * status by name, so they stay off the list, board, and map.
     */
    fun observeIssues(filter: IssueFilter = IssueFilter.None): Flow<List<Issue>>

    fun observeIssue(id: String): Flow<Issue?>

    /**
     * Issues waiting on triage that the signed-in user is responsible for, which
     * is what the review queue shows. Empty for anyone who is not a manager.
     */
    fun observeReviewQueue(): Flow<List<Issue>>

    fun observeHistory(issueId: String): Flow<List<IssueStatusChange>>

    /** Photos for one issue. They are read separately so listing issues never loads image bytes. */
    fun observePhotos(issueId: String): Flow<List<IssuePhoto>>

    suspend fun getIssue(id: String): Issue?

    /**
     * Attaches an image to an issue and queues it for upload like any other write.
     *
     * [bytes] is the encoded image exactly as the platform picker handed it over.
     * [IssuePhoto.bytes] explains why the bytes get stored instead of a file URI.
     */
    suspend fun addPhoto(issueId: String, bytes: ByteArray, caption: String? = null): String

    suspend fun removePhoto(photoId: String)

    /** Returns the id of the newly created issue. */
    suspend fun submitIssue(draft: IssueDraft): String

    suspend fun updateIssue(issue: Issue)

    /**
     * Fixes up an issue's own fields. This is the "edit for completeness" step a
     * manager does before approving a report.
     *
     * Only whoever manages the issue can do this, and that gets checked here and
     * not just in the screen. Every accepted edit stamps
     * [dev.helpmycity.domain.model.IssueEdit] on the issue, so anyone who can see
     * the issue (submitter included) can see that it changed.
     */
    suspend fun editIssue(issueId: String, draft: IssueEditDraft): EditOutcome

    /**
     * Moves an issue to [newStatus] and adds an audit entry in the same step, so
     * the history can never drift away from the current status.
     *
     * Only an approved issue has a status to change, and it can only move to one
     * of [IssueStatus.workflow]; review and rejection are triage's job.
     * [IssueStatus.COMPLETE] needs a [resolution], which everyone can see, and it
     * doubles as the history note when there isn't one. Moving out of Complete
     * clears the resolution.
     */
    suspend fun changeStatus(
        issueId: String,
        newStatus: IssueStatus,
        note: String? = null,
        resolution: String? = null,
        changedByUserId: String? = null,
        changedByDisplayName: String? = null,
    ): StatusOutcome

    /**
     * Publishes an issue. The manager reviewing it accepts the report, and then
     * everyone can see it. An issue that is in review or rejected moves to
     * [IssueStatus.OPEN].
     */
    suspend fun approveIssue(issueId: String, note: String? = null): ReviewOutcome

    /**
     * Turns an issue down. [reason] is required, and the submitter gets to read
     * it; this app will not record a rejection without an explanation. The issue
     * moves to [IssueStatus.REJECTED], loses any resolution, and stays visible only
     * to its submitter and its managers.
     */
    suspend fun rejectIssue(issueId: String, reason: String): ReviewOutcome

    /** Issues the signed-in user has starred; empty when nobody is signed in. */
    fun observeSupportedIssueIds(): Flow<Set<String>>

    /**
     * "Me too" from a resident who found an existing report instead of filing a
     * duplicate. It only counts once per user per issue, so this returns false if
     * the user already starred it or nobody is signed in.
     */
    suspend fun addSupport(issueId: String): Boolean

    suspend fun deleteIssue(id: String)
}

/**
 * Why a review action did or did not land.
 *
 * This is a result instead of an exception because two of the three failures are
 * just ordinary UI states (an empty reason box, or a queue that went stale while
 * the screen was open), and the caller has something to say about each one.
 */
sealed interface ReviewOutcome {
    data object Recorded : ReviewOutcome

    /** The issue is gone, or was never visible to this user. */
    data object IssueNotFound : ReviewOutcome

    /** Signed out, a resident, or a manager for some other part of the city. */
    data object NotPermitted : ReviewOutcome

    /** A rejection arrived with a blank reason. */
    data object ReasonRequired : ReviewOutcome
}

/** Why a status change did or did not land. */
sealed interface StatusOutcome {
    data object Recorded : StatusOutcome

    /** Already in that status, with the same resolution and no note to add. */
    data object NoChanges : StatusOutcome

    data object IssueNotFound : StatusOutcome

    /** The issue has not been approved, or the target is not in [IssueStatus.workflow]. */
    data object NotAllowed : StatusOutcome

    /** [IssueStatus.COMPLETE] with a blank resolution. */
    data object ResolutionRequired : StatusOutcome
}

/**
 * Why an edit did or did not land.
 *
 * [NoChanges] is not an error. A manager who opens the form, reads it, and saves
 * without touching anything should not leave an "edited by" mark on a report
 * they never changed.
 */
sealed interface EditOutcome {
    data object Recorded : EditOutcome

    /** Saved nothing, because nothing was different. */
    data object NoChanges : EditOutcome

    /** The issue is gone, or was never visible to this user. */
    data object IssueNotFound : EditOutcome

    /** Signed out, a resident, or a manager for some other part of the city. */
    data object NotPermitted : EditOutcome
}
