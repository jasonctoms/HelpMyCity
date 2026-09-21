package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.data.remote.external.ExternalRequestGateway
import dev.helpmycity.domain.access.manages
import dev.helpmycity.domain.access.submitted
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.IssuePhoto
import dev.helpmycity.domain.model.IssueStatusChange
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.helpmycity.domain.repository.ReviewOutcome
import dev.helpmycity.domain.repository.StatusOutcome
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

data class IssueDetailUiState(
    val issue: Issue? = null,
    val department: Department? = null,
    /** Resolved from the stable id stored on the issue; null if that id is gone. */
    val neighborhoodName: String? = null,
    val photos: List<IssuePhoto> = emptyList(),
    val history: List<IssueStatusChange> = emptyList(),
    /** This viewer manages the area: they can triage, approve and reject. */
    val canReview: Boolean = false,
    /** This viewer filed it, which is why they can see it before it is approved. */
    val isSubmitter: Boolean = false,
    /** This viewer has starred it; a second star would not count. */
    val hasSupported: Boolean = false,
    /** Signed in, as anyone -- a star has to belong to someone. */
    val canSupport: Boolean = false,
    val isLoaded: Boolean = false,
)

/** What the reject box is doing right now. */
data class RejectionState(
    val isOpen: Boolean = false,
    val reason: String = "",
    /** Set after a reject attempt with a blank reason. */
    val showReasonRequired: Boolean = false,
) {
    val canSubmit: Boolean get() = reason.isNotBlank()
}

/** What the resolution box is doing right now. */
data class CompletionState(
    val isOpen: Boolean = false,
    val resolution: String = "",
    /** Set after a completion attempt with a blank resolution. */
    val showResolutionRequired: Boolean = false,
) {
    val canSubmit: Boolean get() = resolution.isNotBlank()
}

@KoinViewModel
class IssueDetailViewModel(
    @InjectedParam private val issueId: String,
    private val issueRepository: IssueRepository,
    departmentRepository: DepartmentRepository,
    neighborhoodRepository: NeighborhoodRepository,
    private val session: UserSession,
    private val externalRequests: ExternalRequestGateway,
    private val time: TimeProvider,
) : ViewModel() {

    private val details = combine(
        issueRepository.observeIssue(issueId),
        issueRepository.observeHistory(issueId),
        issueRepository.observePhotos(issueId),
        departmentRepository.observeDepartments(),
        neighborhoodRepository.observeNeighborhoods(),
    ) { issue, history, photos, departments, neighborhoods ->
        IssueDetailUiState(
            issue = issue,
            department = departments.firstOrNull { it.id == issue?.departmentId },
            neighborhoodName = neighborhoods
                .firstOrNull { it.id == issue?.location?.neighborhood }
                ?.name,
            photos = photos,
            history = history,
            isLoaded = true,
        )
    }

    /**
     * Who is looking is part of the state, not a one-off read: a sign-in while
     * this screen is open has to add the review controls, and a sign-out has to
     * take them away.
     */
    val uiState: StateFlow<IssueDetailUiState> = combine(
        details,
        session.currentUser,
        issueRepository.observeSupportedIssueIds(),
    ) { state, user, supported ->
        val issue = state.issue
        state.copy(
            canReview = issue != null && user.manages(issue),
            isSubmitter = issue != null && user.submitted(issue),
            hasSupported = issueId in supported,
            canSupport = user != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueDetailUiState())

    private val _rejection = MutableStateFlow(RejectionState())
    val rejection: StateFlow<RejectionState> = _rejection.asStateFlow()

    /** Set when a review action was refused; cleared on the next attempt. */
    private val _reviewError = MutableStateFlow<ReviewOutcome?>(null)
    val reviewError: StateFlow<ReviewOutcome?> = _reviewError.asStateFlow()

    /** Null when this deployment has no city system to hand off to. */
    val externalSystemName: String? get() = externalRequests.systemDisplayName

    fun externalSubmissionUrl(issue: Issue): String? = externalRequests.submissionUrl(issue)

    private val _completion = MutableStateFlow(CompletionState())
    val completion: StateFlow<CompletionState> = _completion.asStateFlow()

    /** Complete opens the resolution box instead, since it cannot be set without one. */
    fun changeStatus(newStatus: IssueStatus) {
        if (newStatus == IssueStatus.COMPLETE) {
            onEditResolution()
            return
        }
        _completion.value = CompletionState()
        viewModelScope.launch { recordStatus(newStatus, resolution = null) }
    }

    fun onEditResolution() {
        _completion.value = CompletionState(
            isOpen = true,
            resolution = uiState.value.issue?.resolution.orEmpty(),
        )
    }

    fun onResolutionChange(value: String) =
        _completion.update { it.copy(resolution = value, showResolutionRequired = false) }

    fun onCompletionCancel() {
        _completion.value = CompletionState()
    }

    fun confirmCompletion() {
        val resolution = _completion.value.resolution
        if (resolution.isBlank()) {
            _completion.update { it.copy(showResolutionRequired = true) }
            return
        }
        viewModelScope.launch {
            when (recordStatus(IssueStatus.COMPLETE, resolution)) {
                StatusOutcome.ResolutionRequired ->
                    _completion.update { it.copy(showResolutionRequired = true) }
                else -> _completion.value = CompletionState()
            }
        }
    }

    private suspend fun recordStatus(newStatus: IssueStatus, resolution: String?): StatusOutcome {
        val actor = session.currentUser.value
        return issueRepository.changeStatus(
            issueId = issueId,
            newStatus = newStatus,
            resolution = resolution,
            changedByUserId = actor?.id,
            changedByDisplayName = actor?.displayName,
        )
    }

    fun addSupport() {
        viewModelScope.launch { issueRepository.addSupport(issueId) }
    }

    fun approve() {
        _reviewError.value = null
        viewModelScope.launch { record(issueRepository.approveIssue(issueId)) }
    }

    fun onRejectClick() = _rejection.update { it.copy(isOpen = true, showReasonRequired = false) }

    fun onRejectCancel() {
        _rejection.value = RejectionState()
    }

    fun onRejectReasonChange(value: String) =
        _rejection.update { it.copy(reason = value, showReasonRequired = false) }

    /**
     * Rejects with the typed reason. A blank one never reaches the repository --
     * and the repository refuses it anyway, because "a manager cannot reject
     * without a reason" is a rule about the data, not about this screen.
     */
    fun confirmRejection() {
        val reason = _rejection.value.reason
        if (reason.isBlank()) {
            _rejection.update { it.copy(showReasonRequired = true) }
            return
        }
        _reviewError.value = null
        viewModelScope.launch {
            val outcome = issueRepository.rejectIssue(issueId, reason)
            if (outcome == ReviewOutcome.Recorded) _rejection.value = RejectionState()
            record(outcome)
        }
    }

    private fun record(outcome: ReviewOutcome) {
        _reviewError.value = outcome.takeUnless { it == ReviewOutcome.Recorded }
    }

    /** Records a ticket number a manager copied back from the city's own site. */
    fun recordExternalReference(referenceNumber: String) {
        if (referenceNumber.isBlank()) return
        viewModelScope.launch {
            val issue = issueRepository.getIssue(issueId) ?: return@launch
            val reference = externalRequests.recordManualSubmission(
                issueId = issueId,
                referenceNumber = referenceNumber.trim(),
                submittedAtMillis = time.nowMillis(),
            )
            issueRepository.updateIssue(issue.copy(externalReference = reference))
        }
    }
}
