package dev.helpmycity.data.remote.geocoding

import dev.helpmycity.domain.model.GeoPoint
import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place

/**
 * A [Place] as one line a resident would recognise, for
 * [dev.helpmycity.domain.model.IssueLocation.geocodedAddress].
 *
 * Compass hands back the same structured fields whichever geocoder answered, so
 * the wording of a report does not change with the device it was filed from.
 * Null when the geocoder found a point it cannot describe -- worth a pin, not
 * worth writing down.
 */
fun Place.asAddressLine(): String? = listOfNotNull(
    streetLine(),
    locality ?: subLocality ?: subAdministrativeArea,
    administrativeArea,
    postalCode,
)
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(", ")
    .ifBlank { null }

/** House number and street where both are known, and whatever names the spot otherwise. */
private fun Place.streetLine(): String? {
    val road = thoroughfare ?: street
    return when {
        road != null && subThoroughfare != null -> "$subThoroughfare $road"
        road != null -> road
        else -> name
    }
}

fun Coordinates.toGeoPoint(): GeoPoint = GeoPoint(latitude = latitude, longitude = longitude)

fun GeoPoint.toCoordinates(): Coordinates =
    Coordinates(latitude = latitude, longitude = longitude)
