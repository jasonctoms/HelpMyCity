package dev.helpmycity.data.remote.geocoding

import dev.jordond.compass.geocoder.NotSupportedPlatformGeocoder
import dev.jordond.compass.geocoder.PlatformGeocoder

/** A browser does not have a geocoder of its own, so the city profile's fallback does the work. */
actual fun deviceGeocoder(): PlatformGeocoder = NotSupportedPlatformGeocoder
