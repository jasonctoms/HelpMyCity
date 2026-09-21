package dev.helpmycity.data.repository

import dev.helpmycity.data.session.FakeUserSession
import dev.helpmycity.data.local.InMemoryIssueLocalDataSource
import dev.helpmycity.data.local.InMemoryNeighborhoodLocalDataSource
import dev.helpmycity.domain.model.EditableField
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssueDraft
import dev.helpmycity.domain.model.IssueEditDraft
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.ReviewState
import dev.helpmycity.domain.model.SyncState
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.EditOutcome
import dev.helpmycity.domain.repository.ReviewOutcome
import dev.helpmycity.domain.repository.StatusOutcome
import dev.helpmycity.domain.util.IdGenerator
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultIssueRepositoryTest {

    private val ids = object : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }
    private var now = 1_000L
    private val time = TimeProvider { now }

    private val resident = User("resident-1", "Ana", null, UserRole.RESIDENT)
    private val otherResident = User("resident-2", "Bo", null, UserRole.RESIDENT)
    private val libbyLakeManager = User(
        id = "manager-1",
        displayName = "Manager One",
        email = null,
        role = UserRole.MANAGER,
        scope = ManagerScope(neighborhoodIds = setOf("nbhd-libby-lake")),
    )
    private val districtTwoManager = User(
        id = "manager-2",
        displayName = "Manager Two",
        email = null,
        role = UserRole.MANAGER,
        scope = ManagerScope(districts = setOf("District 2")),
    )
    private val admin = User("admin-1", "Admin", null, UserRole.ADMIN)

    private val neighborhoods = InMemoryNeighborhoodLocalDataSource()
    private val session = FakeUserSession()

    private fun repository(local: InMemoryIssueLocalDataSource = InMemoryIssueLocalDataSource()) =
        DefaultIssueRepository(local, neighborhoods, session, ids, time) to local

    private val draft = IssueDraft(
        title = "  Street light out  ",
        description = "Dark for three weeks",
        locationDescription = "N. River Rd",
    )

    private val libbyLakeDraft = draft.copy(neighborhood = "nbhd-libby-lake")

    private suspend fun seedNeighborhoods() {
        neighborhoods.upsertAll(
            listOf(
                Neighborhood(id = "nbhd-libby-lake", name = "Libby Lake", councilDistrict = "District 1"),
                Neighborhood(id = "nbhd-eastside", name = "Eastside", councilDistrict = "District 2"),
            )
        )
    }

    @Test
    fun submittedIssueStartsPendingUploadAndTrimsInput() = runTest {
        val (repository, _) = repository()

        val id = repository.submitIssue(draft)
        val issue = assertNotNull(repository.getIssue(id))

        assertEquals("Street light out", issue.title)
        assertEquals(IssueStatus.IN_REVIEW, issue.status)
        assertEquals(SyncState.PENDING_UPLOAD, issue.sync.state)
        assertEquals(now, issue.createdAtMillis)
    }

    @Test
    fun submissionSeedsHistorySoTheAuditLogStartsAtSubmission() = runTest {
        val (repository, _) = repository()

        val id = repository.submitIssue(draft)
        val history = repository.observeHistory(id).first()

        assertEquals(1, history.size)
        assertNull(history.single().fromStatus)
        assertEquals(IssueStatus.IN_REVIEW, history.single().toStatus)
    }

    private suspend fun approvedIssue(repository: DefaultIssueRepository): String {
        seedNeighborhoods()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)
        session.signedInAs(libbyLakeManager)
        repository.approveIssue(id)
        return id
    }

    @Test
    fun changingStatusUpdatesTheIssueAndAppendsOneHistoryEntry() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)

        now = 2_000L
        assertEquals(
            StatusOutcome.Recorded,
            repository.changeStatus(id, IssueStatus.IN_PROGRESS, note = "Assigned to Public Works"),
        )

        val issue = assertNotNull(repository.getIssue(id))
        assertEquals(IssueStatus.IN_PROGRESS, issue.status)
        assertEquals(2_000L, issue.updatedAtMillis)

        val history = repository.observeHistory(id).first()
        assertEquals(3, history.size)
        val latest = history.first()
        assertEquals(IssueStatus.OPEN, latest.fromStatus)
        assertEquals(IssueStatus.IN_PROGRESS, latest.toStatus)
        assertEquals("Assigned to Public Works", latest.note)
    }

    @Test
    fun changingToTheSameStatusWithoutANoteIsANoOp() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)

        assertEquals(StatusOutcome.NoChanges, repository.changeStatus(id, IssueStatus.OPEN))

        assertEquals(2, repository.observeHistory(id).first().size)
    }

    @Test
    fun anIssueInReviewHasNoStatusToChange() = runTest {
        val (repository, _) = repository()
        val id = repository.submitIssue(draft)

        assertEquals(StatusOutcome.NotAllowed, repository.changeStatus(id, IssueStatus.OPEN))
        assertEquals(IssueStatus.IN_REVIEW, assertNotNull(repository.getIssue(id)).status)
    }

    @Test
    fun anApprovedIssueCannotGoBackToReview() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)

        assertEquals(StatusOutcome.NotAllowed, repository.changeStatus(id, IssueStatus.IN_REVIEW))
        assertEquals(StatusOutcome.NotAllowed, repository.changeStatus(id, IssueStatus.REJECTED))
    }

    @Test
    fun completingRequiresAResolution() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)

        assertEquals(
            StatusOutcome.ResolutionRequired,
            repository.changeStatus(id, IssueStatus.COMPLETE, resolution = "  "),
        )
        assertEquals(IssueStatus.OPEN, assertNotNull(repository.getIssue(id)).status)
    }

    @Test
    fun completingRecordsTheResolutionAndReopeningClearsIt() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)

        now = 2_000L
        repository.changeStatus(id, IssueStatus.COMPLETE, resolution = " Replaced the bulbs ")
        val completed = assertNotNull(repository.getIssue(id))
        assertEquals(IssueStatus.COMPLETE, completed.status)
        assertEquals("Replaced the bulbs", completed.resolution)
        assertEquals("Replaced the bulbs", repository.observeHistory(id).first().first().note)

        repository.changeStatus(id, IssueStatus.IN_PROGRESS)
        assertNull(assertNotNull(repository.getIssue(id)).resolution)
    }

    @Test
    fun rejectingAnApprovedIssueMarksItRejected() = runTest {
        val (repository, _) = repository()
        val id = approvedIssue(repository)
        repository.changeStatus(id, IssueStatus.COMPLETE, resolution = "Fixed")

        now = 2_000L
        repository.rejectIssue(id, "Duplicate of another report")

        val issue = assertNotNull(repository.getIssue(id))
        assertEquals(IssueStatus.REJECTED, issue.status)
        assertNull(issue.resolution)
        assertEquals(IssueStatus.REJECTED, repository.observeHistory(id).first().first().toStatus)
    }

    @Test
    fun deletingAnUnsyncedIssueRemovesItOutright() = runTest {
        val (repository, local) = repository()
        val id = repository.submitIssue(draft)

        repository.deleteIssue(id)

        assertNull(local.getById(id))
    }

    @Test
    fun deletingASyncedIssueLeavesATombstoneForTheBackend() = runTest {
        val (repository, local) = repository()
        val id = repository.submitIssue(draft)
        local.markSynced(id, syncedAtMillis = 1_500L, remoteVersion = "v1")

        repository.deleteIssue(id)

        val issue = assertNotNull(local.getById(id))
        assertEquals(SyncState.PENDING_DELETE, issue.sync.state)
    }

    @Test
    fun editingASyncedIssuePutsItBackInTheOutbox() = runTest {
        val (repository, local) = repository()
        val id = repository.submitIssue(draft)
        local.markSynced(id, syncedAtMillis = 1_500L, remoteVersion = "v1")

        repository.updateIssue(assertNotNull(local.getById(id)).copy(title = "Street light still out"))

        val issue = assertNotNull(local.getById(id))
        assertEquals(SyncState.PENDING_UPLOAD, issue.sync.state)
        assertTrue(local.pendingSync().any { it.id == id })
    }

    /**
     * A resident may not change someone else's issue, so a star must travel as
     * its own row -- pushing the issue would be refused by the backend.
     */
    @Test
    fun aStarCountsOncePerUserAndLeavesTheIssueOutOfTheOutbox() = runTest {
        val (repository, local) = repository()
        val id = repository.submitIssue(draft)
        local.markSynced(id, syncedAtMillis = 1_500L, remoteVersion = "v1")
        session.signedInAs(resident)

        assertTrue(repository.addSupport(id))
        assertFalse(repository.addSupport(id))
        session.signedInAs(otherResident)
        assertTrue(repository.addSupport(id))

        val issue = assertNotNull(local.getById(id))
        assertEquals(2, issue.supportCount)
        assertEquals(SyncState.SYNCED, issue.sync.state)
        assertEquals(listOf(resident.id, otherResident.id), local.pendingSupports().map { it.userId })
        assertEquals(setOf(id), repository.observeSupportedIssueIds().first())
    }

    @Test
    fun nobodySignedInCannotStar() = runTest {
        val (repository, local) = repository()
        val id = repository.submitIssue(draft)

        assertFalse(repository.addSupport(id))
        assertEquals(0, assertNotNull(local.getById(id)).supportCount)
    }
    @Test
    fun photosAreStoredAgainstTheirIssueAndQueuedForUpload() = runTest {
        val (repository, _) = repository()
        val issueId = repository.submitIssue(draft)
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val photoId = repository.addPhoto(issueId, bytes, caption = "The pole")

        val photos = repository.observePhotos(issueId).first()
        assertEquals(1, photos.size)
        val photo = photos.single()
        assertEquals(photoId, photo.id)
        assertEquals(issueId, photo.issueId)
        assertEquals("The pole", photo.caption)
        // The bytes are the upload payload, so they have to survive the round trip.
        assertTrue(bytes.contentEquals(photo.bytes))
        assertNull(photo.remoteUrl)
        assertEquals(SyncState.PENDING_UPLOAD, photo.sync.state)
    }

    /** Photos left the Issue aggregate so that listing issues never loads image bytes. */
    @Test
    fun photosAreNotReturnedWithTheIssueItself() = runTest {
        val (repository, _) = repository()
        val issueId = repository.submitIssue(draft)
        repository.addPhoto(issueId, byteArrayOf(1, 2, 3))

        val otherIssueId = repository.submitIssue(draft)

        assertEquals(1, repository.observePhotos(issueId).first().size)
        assertTrue(repository.observePhotos(otherIssueId).first().isEmpty())
    }

    @Test
    fun removingAPhotoLeavesTheIssueAlone() = runTest {
        val (repository, _) = repository()
        val issueId = repository.submitIssue(draft)
        val photoId = repository.addPhoto(issueId, byteArrayOf(1, 2, 3))

        repository.removePhoto(photoId)

        assertTrue(repository.observePhotos(issueId).first().isEmpty())
        assertNotNull(repository.getIssue(issueId))
    }

    // --- Triage -------------------------------------------------------------

    @Test
    fun aNewIssueIsRecordedAgainstItsSubmitterAndAwaitsReview() = runTest {
        val (repository, _) = repository()
        session.signedInAs(resident)

        val id = repository.submitIssue(draft)

        val issue = assertNotNull(repository.getIssue(id))
        assertEquals(resident.id, issue.submittedByUserId)
        assertEquals(ReviewState.PENDING, issue.review.state)
    }

    /** District managers can only be routed to if the district is stamped on submission. */
    @Test
    fun submittingResolvesTheCouncilDistrictFromTheNeighborhood() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()

        val id = repository.submitIssue(draft.copy(neighborhood = "nbhd-eastside"))

        assertEquals("District 2", repository.getIssue(id)?.location?.councilDistrict)
    }

    @Test
    fun anIssueAwaitingReviewIsHiddenFromEveryoneButItsSubmitterAndItsManagers() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        // The person who filed it keeps sight of it.
        assertEquals(1, repository.observeIssues().first().size)

        session.signedInAs(otherResident)
        assertTrue(repository.observeIssues().first().isEmpty())
        assertNull(repository.observeIssue(id).first())

        session.signedInAs(districtTwoManager)
        assertTrue(repository.observeIssues().first().isEmpty())

        session.signedInAs(libbyLakeManager)
        assertEquals(1, repository.observeIssues().first().size)

        session.signedInAs(admin)
        assertEquals(1, repository.observeIssues().first().size)
    }

    @Test
    fun approvingPublishesTheIssueAndOpensIt() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        now = 2_000L
        assertEquals(ReviewOutcome.Recorded, repository.approveIssue(id))

        val issue = assertNotNull(repository.getIssue(id))
        assertEquals(ReviewState.APPROVED, issue.review.state)
        assertEquals(libbyLakeManager.displayName, issue.review.reviewedByDisplayName)
        assertEquals(2_000L, issue.review.reviewedAtMillis)
        // Triaging a report is what opens it, and that is a real status change.
        assertEquals(IssueStatus.OPEN, issue.status)
        assertEquals(IssueStatus.OPEN, repository.observeHistory(id).first().first().toStatus)

        session.signedInAs(otherResident)
        assertEquals(1, repository.observeIssues().first().size)
    }

    @Test
    fun rejectingRecordsTheReasonAndLeavesItVisibleToTheSubmitterOnly() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        assertEquals(
            ReviewOutcome.Recorded,
            repository.rejectIssue(id, "  Not a city street -- this is HOA property.  "),
        )

        val issue = assertNotNull(repository.getIssue(id))
        assertEquals(ReviewState.REJECTED, issue.review.state)
        assertEquals("Not a city street -- this is HOA property.", issue.review.rejectionReason)
        assertEquals(libbyLakeManager.displayName, issue.review.reviewedByDisplayName)
        assertEquals(IssueStatus.REJECTED, issue.status)

        val rejected = IssueFilter(statuses = setOf(IssueStatus.REJECTED))
        assertTrue(repository.observeIssues().first().isEmpty())
        assertEquals(1, repository.observeIssues(rejected).first().size)

        session.signedInAs(resident)
        assertNotNull(repository.observeIssue(id).first())
        assertEquals(1, repository.observeIssues(rejected).first().size)
        session.signedInAs(otherResident)
        assertNull(repository.observeIssue(id).first())
        assertTrue(repository.observeIssues(rejected).first().isEmpty())
    }

    @Test
    fun approvingARejectedIssueOpensIt() = runTest {
        val (repository, _) = repository()
        seedNeighborhoods()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)
        session.signedInAs(libbyLakeManager)
        repository.rejectIssue(id, "Needs a location")

        now = 2_000L
        repository.approveIssue(id)

        assertEquals(IssueStatus.OPEN, assertNotNull(repository.getIssue(id)).status)
    }

    /** No rejection without a reason, enforced in the repository and in `IssueReview`. */
    @Test
    fun rejectingWithoutAReasonIsRefusedAndChangesNothing() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)
        session.signedInAs(libbyLakeManager)

        assertEquals(ReviewOutcome.ReasonRequired, repository.rejectIssue(id, "   "))

        assertEquals(ReviewState.PENDING, repository.getIssue(id)?.review?.state)
    }

    @Test
    fun aManagerForSomewhereElseCannotReview() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(districtTwoManager)
        assertEquals(ReviewOutcome.NotPermitted, repository.approveIssue(id))
        assertEquals(ReviewOutcome.NotPermitted, repository.rejectIssue(id, "Not mine"))

        session.signedInAs(resident)
        assertEquals(ReviewOutcome.NotPermitted, repository.approveIssue(id))

        assertEquals(ReviewState.PENDING, repository.getIssue(id)?.review?.state)
    }

    @Test
    fun aDistrictManagerReviewsWhateverFallsInTheirDistrict() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(draft.copy(neighborhood = "nbhd-eastside"))

        session.signedInAs(districtTwoManager)

        assertEquals(listOf(id), repository.observeReviewQueue().first().map { it.id })
        assertEquals(ReviewOutcome.Recorded, repository.approveIssue(id))
    }

    @Test
    fun theReviewQueueHoldsOnlyWhatIsStillWaitingAndStillOurs() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val mine = repository.submitIssue(libbyLakeDraft)
        val elsewhere = repository.submitIssue(draft.copy(neighborhood = "nbhd-eastside"))

        session.signedInAs(libbyLakeManager)
        assertEquals(listOf(mine), repository.observeReviewQueue().first().map { it.id })

        repository.approveIssue(mine)
        assertTrue(repository.observeReviewQueue().first().isEmpty())

        session.signedInAs(resident)
        assertTrue(repository.observeReviewQueue().first().isEmpty())
        assertNotNull(elsewhere)
    }

    @Test
    fun aManagerEditingAnIssueRecordsWhoChangedWhat() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        now = 5_000L
        val before = assertNotNull(repository.getIssue(id))
        val outcome = repository.editIssue(
            issueId = id,
            draft = IssueEditDraft.of(before).copy(
                title = "  Street light out on N. River Rd  ",
                category = IssueCategory.STREET_LIGHTING,
            ),
        )

        assertEquals(EditOutcome.Recorded, outcome)
        val edited = assertNotNull(repository.getIssue(id))
        assertEquals("Street light out on N. River Rd", edited.title)
        assertEquals(IssueCategory.STREET_LIGHTING, edited.category)

        val edit = assertNotNull(edited.lastEdit)
        assertEquals(libbyLakeManager.id, edit.editedByUserId)
        assertEquals(libbyLakeManager.displayName, edit.editedByDisplayName)
        assertEquals(5_000L, edit.editedAtMillis)
        assertEquals(1, edit.revision)
        assertEquals(setOf(EditableField.TITLE, EditableField.CATEGORY), edit.fields)
        // The correction is a local write like any other, so it re-enters the outbox.
        assertEquals(SyncState.PENDING_UPLOAD, edited.sync.state)
    }

    @Test
    fun theEditedMarkIsVisibleToTheSubmitterToo() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        val before = assertNotNull(repository.getIssue(id))
        repository.editIssue(id, IssueEditDraft.of(before).copy(description = "Dark since June"))

        session.signedInAs(resident)
        val seenBySubmitter = assertNotNull(repository.observeIssue(id).first())
        assertEquals(libbyLakeManager.displayName, seenBySubmitter.lastEdit?.editedByDisplayName)
        assertEquals(setOf(EditableField.DESCRIPTION), seenBySubmitter.lastEdit?.fields)
    }

    @Test
    fun editingCountsUpAndDescribesOnlyTheLatestChange() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        val first = assertNotNull(repository.getIssue(id))
        repository.editIssue(id, IssueEditDraft.of(first).copy(title = "Light out"))
        val second = assertNotNull(repository.getIssue(id))
        repository.editIssue(id, IssueEditDraft.of(second).copy(notesSource = "walk audit"))

        val edit = assertNotNull(repository.getIssue(id)).lastEdit
        assertEquals(2, edit?.revision)
        assertEquals(setOf(EditableField.NOTES_SOURCE), edit?.fields)
    }

    @Test
    fun savingAnUnchangedFormLeavesNoMark() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        val issue = assertNotNull(repository.getIssue(id))

        assertEquals(EditOutcome.NoChanges, repository.editIssue(id, IssueEditDraft.of(issue)))
        assertNull(assertNotNull(repository.getIssue(id)).lastEdit)
    }

    @Test
    fun aManagerForSomewhereElseCannotEdit() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)
        val issue = assertNotNull(repository.getIssue(id))
        val draft = IssueEditDraft.of(issue).copy(title = "Rewritten")

        // The person who filed it cannot rewrite it either: editing is a
        // manager's completeness pass, not an author's second draft.
        assertEquals(EditOutcome.NotPermitted, repository.editIssue(id, draft))

        session.signedInAs(districtTwoManager)
        assertEquals(EditOutcome.NotPermitted, repository.editIssue(id, draft))

        session.signedInAs(admin)
        assertEquals(EditOutcome.Recorded, repository.editIssue(id, draft))
    }

    @Test
    fun movingAnIssueToAnotherNeighborhoodRoutesItToThatDistrictsManager() = runTest {
        seedNeighborhoods()
        val (repository, _) = repository()
        session.signedInAs(resident)
        val id = repository.submitIssue(libbyLakeDraft)

        session.signedInAs(libbyLakeManager)
        val issue = assertNotNull(repository.getIssue(id))
        assertEquals("District 1", issue.location.councilDistrict)

        repository.editIssue(id, IssueEditDraft.of(issue).copy(neighborhood = "nbhd-eastside"))

        val moved = assertNotNull(repository.getIssue(id))
        assertEquals("District 2", moved.location.councilDistrict)
        session.signedInAs(districtTwoManager)
        assertEquals(listOf(id), repository.observeReviewQueue().first().map { it.id })
    }
}
