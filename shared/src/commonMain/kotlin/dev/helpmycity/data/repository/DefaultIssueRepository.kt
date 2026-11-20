package dev.helpmycity.data.repository

import dev.helpmycity.data.session.UserSession
import dev.helpmycity.data.local.IssueLocalDataSource
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.domain.access.canSee
import dev.helpmycity.domain.access.manages
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.EditableField
import dev.helpmycity.domain.model.IssueDraft
import dev.helpmycity.domain.model.IssueEdit
import dev.helpmycity.domain.model.IssueEditDraft
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.IssueLocation
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueReview
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.model.IssueSupport
import dev.helpmycity.domain.model.SyncMetadata
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.repository.EditOutcome
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.ReviewOutcome
import dev.helpmycity.domain.repository.StatusOutcome
import dev.helpmycity.domain.util.IdGenerator
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Offline-first. Every read comes from the local store, and every write lands
 * there first, marked [SyncState.PENDING_UPLOAD] for the sync engine to drain.
 * Nothing here ever waits on the network, so the app behaves the same in a dead
 * zone as it does on wifi.
 *
 * This is also where the visibility rules in [dev.helpmycity.domain.access] get
 * applied. Reads are filtered against the signed-in user, so an issue that is
 * still in triage can't reach a screen that forgot to check, and reads re-run when
 * the user changes. Signing in as a manager fills in the list without a reload.
 */
