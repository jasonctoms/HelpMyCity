package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.repository.IssueRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

data class ReviewQueueUiState(
    /** Oldest first: the report that has been waiting longest is the one to read next. */
    val waiting: List<Issue> = emptyList(),
    /**
     * True when the signed-in manager has no district, neighborhood or
     * department yet. An empty queue then means "nobody can reach you", which is
     * a different problem from "you are caught up" and deserves different words.
     */
    val hasNoScope: Boolean = false,
)

/**
 * Backs [ReviewQueueScreen].
 *
 * Thin by design: the repository already decides which issues are this user's to
 * review, so there is nothing to filter here.
 */
@KoinViewModel
class ReviewQueueViewModel(
    repository: IssueRepository,
    session: UserSession,
) : ViewModel() {

    val uiState: StateFlow<ReviewQueueUiState> = combine(
        repository.observeReviewQueue(),
        session.currentUser,
    ) { waiting, user ->
        ReviewQueueUiState(
            waiting = waiting,
            hasNoScope = user?.role?.canManageConfiguration == false && user.scope.isEmpty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewQueueUiState())
}
