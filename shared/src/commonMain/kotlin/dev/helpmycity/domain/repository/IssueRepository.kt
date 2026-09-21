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
 * Reads always come from the local database, so the UI renders instantly and
 * works with no network. Writes land locally first and are queued for the
 * backend by the sync engine.
 *
 * Every read is already filtered to what the signed-in user is allowed to see
 * (see [dev.helpmycity.domain.access.canSee]), so no screen has to
 * remember that an issue awaiting triage is not public yet.
 */
interface IssueRepository {
    /**
     * Leaves out [IssueStatus.REJECTED] issues unless [filter] asks for that
     * status by name, so they stay off the list, board and map.
     */
    fun observeIssues(filter: IssueFilter = IssueFilter.None): Flow<List<Issue>>

    fun observeIssue(id: String): Flow<Issue?>

    /**
     * Issues waiting on triage that the signed-in user is responsible for --
     * what the review queue shows. Empty for anyone who is not a manager.
     */
    fun observeReviewQueue(): Flow<List<Issue>>

    fun observeHistory(issueId: String): Flow<List<IssueStatusChange>>

    /** Photos for one issue, read separately so listing issues never loads image bytes. */
    fun observePhotos(issueId: String): Flow<List<IssuePhoto>>

    suspend fun getIssue(id: String): Issue?

    /**
     * Attaches an image to an issue, queued for upload like every other write.
     *
     * [bytes] is the encoded image as the platform picker handed it over -- see
     * [IssuePhoto.bytes] for why the bytes are stored rather than a file URI.
     */
    suspend fun addPhoto(issueId: String, bytes: ByteArray, caption: String? = null): String

    suspend fun removePhoto(photoId: String)

    /** Returns the id of the newly created issue. */
    suspend fun submitIssue(draft: IssueDraft): String

    suspend fun updateIssue(issue: Issue)

    /**
     * Corrects an issue's own fields -- the "edit for completeness" step a
     * manager takes before approving a report.
     *
     * Restricted to whoever manages the issue, checked here rather than only in
     * the screen, and every accepted edit stamps
     * [dev.helpmycity.domain.model.IssueEdit] on the issue so the
     * change is visible to everyone who can see it, submitter included.
     */
    suspend fun editIssue(issueId: String, draft: IssueEditDraft): EditOutcome

    /**
     * Moves an issue to [newStatus] and appends an audit entry in one step, so
     * the history can never drift from the current status.
     *
     * Only an approved issue has a status to change, and only to one of
     * [IssueStatus.workflow]: review and rejection are triage's job. [IssueStatus.COMPLETE]
     * needs a [resolution], which is shown to everyone and recorded as the
     * history note when there is no other; leaving it clears the resolution.
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
     * Publishes an issue: the manager reviewing it accepts the report, and it
     * becomes visible to everyone. An issue in review or rejected moves to
     * [IssueStatus.OPEN].
     */
    suspend fun approveIssue(issueId: String, note: String? = null): ReviewOutcome

    /**
     * Turns an issue down. [reason] is required and is shown to the submitter:
     * a rejection with no explanation is not a rejection this app will record.
     * The issue moves to [IssueStatus.REJECTED], losing any resolution, and
     * stays visible only to its submitter and its managers.
     */
    suspend fun rejectIssue(issueId: String, reason: String): ReviewOutcome

    /** Issues the signed-in user has starred; empty when nobody is signed in. */
    fun observeSupportedIssueIds(): Flow<Set<String>>

    /**
     * "Me too" from a resident who found an existing report instead of filing a
     * duplicate. Once per user per issue: false when this user already starred
     * it, or nobody is signed in.
     */
    suspend fun addSupport(issueId: String): Boolean

    suspend fun deleteIssue(id: String)
}

/**
 * Why a review action did or did not land.
 *
 * A result rather than an exception because two of the three failures are
 * ordinary UI states -- an empty reason box, or a queue gone stale while the
 * screen was open -- and the caller has something to say about each.
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
 * [NoChanges] is not an error: a manager who opens the form, reads it and saves
 * without touching anything should not leave an "edited by" mark on a report
 * they did not change.
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
