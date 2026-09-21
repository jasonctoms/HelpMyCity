package dev.helpmycity.data.remote.geocoding

import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import dev.jordond.compass.geocoder.Geocoder
import dev.jordond.compass.geocoder.NotSupportedPlatformGeocoder
import dev.jordond.compass.geocoder.PlatformGeocoder

/**
 * The geocoder built into the device -- Android's `Geocoder`, iOS's
 * `CLGeocoder` -- or nothing on the web, which has no such thing.
 *
 * Worth preferring wherever it exists: it costs nothing, needs no account,
 * spends no third party's donated capacity and is subject to no rate limit.
 * Android's is also absent on plenty of builds, so it reports its own
 * availability; pair it with something that always works using [orElse].
 */
expect fun deviceGeocoder(): PlatformGeocoder

/**
 * [this] wherever the platform provides it, [fallback] everywhere else -- the
 * device geocoder on a phone, and an HTTP one in a browser, from one expression
 * a city profile can write in common code.
 *
 * Availability is re-checked per call rather than captured, so nothing here
 * has to run at start-up.
 */
infix fun PlatformGeocoder.orElse(fallback: PlatformGeocoder): PlatformGeocoder =
    FallbackPlatformGeocoder(preferred = this, fallback = fallback)

/** A geocoder that reports itself unsupported: the form falls back to the pin picker. */
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
