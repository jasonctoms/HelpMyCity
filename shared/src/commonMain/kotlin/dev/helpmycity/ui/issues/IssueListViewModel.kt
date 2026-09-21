package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.repository.IssueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

/**
 * Backs both the list and the kanban board -- they are the same query with a
 * different presentation, so they share a filter.
 */
@KoinViewModel
class IssueListViewModel(private val repository: IssueRepository) : ViewModel() {

    private val _filter = MutableStateFlow(IssueFilter.None)
    val filter: StateFlow<IssueFilter> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val issues: StateFlow<List<Issue>> = _filter
        .flatMapLatest { repository.observeIssues(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unfiltered, so the board always shows every column's true count. */
    val allIssues: StateFlow<List<Issue>> = repository.observeIssues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) = _filter.update { it.copy(searchQuery = query) }

    fun toggleStatus(status: IssueStatus) = _filter.update { current ->
        current.copy(
            statuses = if (status in current.statuses) {
                current.statuses - status
            } else {
                current.statuses + status
            }
        )
    }

    fun clearFilters() {
        _filter.value = IssueFilter.None
    }
}
