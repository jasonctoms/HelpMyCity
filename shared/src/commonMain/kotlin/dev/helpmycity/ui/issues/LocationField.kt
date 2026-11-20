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
    /** True while [point] came from the address instead of from a finger. */
    val isPointApproximate: Boolean = false,
    /** What geocoding made of the location, stored on the issue either way. */
    val geocodedAddress: String? = null,
    /** [description] is the geocoder's words, so it is fine to overwrite it. */
    val isDescriptionFromGeocoder: Boolean = false,
    val status: GeocodingStatus = GeocodingStatus.Idle,
)

/**
 * "Where is it?" and the map pin, kept in step by geocoding.
 *
 * This is one object because the report form and the manager edit form ask the
 * same question and have to answer it the same way. Two rules do most of the
 * work, and both of them are about not overruling a person:
 *
 * - A dropped pin only fills in the location field when it is empty or still has
 *   a previous lookup's words in it. What a resident wrote beats whatever a
 *   geocoder would call the place, and the geocoder's version gets kept next to it
 *   on [LocationFieldState.geocodedAddress] instead of replacing it.
 * - Typing never moves a pin that was dropped by hand. A finger on the map is the
 *   more precise answer, and the more deliberate one.
 *
 * Every lookup ends at an address. A forward match gives back coordinates, which
 * then get described right back. Compass's `PlatformGeocoder` has no room to
 * return both at once, and asking twice is cheap; it is free on a device geocoder,
 * and [dev.helpmycity.data.remote.geocoding.NominatimPlatformGeocoder] answers it
 * from memory.
 */
class LocationField(
    private val geocoder: Geocoder,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(LocationFieldState())
    val state: StateFlow<LocationFieldState> = _state.asStateFlow()

    /**
     * Only typing ends up here, never a reverse-geocoded fill. Otherwise an
     * address written into the field would search for itself and move the pin that
     * produced it.
     */
    private val typed = MutableStateFlow("")
    private var pinLookup: Job? = null

    init {
        if (geocoder.isAvailable()) {
            scope.launch {
                // collectLatest is the debounce. A new keystroke cancels the wait,
                // and also cancels a request that is already in flight.
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
     * Loads what an issue already says, without treating it like something a
     * person just typed, because an edit form should not geocode itself on open.
     */
    fun start(description: String, point: GeoPoint?, geocodedAddress: String?) {
        pinLookup?.cancel()
        _state.value = LocationFieldState(
            description = description,
            point = point,
            geocodedAddress = geocodedAddress,
        )
        // [typed] is left empty on purpose. Seeding it here would hand the stored
        // location to the debounce as if someone had just written it, and then an
        // edit form would move (or make up) a pin nobody asked for.
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
        // A pin dropped by hand is the truth about where this is, so leave it alone.
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
        // Best effort. The pin is worth placing whether or not the geocoder can
        // also name the spot, and the map labels it as approximate either way.
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
        /** Long enough to be an address and not just the start of one. */
        const val MIN_QUERY_LENGTH = 5

        /** A pause in typing, not a pause in thinking. */
        const val TYPING_SETTLE_MILLIS = 700L
    }
}

/** Compass's unhappy outcomes, translated into the form's terms. */
private fun GeocoderResult<*>.statusWhenNothingUsable(): GeocodingStatus = when (this) {
    GeocoderResult.NotFound -> GeocodingStatus.NoMatch
    // No geocoder on this device and no fallback configured. Say nothing instead
    // of blaming the network for a feature this build does not have.
    GeocoderResult.NotSupported -> GeocodingStatus.Idle
    else -> GeocodingStatus.Unavailable
}
