package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.repository.IssueRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

/** Newest first. The repository already limits these to the submitter and the issue's managers. */
@KoinViewModel
class RejectedIssuesViewModel(repository: IssueRepository) : ViewModel() {

    val issues: StateFlow<List<Issue>> = repository
        .observeIssues(IssueFilter(statuses = setOf(IssueStatus.REJECTED)))
        .map { issues -> issues.sortedByDescending(Issue::updatedAtMillis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
