package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.domain.access.manages
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssueEditDraft
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.EditOutcome
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.jordond.compass.geocoder.Geocoder
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

/**
 * The manager edit form: the submission form's fields, minus the submitter's
 * contact details. Status, triage and the city reference number are edited on
 * the detail screen, where each has its own audited path.
 */
data class EditIssueFormState(
    val title: String = "",
    val description: String = "",
    val requestedAction: String = "",
    val locationDescription: String = "",
    val point: GeoPoint? = null,
    /** True while [point] came from the address rather than from a finger. */
    val isPointApproximate: Boolean = false,
    /** What geocoding made of the location, saved on the issue either way. */
    val geocodedAddress: String? = null,
    val geocoding: GeocodingStatus = GeocodingStatus.Idle,
    val isPickingPoint: Boolean = false,
    val category: IssueCategory = IssueCategory.OTHER,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val neighborhood: String? = null,
    val departmentId: String? = null,
    val notesSource: String = "",
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    /** False until the issue has been read, so the form never starts blank over real data. */
    val isLoaded: Boolean = false,
    /** The viewer stopped managing this issue while the form was open. */
    val isPermitted: Boolean = true,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    val descriptionError: Boolean get() = showErrors && description.isBlank()
    val locationError: Boolean get() = showErrors && locationDescription.isBlank()

    /** The same three fields the submission form requires; an edit cannot empty them. */
    val isValid: Boolean
        get() = title.isNotBlank() && description.isNotBlank() && locationDescription.isNotBlank()
}

@KoinViewModel
class EditIssueViewModel(
    @InjectedParam private val issueId: String,
    private val issueRepository: IssueRepository,
    departmentRepository: DepartmentRepository,
    neighborhoodRepository: NeighborhoodRepository,
    session: UserSession,
    city: CityProfile,
    geocoder: Geocoder,
) : ViewModel() {

    /** Where the pin picker opens; see [dev.helpmycity.ui.map.LocationPickerMap]. */
    val mapSettings: MapSettings = city.map

    /** The same lookup the report form uses, under the same two rules. */
    private val location = LocationField(geocoder, viewModelScope)

    private val _form = MutableStateFlow(EditIssueFormState())

    // Eager, because save() reads .value whether or not a screen is watching.
    val form: StateFlow<EditIssueFormState> =
        combine(_form, location.state) { form, place -> form.withLocation(place) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, EditIssueFormState())

    /** Set when a save was refused; cleared on the next attempt. */
    private val _saveError = MutableStateFlow<EditOutcome?>(null)
    val saveError: StateFlow<EditOutcome?> = _saveError.asStateFlow()

    val options: StateFlow<NewIssueOptions> = combine(
        departmentRepository.observeDepartments(),
        neighborhoodRepository.observeNeighborhoods(),
    ) { departments: List<Department>, neighborhoods: List<Neighborhood> ->
        NewIssueOptions(departments, neighborhoods)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewIssueOptions())

    init {
        viewModelScope.launch {
            // Read once, not observed: a form that rewrote itself on every sync
            // would lose what the manager typed.
            val issue = issueRepository.getIssue(issueId)
            if (issue == null) {
                _form.update { it.copy(isLoaded = true, isPermitted = false) }
                _saveError.value = EditOutcome.IssueNotFound
                return@launch
            }
            val draft = IssueEditDraft.of(issue)
            // Seeded, not typed: opening a form must not geocode what is already there.
            location.start(
                description = draft.locationDescription,
                point = draft.point,
                geocodedAddress = draft.geocodedAddress,
            )
            _form.value = EditIssueFormState(
                title = draft.title,
                description = draft.description,
                requestedAction = draft.requestedAction,
                category = draft.category,
                priority = draft.priority,
                neighborhood = draft.neighborhood,
                departmentId = draft.departmentId,
                notesSource = draft.notesSource,
                isLoaded = true,
                // Checked again by the repository on save; here only so the
                // form does not invite work that will be refused.
                isPermitted = session.currentUser.value.manages(issue),
            )
        }
    }

    fun onTitleChange(value: String) = _form.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onRequestedActionChange(value: String) = _form.update { it.copy(requestedAction = value) }
    fun onLocationChange(value: String) = location.onDescriptionChange(value)
    fun onPointChange(value: GeoPoint?) = location.onPointChange(value)
    fun onTogglePointPicker() = _form.update { it.copy(isPickingPoint = !it.isPickingPoint) }
    fun onCategoryChange(value: IssueCategory) = _form.update { it.copy(category = value) }
    fun onPriorityChange(value: IssuePriority) = _form.update { it.copy(priority = value) }
    fun onNeighborhoodChange(value: String?) = _form.update { it.copy(neighborhood = value) }
    fun onDepartmentChange(value: String?) = _form.update { it.copy(departmentId = value) }
    fun onNotesSourceChange(value: String) = _form.update { it.copy(notesSource = value) }

    /** Calls [onSaved] once the edit is recorded, and also when nothing changed. */
    fun save(onSaved: () -> Unit) {
        val state = form.value
        if (!state.isValid) {
            _form.update { it.copy(showErrors = true) }
            return
        }
        _saveError.value = null
        _form.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val outcome = issueRepository.editIssue(
                issueId = issueId,
                draft = IssueEditDraft(
                    title = state.title,
                    description = state.description,
                    requestedAction = state.requestedAction,
                    category = state.category,
                    priority = state.priority,
                    locationDescription = state.locationDescription,
                    point = state.point,
                    geocodedAddress = state.geocodedAddress,
                    neighborhood = state.neighborhood,
                    departmentId = state.departmentId,
                    notesSource = state.notesSource,
                ),
            )
            _form.update { it.copy(isSaving = false) }
            when (outcome) {
                EditOutcome.Recorded, EditOutcome.NoChanges -> onSaved()
                else -> _saveError.value = outcome
            }
        }
    }
}

/** The location the [LocationField] is holding, laid over the rest of the form. */
private fun EditIssueFormState.withLocation(location: LocationFieldState): EditIssueFormState =
    copy(
        locationDescription = location.description,
        point = location.point,
        isPointApproximate = location.isPointApproximate,
        geocodedAddress = location.geocodedAddress,
        geocoding = location.status,
    )
