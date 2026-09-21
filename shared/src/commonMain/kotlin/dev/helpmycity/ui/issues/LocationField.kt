package dev.helpmycity.ui.issues

import dev.helpmycity.data.remote.geocoding.asAddressLine
import dev.helpmycity.data.remote.geocoding.toGeoPoint
import dev.helpmycity.domain.model.GeoPoint
import dev.jordond.compass.geocoder.Geocoder
import dev.jordond.compass.geocoder.GeocoderResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the address lookup is doing, for the line under the location field. */
sealed interface GeocodingStatus {
    data object Idle : GeocodingStatus
    data object Searching : GeocodingStatus
    data class Resolved(val address: String) : GeocodingStatus
    data object NoMatch : GeocodingStatus
    data object Unavailable : GeocodingStatus
}

data class LocationFieldState(
    val description: String = "",
    val point: GeoPoint? = null,
    /** True while [point] came from the address rather than from a finger. */
    val isPointApproximate: Boolean = false,
    /** What geocoding made of the location, stored on the issue either way. */
    val geocodedAddress: String? = null,
    /** [description] is a geocoder's words, so it may be overwritten. */
    val isDescriptionFromGeocoder: Boolean = false,
    val status: GeocodingStatus = GeocodingStatus.Idle,
)

/**
 * "Where is it?" and the map pin, kept in step by geocoding.
 *
 * One object because the report form and the manager edit form ask the same
 * question and must answer it the same way. Two rules do the work, and both are
 * about not overruling a person:
 *
 * - A dropped pin fills the location field only when it is empty or still holds
 *   a previous lookup's words. What a resident wrote outranks what a geocoder
 *   would call the place, and the geocoder's version is kept alongside it on
 *   [LocationFieldState.geocodedAddress] rather than instead of it.
 * - Typing never moves a pin that was dropped by hand. A finger on the map is
 *   the more precise answer, and the more deliberate one.
 *
 * Every lookup ends at an address: a forward match yields coordinates, which are
 * described straight back. Compass's `PlatformGeocoder` has no room to return
 * both at once, and asking twice is cheap -- free on a device geocoder, and
 * answered from memory by
 * [dev.helpmycity.data.remote.geocoding.NominatimPlatformGeocoder].
 */
class LocationField(
    private val geocoder: Geocoder,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(LocationFieldState())
    val state: StateFlow<LocationFieldState> = _state.asStateFlow()

    /**
     * Only typing lands here, never a reverse-geocoded fill -- otherwise an
     * address written into the field would search for itself and move the pin
     * that produced it.
     */
    private val typed = MutableStateFlow("")
    private var pinLookup: Job? = null

    init {
        if (geocoder.isAvailable()) {
            scope.launch {
                // collectLatest is the debounce: a new keystroke cancels the
                // wait, and cancels a request already in flight.
                typed.collectLatest { address ->
                    delay(TYPING_SETTLE_MILLIS)
                    placePinFor(address)
                }
            }
        }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value, isDescriptionFromGeocoder = false) }
        typed.value = value
    }

    /** A pin placed or cleared by hand, which is always the more precise answer. */
    fun onPointChange(value: GeoPoint?) {
        pinLookup?.cancel()
        _state.update { it.copy(point = value, isPointApproximate = false) }
        if (value == null) {
            _state.update { it.copy(geocodedAddress = null, status = GeocodingStatus.Idle) }
            return
        }
        if (!geocoder.isAvailable()) return
        _state.update { it.copy(status = GeocodingStatus.Searching) }
        pinLookup = scope.launch { describePin(value) }
    }

    /**
     * Loads what an issue already says, without treating it as something a
     * person just typed -- an edit form opening must not geocode itself.
     */
    fun start(description: String, point: GeoPoint?, geocodedAddress: String?) {
        pinLookup?.cancel()
        _state.value = LocationFieldState(
            description = description,
            point = point,
            geocodedAddress = geocodedAddress,
        )
        // [typed] is deliberately left empty. Seeding it here would hand the
        // stored location to the debounce as though someone had just written it,
        // and an edit form would move -- or invent -- a pin nobody asked it to.
    }

    /** Back to empty, for a form that has just been submitted. */
    fun clear() {
        pinLookup?.cancel()
        _state.value = LocationFieldState()
        typed.value = ""
    }

    private suspend fun describePin(point: GeoPoint) {
        val result = geocoder.reverse(point.latitude, point.longitude)
        val address = result.getFirstOrNull()?.asAddressLine()
        if (address == null) {
            _state.update { it.copy(status = result.statusWhenNothingUsable()) }
            return
        }
        _state.update { current ->
            val adopt = current.description.isBlank() || current.isDescriptionFromGeocoder
            current.copy(
                description = if (adopt) address else current.description,
                isDescriptionFromGeocoder = adopt,
                geocodedAddress = address,
                status = GeocodingStatus.Resolved(address),
            )
        }
    }

    private suspend fun placePinFor(query: String) {
        val address = query.trim()
        if (address.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(status = GeocodingStatus.Idle) }
            return
        }
        // A pin dropped by hand is the truth about where this is; leave it alone.
        val current = _state.value
        if (current.point != null && !current.isPointApproximate) return

        _state.update { it.copy(status = GeocodingStatus.Searching) }
        val result = geocoder.forward(address)
        val match = result.getFirstOrNull()
        if (match == null) {
            _state.update { it.copy(status = result.statusWhenNothingUsable()) }
            return
        }

        val point = match.toGeoPoint()
        // Best effort: the pin is worth placing whether or not the geocoder can
        // also name the spot, and the map labels it approximate either way.
        val named = geocoder.reverse(match).getFirstOrNull()?.asAddressLine()
        _state.update {
            it.copy(
                point = point,
                isPointApproximate = true,
                geocodedAddress = named,
                status = named?.let(GeocodingStatus::Resolved) ?: GeocodingStatus.Idle,
            )
        }
    }

    private companion object {
        /** Long enough to be an address rather than the start of one. */
        const val MIN_QUERY_LENGTH = 5

        /** A pause in typing, not a pause in thinking. */
        const val TYPING_SETTLE_MILLIS = 700L
    }
}

/** Compass's unhappy outcomes, in the terms the form speaks. */
private fun GeocoderResult<*>.statusWhenNothingUsable(): GeocodingStatus = when (this) {
    GeocoderResult.NotFound -> GeocodingStatus.NoMatch
    // No geocoder on this device and no fallback configured. Say nothing rather
    // than blame the network for a feature this build does not have.
    GeocoderResult.NotSupported -> GeocodingStatus.Idle
    else -> GeocodingStatus.Unavailable
}
