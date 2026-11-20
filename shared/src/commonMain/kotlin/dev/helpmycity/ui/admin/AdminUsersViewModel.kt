package dev.helpmycity.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.helpmycity.domain.repository.UserRepository
import dev.helpmycity.ui.areaNames
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

data class AdminUserRow(
    val user: User,
    val areaNames: List<String>,
    /** The admin looking at the screen. Their own row says so, and its role can't be changed. */
    val isSelf: Boolean,
)

data class AdminUsersUiState(
    val users: List<AdminUserRow> = emptyList(),
    /** False for anyone who is not an admin. The repository refuses the writes either way. */
    val isPermitted: Boolean = true,
)

/**
 * Everyone who has ever signed in to this deployment, so an admin can give them a
 * role and a part of the city.
 *
 * Someone shows up here the first time their account signs in. With the mock
 * identity provider that is the only way anyone gets in, and with a real one it
 * is how the list fills up as a city's volunteers join.
 */
@KoinViewModel
class AdminUsersViewModel(
    users: UserRepository,
    session: UserSession,
    neighborhoodRepository: NeighborhoodRepository,
    departmentRepository: DepartmentRepository,
) : ViewModel() {

    val uiState: StateFlow<AdminUsersUiState> = combine(
        users.observeUsers(),
        session.currentUser,
        neighborhoodRepository.observeNeighborhoods(),
        departmentRepository.observeDepartments(),
    ) { people, viewer, neighborhoods, departments ->
        AdminUsersUiState(
            users = people.map { person ->
                AdminUserRow(
                    user = person,
                    areaNames = person.scope.areaNames(neighborhoods, departments),
                    isSelf = person.id == viewer?.id,
                )
            },
            isPermitted = viewer?.role?.canManageConfiguration == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUsersUiState())
}
