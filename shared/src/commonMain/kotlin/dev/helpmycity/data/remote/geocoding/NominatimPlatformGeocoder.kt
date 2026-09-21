package dev.helpmycity.data.remote.geocoding

import dev.helpmycity.domain.model.GeoPoint
import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import dev.jordond.compass.geocoder.PlatformGeocoder
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [Nominatim](https://nominatim.org/), OpenStreetMap's own geocoder, as a
 * Compass [PlatformGeocoder].
 *
 * Compass ships device geocoders for Android and iOS and keyed HTTP ones for
 * Google, Mapbox and OpenCage; it has nothing that works on the web without an
 * account. This fills that hole, and it is the counterpart to the
 * [OpenFreeMap][dev.helpmycity.deployment.MapSettings.Companion.OPEN_FREE_MAP_LIBERTY]
 * basemap default: a fork geocodes real addresses on first run, in a browser,
 * without registering with anyone, off the same OpenStreetMap data the map
 * already draws.
 *
 * Weigh the same caveat before a real deployment leans on it. The public
 * instance is donated capacity governed by a
 * [usage policy](https://operations.osmfoundation.org/policies/nominatim/): one
 * request a second, no bulk work, and an app that identifies itself. The rate
 * limit is honored here; [userAgent] and [contactEmail] are the identification,
 * so fill them in with something a Nominatim admin could write to. A city with
 * real traffic should run its own instance and pass [baseUrl], or drop in one of
 * Compass's keyed backends.
 *
 * Errors are thrown rather than swallowed: Compass's `Geocoder` wrapper turns
 * them into `GeocoderResult.GeocodeFailed`, and an empty list into `NotFound`.
 *
 * @param userAgent sent as `User-Agent`, which the policy asks for. A browser
 *   will not let a page set that header, so [contactEmail] is what identifies
 *   the web build.
 * @param contactEmail Nominatim's `email` parameter, its documented alternative
 *   for identifying the caller. The deployment's address, not a resident's.
 * @param viewBox confines results to the city. Without it, "Main St" resolves to
 *   whichever Main Street the planet finds more important.
 * @param baseUrl the Nominatim instance. Defaults to [PUBLIC_INSTANCE].
 */
class NominatimPlatformGeocoder(
    private val userAgent: String,
    private val contactEmail: String? = null,
    private val viewBox: GeoBoundingBox? = null,
    private val baseUrl: String = PUBLIC_INSTANCE,
) : PlatformGeocoder {

    override fun isAvailable(): Boolean = true

    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val rateLimit = Mutex()
    private var lastRequest: TimeMark? = null

    /**
     * The place behind the last [forward] answer.
     *
     * [PlatformGeocoder.forward] has room only for coordinates, but Nominatim's
     * search response already carries the address, and a caller that wants to
     * know what it matched must reverse those same coordinates straight back.
     * Holding the one answer saves that round trip and a second wait on the rate
     * limit. A miss only costs the request it would have cost anyway.
     */
    private var lastForwardMatch: Pair<Coordinates, Place>? = null

    override suspend fun forward(address: String): List<Coordinates> {
        val places = search(address)
        lastForwardMatch = places.firstOrNull()?.let { it.coordinates to it }
        return places.map { it.coordinates }
    }

    override suspend fun reverse(latitude: Double, longitude: Double): List<Place> {
        lastForwardMatch
            ?.takeIf { (at, _) -> at.latitude == latitude && at.longitude == longitude }
            ?.let { (_, place) -> return listOf(place) }

        val body = get("$baseUrl/reverse") {
            parameter("lat", latitude)
            parameter("lon", longitude)
            parameter("zoom", REVERSE_DETAIL_ZOOM)
        }
        // Nominatim answers "nothing here" with an `error` field rather than a
        // status code, so a response without coordinates is the no-match signal.
        return listOfNotNull(json.decodeOrNull<NominatimPlace>(body)?.toPlace())
    }

    private suspend fun search(address: String): List<Place> {
        val body = get("$baseUrl/search") {
            parameter("q", address)
            parameter("limit", SEARCH_RESULTS)
            viewBox?.let {
                parameter("viewbox", it.asViewBoxParameter())
                parameter("bounded", 1)
            }
        }
        return json.decodeOrNull<List<NominatimPlace>>(body).orEmpty().mapNotNull { it.toPlace() }
    }

    private suspend fun get(url: String, block: HttpRequestBuilder.() -> Unit): String =
        rateLimit.withLock {
            waitOutRateLimit()
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, userAgent)
                parameter("format", "jsonv2")
                parameter("addressdetails", 1)
                contactEmail?.let { parameter("email", it) }
                block()
            }
            check(response.status.isSuccess()) { "Nominatim answered ${response.status}" }
            response.bodyAsText()
        }

    private suspend fun waitOutRateLimit() {
        val sinceLast = lastRequest?.elapsedNow()
        if (sinceLast != null && sinceLast < MIN_REQUEST_INTERVAL) {
            delay(MIN_REQUEST_INTERVAL - sinceLast)
        }
        lastRequest = TimeSource.Monotonic.markNow()
    }

    companion object {
        const val PUBLIC_INSTANCE: String = "https://nominatim.openstreetmap.org"

        /** One: the form places a single pin, and every extra result is billed to a donated server. */
        private const val SEARCH_RESULTS = 1

        /** House-number detail. Lower numbers answer with a suburb or a city. */
        private const val REVERSE_DETAIL_ZOOM = 18

        private val MIN_REQUEST_INTERVAL = 1.seconds
    }
}

/** Two opposite corners, in the order the rest of the app writes coordinates. */
data class GeoBoundingBox(val southWest: GeoPoint, val northEast: GeoPoint) {

    internal fun asViewBoxParameter(): String = listOf(
        southWest.longitude,
        southWest.latitude,
        northEast.longitude,
        northEast.latitude,
    ).joinToString(",")
}

/** Malformed JSON reads as no result; Compass reports the empty list as `NotFound`. */
private inline fun <reified T> Json.decodeOrNull(body: String): T? =
    try {
        decodeFromString<T>(body)
    } catch (malformed: Exception) {
        null
    }

@Serializable
private data class NominatimPlace(
    val lat: String? = null,
    val lon: String? = null,
    val name: String? = null,
    val address: NominatimAddress? = null,
)

/** Nominatim's `addressdetails=1` breakdown, which maps onto Compass's [Place]. */
@Serializable
private data class NominatimAddress(
    @SerialName("house_number") val houseNumber: String? = null,
    val road: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

private fun NominatimPlace.toPlace(): Place? {
    val latitude = lat?.toDoubleOrNull() ?: return null
    val longitude = lon?.toDoubleOrNull() ?: return null
    return Place(
        coordinates = Coordinates(latitude = latitude, longitude = longitude),
        name = name?.takeIf { it.isNotBlank() },
        street = address?.road,
        isoCountryCode = address?.countryCode?.uppercase(),
        country = address?.country,
        postalCode = address?.postcode,
        administrativeArea = address?.state,
        subAdministrativeArea = address?.county,
        locality = address?.city ?: address?.town ?: address?.village,
        subLocality = address?.suburb ?: address?.neighbourhood,
        thoroughfare = address?.road,
        subThoroughfare = address?.houseNumber,
    )
}
