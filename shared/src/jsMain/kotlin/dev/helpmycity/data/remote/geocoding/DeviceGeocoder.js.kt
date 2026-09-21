package dev.helpmycity.data.remote.geocoding

import dev.jordond.compass.geocoder.NotSupportedPlatformGeocoder
import dev.jordond.compass.geocoder.PlatformGeocoder

/** A browser has no geocoder of its own; the city profile's fallback does the work. */
actual fun deviceGeocoder(): PlatformGeocoder = NotSupportedPlatformGeocoder
