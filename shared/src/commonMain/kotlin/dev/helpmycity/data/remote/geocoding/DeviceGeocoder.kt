package dev.helpmycity.data.remote.geocoding

import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import dev.jordond.compass.geocoder.Geocoder
import dev.jordond.compass.geocoder.NotSupportedPlatformGeocoder
import dev.jordond.compass.geocoder.PlatformGeocoder

/**
 * The geocoder built into the device (Android's `Geocoder` or iOS's
 * `CLGeocoder`), or nothing on the web, since browsers do not have one.
 *
 * Use it wherever it exists. It costs nothing, needs no account, does not eat
 * into anybody's donated capacity, and has no rate limit. Plenty of Android builds
 * do not have it though, so it reports its own availability, and you should pair
 * it with something that always works using [orElse].
 */
expect fun deviceGeocoder(): PlatformGeocoder

/**
 * Uses [this] wherever the platform provides it, and [fallback] everywhere else.
 * That gets you the device geocoder on a phone and an HTTP one in a browser, from
 * one expression a city profile can write in common code.
 *
 * Availability gets checked again on every call instead of once up front, so
 * nothing here has to run at startup.
 */
infix fun PlatformGeocoder.orElse(fallback: PlatformGeocoder): PlatformGeocoder =
    FallbackPlatformGeocoder(preferred = this, fallback = fallback)

/** A geocoder that reports itself as unsupported, so the form falls back to the pin picker. */
val NoGeocoding: Geocoder = Geocoder(NotSupportedPlatformGeocoder)

private class FallbackPlatformGeocoder(
    private val preferred: PlatformGeocoder,
    private val fallback: PlatformGeocoder,
) : PlatformGeocoder {

    private val active: PlatformGeocoder
        get() = if (preferred.isAvailable()) preferred else fallback

    override fun isAvailable(): Boolean = preferred.isAvailable() || fallback.isAvailable()

    override suspend fun forward(address: String): List<Coordinates> = active.forward(address)

    override suspend fun reverse(latitude: Double, longitude: Double): List<Place> =
        active.reverse(latitude, longitude)
}
