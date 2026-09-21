package dev.helpmycity.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.domain.model.Issue
import dev.helpmycity.domain.model.IssueFilter
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

/** What the map screen draws, for one set of filter selections. */
data class IssueMapUiState(
    /** Issues matching the filter that have coordinates -- the ones with markers. */
    val plotted: List<Issue> = emptyList(),
    /** Issues matching the filter, with or without coordinates. */
    val matchingCount: Int = 0,
    val neighborhoods: List<Neighborhood> = emptyList(),
    val selectedNeighborhoods: Set<String> = emptySet(),
)

/**
 * Backs [IssueMapScreen].
 *
 * Separate from `IssueListViewModel` because the map wants a different slice --
 * only issues that can be plotted -- and its own filter: the list's status
 * chips and search box do not belong over a map, and a neighborhood filter does
 * not belong in a list.
 */
@KoinViewModel
class IssueMapViewModel(
    private val repository: IssueRepository,
    neighborhoodRepository: NeighborhoodRepository,
    city: CityProfile,
) : ViewModel() {

    val settings: MapSettings = city.map

    private val _selectedNeighborhoods = MutableStateFlow<Set<String>>(emptySet())
    val selectedNeighborhoods: StateFlow<Set<String>> = _selectedNeighborhoods.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IssueMapUiState> = combine(
        _selectedNeighborhoods
            .flatMapLatest { selected ->
                repository.observeIssues(IssueFilter(neighborhoods = selected))
            },
        neighborhoodRepository.observeNeighborhoods(),
        _selectedNeighborhoods,
    ) { issues, neighborhoods, selected ->
        IssueMapUiState(
            // A missing point is not an error: the pin drop is optional, so the
            // screen reports the shortfall rather than hiding it.
            plotted = issues.filter { it.location.point != null },
            matchingCount = issues.size,
            neighborhoods = neighborhoods,
            selectedNeighborhoods = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueMapUiState())

    /**
     * Nothing selected means every neighborhood, which is why this toggles
     * rather than selecting one at a time -- "all" needs to stay reachable.
     */
    fun toggleNeighborhood(id: String) = _selectedNeighborhoods.update { current ->
        if (id in current) current - id else current + id
    }

    fun clearNeighborhoods() {
        _selectedNeighborhoods.value = emptySet()
    }

    /** Total issues in the city, ignoring the filter -- for "N of M". */
    val totalIssues: StateFlow<Int> = repository.observeIssues()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
