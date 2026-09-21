package dev.helpmycity.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.export.IssueCsvExporter
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.helpmycity.domain.util.TimeProvider
import dev.helpmycity.ui.areaNames
import dev.helpmycity.ui.formatTimestamp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

data class ProfileUiState(
    val user: User? = null,
    /** Resolved names for [dev.helpmycity.domain.model.ManagerScope]'s ids. */
    val areaNames: List<String> = emptyList(),
) {
    val canManageUsers: Boolean get() = user?.role?.canManageConfiguration == true

    val canExportIssues: Boolean get() = user?.role?.canManageConfiguration == true

    /** A guest has nothing stored, so there is nothing to edit. */
    val canEditProfile: Boolean get() = user?.isGuest == false

    /** Only a manager has areas: an admin reaches everything and a resident nothing. */
    val showsAreas: Boolean get() = user?.role == UserRole.MANAGER

    val isCitywide: Boolean get() = user?.scope?.citywide == true
}

@KoinViewModel
class ProfileViewModel(
    private val session: UserSession,
    neighborhoodRepository: NeighborhoodRepository,
    departmentRepository: DepartmentRepository,
    private val issueRepository: IssueRepository,
    private val csvExporter: IssueCsvExporter,
    private val time: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        session.currentUser,
        neighborhoodRepository.observeNeighborhoods(),
        departmentRepository.observeDepartments(),
    ) { user, neighborhoods, departments ->
        ProfileUiState(
            user = user,
            areaNames = user?.scope?.areaNames(neighborhoods, departments).orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    /** A dated file name, so a second export does not overwrite the first. */
    fun exportFileName(): String = "issues-${formatTimestamp(time.nowMillis())}"

    /**
     * Every issue this device holds, which for an admin is every issue there
     * is as of the last sync. Null for anyone else.
     */
    suspend fun exportCsv(): String? {
        if (session.currentUser.value?.role?.canManageConfiguration != true) return null
        return csvExporter.toCsv(issueRepository.observeIssues().first())
    }
}
