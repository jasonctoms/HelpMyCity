package dev.helpmycity.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.repository.AssignmentOutcome
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.helpmycity.domain.repository.UserRepository
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

/** What an admin can hand out: the four independent halves of a [ManagerScope]. */
data class AdminUserOptions(
    val districts: List<String> = emptyList(),
    val neighborhoods: List<Neighborhood> = emptyList(),
    val departments: List<Department> = emptyList(),
)

data class AdminUserFormState(
    val user: User? = null,
    val role: UserRole = UserRole.RESIDENT,
    val citywide: Boolean = false,
    val districts: Set<String> = emptySet(),
    val neighborhoodIds: Set<String> = emptySet(),
    val departmentIds: Set<String> = emptySet(),
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    /** The admin is editing themselves, so the role picker is locked. */
    val isSelf: Boolean = false,
    val isPermitted: Boolean = true,
) {
    val showsAreas: Boolean get() = role == UserRole.MANAGER

    /** Citywide already covers everything, so the narrower pickers stop taking taps. */
    val areasEnabled: Boolean get() = !citywide

    val scope: ManagerScope
        get() = if (citywide) {
            ManagerScope.Citywide
        } else {
            ManagerScope(
                districts = districts,
                neighborhoodIds = neighborhoodIds,
                departmentIds = departmentIds,
            )
        }

    /** A manager nobody can reach: worth saying out loud before it is saved. */
    val warnsEmptyScope: Boolean get() = showsAreas && scope.isEmpty
}

/**
 * One person's role and areas.
 *
 * The role picker refuses to change your own role and the repository refuses it
 * again, because an admin who demotes themselves takes this screen away from the
 * deployment with no way back.
 */
@KoinViewModel
class AdminUserViewModel(
    @InjectedParam private val userId: String,
    private val users: UserRepository,
    private val session: UserSession,
    neighborhoodRepository: NeighborhoodRepository,
    departmentRepository: DepartmentRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AdminUserFormState())
    val form: StateFlow<AdminUserFormState> = _form.asStateFlow()

    /** Set when a save was refused; cleared on the next attempt. */
    private val _saveError = MutableStateFlow<AssignmentOutcome?>(null)
    val saveError: StateFlow<AssignmentOutcome?> = _saveError.asStateFlow()

    val options: StateFlow<AdminUserOptions> = combine(
        neighborhoodRepository.observeNeighborhoods(),
        departmentRepository.observeDepartments(),
    ) { neighborhoods, departments ->
        AdminUserOptions(
            // The city publishes districts through its neighborhoods rather than
            // as a list of their own, so this is where they come from.
            districts = neighborhoods.mapNotNull(Neighborhood::councilDistrict).distinct().sorted(),
            neighborhoods = neighborhoods,
            departments = departments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUserOptions())

    init {
        viewModelScope.launch {
            val viewer = session.currentUser.value
            val person = users.getUser(userId)
            if (person == null) {
                _form.update { it.copy(isLoaded = true, isPermitted = false) }
                _saveError.value = AssignmentOutcome.UserNotFound
                return@launch
            }
            _form.value = AdminUserFormState(
                user = person,
                role = person.role,
                citywide = person.scope.citywide,
                districts = person.scope.districts,
                neighborhoodIds = person.scope.neighborhoodIds,
                departmentIds = person.scope.departmentIds,
                isLoaded = true,
                isSelf = person.id == viewer?.id,
                isPermitted = viewer?.role?.canManageConfiguration == true,
            )
        }
    }

    fun onRoleChange(role: UserRole) = _form.update {
        if (it.isSelf) it else it.copy(role = role)
    }

    fun onCitywideChange(citywide: Boolean) = _form.update { it.copy(citywide = citywide) }

    fun onDistrictToggle(district: String) = _form.update {
        it.copy(districts = it.districts.toggle(district))
    }

    fun onNeighborhoodToggle(id: String) = _form.update {
        it.copy(neighborhoodIds = it.neighborhoodIds.toggle(id))
    }

    fun onDepartmentToggle(id: String) = _form.update {
        it.copy(departmentIds = it.departmentIds.toggle(id))
    }

    /** Calls [onSaved] once the assignment is recorded, and also when nothing changed. */
    fun save(onSaved: () -> Unit) {
        val state = _form.value
        if (state.isSaving || !state.isPermitted) return
        _saveError.value = null
        _form.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val outcome = users.assign(
                actor = session.currentUser.value,
                userId = userId,
                role = state.role,
                scope = state.scope,
            )
            _form.update { it.copy(isSaving = false) }
            when (outcome) {
                AssignmentOutcome.Saved, AssignmentOutcome.NoChanges -> onSaved()
                else -> _saveError.value = outcome
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
