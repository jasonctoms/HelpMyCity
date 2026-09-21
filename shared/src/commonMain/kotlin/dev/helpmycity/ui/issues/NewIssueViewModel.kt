package dev.helpmycity.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssueDraft
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.model.ReporterContact
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.IssueRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import dev.helpmycity.domain.util.IdGenerator
import dev.jordond.compass.geocoder.Geocoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * An image picked but not yet stored.
 *
 * Carries an id so a resident can remove one before submitting; comparing raw
 * `ByteArray`s would compare by identity and could not tell two picks apart.
 */
data class PendingPhoto(val id: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is PendingPhoto && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

data class NewIssueFormState(
    val title: String = "",
    val description: String = "",
    val requestedAction: String = "",
    val locationDescription: String = "",
    /** Dropped by hand on the map, or geocoded from [locationDescription]. */
    val point: GeoPoint? = null,
    /** True while [point] came from the address rather than from a finger. */
    val isPointApproximate: Boolean = false,
    /** What geocoding made of the location, stored on the issue either way. */
    val geocodedAddress: String? = null,
    val geocoding: GeocodingStatus = GeocodingStatus.Idle,
    val isPickingPoint: Boolean = false,
    val photos: List<PendingPhoto> = emptyList(),
    val category: IssueCategory = IssueCategory.OTHER,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val neighborhood: String? = null,
    val departmentId: String? = null,
    val reporterName: String = "",
    val reporterEmail: String = "",
    val reporterPhone: String = "",
    val showErrors: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    val descriptionError: Boolean get() = showErrors && description.isBlank()
    val locationError: Boolean get() = showErrors && locationDescription.isBlank()
    val isValid: Boolean
        get() = title.isNotBlank() && description.isNotBlank() && locationDescription.isNotBlank()
}

data class NewIssueOptions(
    val departments: List<Department> = emptyList(),
    val neighborhoods: List<Neighborhood> = emptyList(),
)

@KoinViewModel
class NewIssueViewModel(
    private val issueRepository: IssueRepository,
    departmentRepository: DepartmentRepository,
    neighborhoodRepository: NeighborhoodRepository,
    city: CityProfile,
    geocoder: Geocoder,
    private val idGenerator: IdGenerator,
) : ViewModel() {

    /** Where the pin picker opens; see [dev.helpmycity.ui.map.LocationPickerMap]. */
    val mapSettings: MapSettings = city.map

    /** "Where is it?" and the pin, and the geocoding that keeps them in step. */
    private val location = LocationField(geocoder, viewModelScope)

    private val _form = MutableStateFlow(NewIssueFormState())

    // Eager, because submit() reads .value whether or not a screen is watching.
    val form: StateFlow<NewIssueFormState> =
        combine(_form, location.state) { form, place -> form.withLocation(place) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, NewIssueFormState())

    val options: StateFlow<NewIssueOptions> = combine(
        departmentRepository.observeDepartments(),
        neighborhoodRepository.observeNeighborhoods(),
    ) { departments, neighborhoods -> NewIssueOptions(departments, neighborhoods) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewIssueOptions())

    /**
     * Near-duplicate detection: same category, similar location text. A nudge
     * rather than a block -- nobody is stopped from filing.
     */
    val possibleDuplicateCount: StateFlow<Int> = combine(
        form,
        issueRepository.observeIssues(),
    ) { form, issues ->
        val needle = form.locationDescription.trim()
        if (needle.length < MIN_DUPLICATE_MATCH_LENGTH) {
            0
        } else {
            issues.count { issue ->
                issue.category == form.category &&
                    issue.location.description.contains(needle, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onTitleChange(value: String) = _form.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onRequestedActionChange(value: String) = _form.update { it.copy(requestedAction = value) }

    fun onLocationChange(value: String) = location.onDescriptionChange(value)

    fun onPointChange(value: GeoPoint?) = location.onPointChange(value)

    fun onPhotosPicked(images: List<ByteArray>) = _form.update { current ->
        current.copy(photos = current.photos + images.map { PendingPhoto(idGenerator.newId(), it) })
    }

    fun onPhotoRemoved(photoId: String) = _form.update { current ->
        current.copy(photos = current.photos.filterNot { it.id == photoId })
    }
    fun onTogglePointPicker() = _form.update { it.copy(isPickingPoint = !it.isPickingPoint) }
    fun onCategoryChange(value: IssueCategory) = _form.update { current ->
        // Pre-select the department that usually owns this category; the user can override.
        val suggested = options.value.departments
            .firstOrNull { value in it.handlesCategories }
            ?.id
        current.copy(category = value, departmentId = current.departmentId ?: suggested)
    }

    fun onPriorityChange(value: IssuePriority) = _form.update { it.copy(priority = value) }
    fun onNeighborhoodChange(value: String?) = _form.update { it.copy(neighborhood = value) }
    fun onDepartmentChange(value: String?) = _form.update { it.copy(departmentId = value) }
    fun onReporterNameChange(value: String) = _form.update { it.copy(reporterName = value) }
    fun onReporterEmailChange(value: String) = _form.update { it.copy(reporterEmail = value) }
    fun onReporterPhoneChange(value: String) = _form.update { it.copy(reporterPhone = value) }

    /** Calls [onSubmitted] with the new issue's id so the caller can navigate to it. */
    fun submit(onSubmitted: (String) -> Unit) {
        val state = form.value
        if (!state.isValid) {
            _form.update { it.copy(showErrors = true) }
            return
        }
        _form.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val id = issueRepository.submitIssue(
                IssueDraft(
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
                    reporter = ReporterContact(
                        name = state.reporterName.ifBlank { null },
                        email = state.reporterEmail.ifBlank { null },
                        phone = state.reporterPhone.ifBlank { null },
                    ).takeUnless { it.isEmpty },
                )
            )
            state.photos.forEach { photo ->
                issueRepository.addPhoto(issueId = id, bytes = photo.bytes)
            }
            location.clear()
            _form.value = NewIssueFormState()
            onSubmitted(id)
        }
    }

    private companion object {
        const val MIN_DUPLICATE_MATCH_LENGTH = 4
    }
}

/** The location the [LocationField] is holding, laid over the rest of the form. */
internal fun NewIssueFormState.withLocation(location: LocationFieldState): NewIssueFormState = copy(
    locationDescription = location.description,
    point = location.point,
    isPointApproximate = location.isPointApproximate,
    geocodedAddress = location.geocodedAddress,
    geocoding = location.status,
)