@Single(binds = [IssueRepository::class])
class DefaultIssueRepository(
    private val local: IssueLocalDataSource,
    private val neighborhoods: NeighborhoodLocalDataSource,
    private val session: UserSession,
    private val idGenerator: IdGenerator,
    private val time: TimeProvider,
) : IssueRepository {

    private val viewer: Flow<User?> get() = session.currentUser

    override fun observeIssues(filter: IssueFilter): Flow<List<Issue>> =
        combine(local.observeAll(), viewer) { issues, user ->
            issues.filter {
                user.canSee(it) &&
                    (it.status != IssueStatus.REJECTED || IssueStatus.REJECTED in filter.statuses) &&
                    (filter.isEmpty || filter.matches(it))
            }
        }

    override fun observeIssue(id: String): Flow<Issue?> =
        combine(local.observeById(id), viewer) { issue, user ->
            issue?.takeIf { user.canSee(it) }
        }

    override fun observeReviewQueue(): Flow<List<Issue>> =
        combine(local.observeAll(), viewer) { issues, user ->
            issues.filter { it.review.isPending && user.manages(it) }
                .sortedBy(Issue::createdAtMillis)
        }

    override fun observeHistory(issueId: String): Flow<List<IssueStatusChange>> =
        local.observeHistory(issueId)

    override fun observePhotos(issueId: String): Flow<List<IssuePhoto>> =
        local.observePhotos(issueId)

    override suspend fun addPhoto(issueId: String, bytes: ByteArray, caption: String?): String {
        val photo = IssuePhoto(
            id = idGenerator.newId(),
            issueId = issueId,
            bytes = bytes,
            caption = caption,
            createdAtMillis = time.nowMillis(),
            sync = SyncMetadata(state = SyncState.PENDING_UPLOAD),
        )
        local.upsertPhoto(photo)
        return photo.id
    }

    override suspend fun removePhoto(photoId: String) = local.deletePhoto(photoId)

    /** Unfiltered on purpose, because the write paths above the repository already have the issue. */
    override suspend fun getIssue(id: String): Issue? = local.getById(id)

    override suspend fun submitIssue(draft: IssueDraft): String {
        val now = time.nowMillis()
        val submitter = session.currentUser.value
        val issue = Issue(
            id = idGenerator.newId(),
            title = draft.title.trim(),
            description = draft.description.trim(),
            requestedAction = draft.requestedAction.trim(),
            category = draft.category,
            status = IssueStatus.IN_REVIEW,
            priority = draft.priority,
            location = IssueLocation(
                description = draft.locationDescription.trim(),
                point = draft.point,
                geocodedAddress = draft.geocodedAddress,
                neighborhood = draft.neighborhood,
                // This is worked out here instead of on the form, so every path
                // that creates an issue still routes to the right district manager.
                councilDistrict = draft.neighborhood?.let { neighborhoods.getById(it)?.councilDistrict },
            ),
            departmentId = draft.departmentId,
            reporter = draft.reporter?.takeUnless { it.isEmpty },
            submittedByUserId = submitter?.id,
            review = IssueReview.Pending,
            notesSource = draft.notesSource.trim(),
            externalReference = null,
            supportCount = 0,
            createdAtMillis = now,
            updatedAtMillis = now,
            sync = SyncMetadata(state = SyncState.PENDING_UPLOAD),
        )
        local.upsert(issue)
        // Seed the audit log so history starts when it was submitted, not at the first triage.
        local.appendStatusChange(
            IssueStatusChange(
                id = idGenerator.newId(),
                issueId = issue.id,
                fromStatus = null,
                toStatus = IssueStatus.IN_REVIEW,
                note = null,
                changedByUserId = submitter?.id,
                changedByDisplayName = draft.reporter?.name ?: submitter?.displayName,
                changedAtMillis = now,
            )
        )
        return issue.id
    }

    override suspend fun updateIssue(issue: Issue) {
        local.upsert(issue.touched(time.nowMillis()))
    }

    /**
     * This does two things beyond a plain upsert. The permission check happens
     * here instead of in the screen, and an accepted edit records itself on the
     * issue so everyone who can see the issue can see the change. An edit that
     * changes nothing does not leave a mark.
     */
    override suspend fun editIssue(issueId: String, draft: IssueEditDraft): EditOutcome {
        val existing = local.getById(issueId) ?: return EditOutcome.IssueNotFound
        val editor = session.currentUser.value ?: return EditOutcome.NotPermitted
        if (!editor.manages(existing)) return EditOutcome.NotPermitted

        val edited = existing.applying(draft)
        val changed = existing.differenceFrom(edited)
        if (changed.isEmpty()) return EditOutcome.NoChanges

        val now = time.nowMillis()
        local.upsert(
            edited.copy(
                lastEdit = IssueEdit(
                    editedByUserId = editor.id,
                    editedByDisplayName = editor.displayName,
                    editedAtMillis = now,
                    revision = (existing.lastEdit?.revision ?: 0) + 1,
                    fields = changed,
                ),
            ).touched(now)
        )
        return EditOutcome.Recorded
    }

    /** The draft applied over the issue, working out the district again if the neighborhood changed. */
    private suspend fun Issue.applying(draft: IssueEditDraft): Issue = copy(
        title = draft.title.trim(),
        description = draft.description.trim(),
        requestedAction = draft.requestedAction.trim(),
        category = draft.category,
        priority = draft.priority,
        location = location.copy(
            description = draft.locationDescription.trim(),
            point = draft.point,
            geocodedAddress = draft.geocodedAddress,
            neighborhood = draft.neighborhood,
            councilDistrict = if (draft.neighborhood == location.neighborhood) {
                location.councilDistrict
            } else {
                draft.neighborhood?.let { neighborhoods.getById(it)?.councilDistrict }
            },
        ),
        departmentId = draft.departmentId,
        notesSource = draft.notesSource.trim(),
    )

    /** What is different. This gets recorded on the issue and named in the UI. */
    private fun Issue.differenceFrom(other: Issue): Set<EditableField> = buildSet {
        if (title != other.title) add(EditableField.TITLE)
        if (description != other.description) add(EditableField.DESCRIPTION)
        if (requestedAction != other.requestedAction) add(EditableField.REQUESTED_ACTION)
        if (category != other.category) add(EditableField.CATEGORY)
        if (priority != other.priority) add(EditableField.PRIORITY)
        // To a reader this is one field, since the text and the pin are both "where it is".
        if (location.description != other.location.description ||
            location.point != other.location.point
        ) {
            add(EditableField.LOCATION)
        }
        if (location.neighborhood != other.location.neighborhood) add(EditableField.NEIGHBORHOOD)
        if (departmentId != other.departmentId) add(EditableField.DEPARTMENT)
        if (notesSource != other.notesSource) add(EditableField.NOTES_SOURCE)
    }

    override suspend fun changeStatus(
        issueId: String,
        newStatus: IssueStatus,
        note: String?,
        resolution: String?,
        changedByUserId: String?,
        changedByDisplayName: String?,
    ): StatusOutcome {
        val existing = local.getById(issueId) ?: return StatusOutcome.IssueNotFound
        if (!existing.review.isPublic || newStatus !in IssueStatus.workflow) {
            return StatusOutcome.NotAllowed
        }
        val newResolution = if (newStatus == IssueStatus.COMPLETE) {
            resolution?.trim()?.takeUnless { it.isEmpty() } ?: return StatusOutcome.ResolutionRequired
        } else {
            null
        }
        val newNote = note?.trim()?.takeUnless { it.isEmpty() }
        if (existing.status == newStatus && existing.resolution == newResolution && newNote == null) {
            return StatusOutcome.NoChanges
        }

        val now = time.nowMillis()
        local.upsert(existing.copy(status = newStatus, resolution = newResolution).touched(now))
        local.appendStatusChange(
            IssueStatusChange(
                id = idGenerator.newId(),
                issueId = issueId,
                fromStatus = existing.status,
                toStatus = newStatus,
                note = newNote ?: newResolution,
                changedByUserId = changedByUserId,
                changedByDisplayName = changedByDisplayName,
                changedAtMillis = now,
            )
        )
        return StatusOutcome.Recorded
    }

    override suspend fun approveIssue(issueId: String, note: String?): ReviewOutcome =
        review(issueId) { issue, reviewer, now ->
            val approved = issue.copy(
                review = IssueReview.approved(reviewer.id, reviewer.displayName, now),
                status = if (issue.status in IssueStatus.workflow) issue.status else IssueStatus.OPEN,
            )
            local.upsert(approved.touched(now))
            if (approved.status != issue.status) {
                local.appendStatusChange(
                    IssueStatusChange(
                        id = idGenerator.newId(),
                        issueId = issueId,
                        fromStatus = issue.status,
                        toStatus = approved.status,
                        note = note?.takeUnless { it.isBlank() },
                        changedByUserId = reviewer.id,
                        changedByDisplayName = reviewer.displayName,
                        changedAtMillis = now,
                    )
                )
            }
            ReviewOutcome.Recorded
        }

    override suspend fun rejectIssue(issueId: String, reason: String): ReviewOutcome {
        if (reason.isBlank()) return ReviewOutcome.ReasonRequired
        return review(issueId) { issue, reviewer, now ->
            local.upsert(
                issue.copy(
                    review = IssueReview.rejected(
                        reviewerId = reviewer.id,
                        reviewerName = reviewer.displayName,
                        atMillis = now,
                        reason = reason,
                    ),
                    status = IssueStatus.REJECTED,
                    resolution = null,
                ).touched(now)
            )
            if (issue.status != IssueStatus.REJECTED) {
                local.appendStatusChange(
                    IssueStatusChange(
                        id = idGenerator.newId(),
                        issueId = issueId,
                        fromStatus = issue.status,
                        toStatus = IssueStatus.REJECTED,
                        note = reason.trim(),
                        changedByUserId = reviewer.id,
                        changedByDisplayName = reviewer.displayName,
                        changedAtMillis = now,
                    )
                )
            }
            ReviewOutcome.Recorded
        }
    }

    /**
     * The check both review actions share. The issue has to exist, and the
     * signed-in user has to be responsible for it. This is checked here and not
     * just in the UI, because "the button was hidden" is not a rule.
     */
    private suspend fun review(
        issueId: String,
        action: suspend (issue: Issue, reviewer: User, nowMillis: Long) -> ReviewOutcome,
    ): ReviewOutcome {
        val issue = local.getById(issueId) ?: return ReviewOutcome.IssueNotFound
        val reviewer = session.currentUser.value ?: return ReviewOutcome.NotPermitted
        if (!reviewer.manages(issue)) return ReviewOutcome.NotPermitted
        return action(issue, reviewer, time.nowMillis())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSupportedIssueIds(): Flow<Set<String>> =
        viewer.flatMapLatest { user ->
            if (user == null) flowOf(emptySet()) else local.observeSupportedIssueIds(user.id)
        }

    /**
     * A star is its own row instead of an edit to the issue, so starring never
     * pushes the issue itself (which a resident is not allowed to change).
     */
    override suspend fun addSupport(issueId: String): Boolean {
        val user = session.currentUser.value ?: return false
        if (local.getById(issueId) == null) return false
        return local.addSupport(
            IssueSupport(issueId = issueId, userId = user.id, createdAtMillis = time.nowMillis())
        )
    }

    override suspend fun deleteIssue(id: String) {
        val existing = local.getById(id) ?: return
        if (existing.sync.state == SyncState.PENDING_UPLOAD) {
            // This never made it to the backend, so there is nothing to tell it.
            local.delete(id)
        } else {
            // Tombstone. The sync engine has to send the delete up before the row can go away.
            local.markSyncState(id, SyncState.PENDING_DELETE)
        }
    }

    /** Any local edit goes back into the outbox, no matter what the previous sync state was. */
    private fun Issue.touched(nowMillis: Long): Issue = copy(
        updatedAtMillis = nowMillis,
        sync = sync.copy(state = SyncState.PENDING_UPLOAD),
    )
}
