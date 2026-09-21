package dev.helpmycity.deployment

import dev.helpmycity.data.remote.external.ExternalRequestGateway
import dev.helpmycity.data.remote.external.NoExternalRequestGateway
import dev.helpmycity.data.remote.geocoding.NoGeocoding
import dev.jordond.compass.geocoder.Geocoder
import dev.helpmycity.deployment.MapSettings.Companion.OPEN_FREE_MAP_LIBERTY
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.Neighborhood

/**
 * Everything about *one city* that this app cannot know in advance.
 *
 * This is one half of the fork seam (see [BackendProvider] for the other). No
 * code outside a city profile should name a city, a department, a neighborhood
 * or a municipal website: if a fork has to edit a file inside `:shared` to run
 * in its own town, that is a bug in this interface, not in the fork.
 *
 * `:cityConfig` is the worked example. A new city edits or replaces that module
 * and passes its profile to `initKoin(city = ...)`.
 */
interface CityProfile {

    /** The city this deployment serves, as residents would name it. */
    val displayName: String

    /**
     * Departments issues can be routed to. Ids must be stable: they are stored
     * on every issue and a later backend pull merges onto the same rows.
     */
    val departments: List<Department>

    /** Neighborhoods used to tag issues, with the same stable-id requirement. */
    val neighborhoods: List<Neighborhood>

    /**
     * Shared sign-ins to advertise on the sign-in screen, for a deployment that
     * is a public demo.
     *
     * Empty by default, and empty is the answer for a real city: the notice
     * appears only when this list does not. These are real accounts on whatever
     * identity provider the deployment uses -- nothing here creates them, and
     * nothing here treats a password as a secret, because the whole point is
     * that anyone may use them.
     */
    val demoLogins: List<DemoLogin> get() = emptyList()

    /**
     * The city's own service-request system, when the app is expected to hand
     * off to one. Defaults to [NoExternalRequestGateway], which hides the
     * hand-off UI entirely.
     */
    val externalRequestSystem: ExternalRequestGateway get() = NoExternalRequestGateway

    /** Where the map opens and which basemap it draws. See [MapSettings]. */
    val map: MapSettings get() = MapSettings()

    /** How the app wears this city's face. See [CityBranding]. */
    val branding: CityBranding get() = CityBranding()

    /**
     * Address lookup for the report and edit forms, as a
     * [Compass](https://github.com/jordond/compass) geocoder. Defaults to
     * [NoGeocoding], which leaves the pin picker as the only way to put an issue
     * on the map.
     *
     * Off by default rather than on: every geocoder is somebody's metered or
     * donated capacity, and a deployment should choose to spend it. Compass
     * supplies the device geocoders and the keyed web ones; see
     * [dev.helpmycity.data.remote.geocoding.deviceGeocoder] and
     * [dev.helpmycity.data.remote.geocoding.NominatimPlatformGeocoder]
     * for the pairing that needs no signup on any target.
     */
    val geocoder: Geocoder get() = NoGeocoding
}

/**
 * The look of a city profile, as against the data in one.
 *
 * The app header is the one place a resident should be able to tell, at a
 * glance, whose app this is. A photograph of somewhere they recognize does that
 * better than a color ever will, so a deployment supplies one rather than
 * theming its way there.
 *
 * @param headerImageUrl a wide photograph drawn behind the header. Cropped to
 *   fill and darkened toward the bottom, where the wordmark and tabs sit, so
 *   pick something that reads at a glance and keeps its subject off the lower
 *   edge. Null falls back to a flat themed header. It is fetched over the
 *   network on every platform: host it somewhere you control, or at least
 *   somewhere that will not mind the traffic.
 * @param logoUrl the city's own mark, drawn in the header in place of its name.
 *   Null falls back to the name set in type. Fetched over the network like
 *   [headerImageUrl], and on the web that fetch is subject to CORS: a city's
 *   CMS usually serves logos to `<img>` tags but not to scripts, so host the
 *   copy this app loads somewhere that allows cross-origin reads.
 * @param logoIsSingleColor true when [logoUrl] is one flat color on
 *   transparency, which lets the header draw it in white straight over the
 *   photograph. False -- the safe default, and what a multi-color seal needs --
 *   sets it on a light plate instead.
 * @param tagline one line under the city name, for what this deployment is for.
 *   Null leaves the wordmark alone.
 */
data class CityBranding(
    val headerImageUrl: String? = null,
    val logoUrl: String? = null,
    val logoIsSingleColor: Boolean = false,
    val tagline: String? = null,
)

/**
 * The map half of a city profile.
 *
 * Two things about a map cannot be known by city-agnostic code: where to point
 * the camera, and whose tiles to draw. Both live here rather than in `ui/map`,
 * for the same reason departments do.
 *
 * @param styleUrl a [MapLibre style](https://maplibre.org/maplibre-style-spec/)
 *   URL. See [OPEN_FREE_MAP_LIBERTY], the default, for what to weigh before a
 *   real deployment leans on it. Whichever you pick, nothing here is
 *   secret-safe: a style URL ships to the client, so a provider key belongs in
 *   it only if that key is domain-restricted.
 * @param center where the camera starts when no issue has coordinates yet.
 *   Null means "wherever the issues are", falling back to a world view on an
 *   empty database.
 * @param defaultZoom the starting zoom for [center]. Roughly: 10 is a county,
 *   12 a city, 15 a few blocks.
 */
data class MapSettings(
    val styleUrl: String = OPEN_FREE_MAP_LIBERTY,
    val center: GeoPoint? = null,
    val defaultZoom: Double = 12.0,
) {
    companion object {
        /**
         * [OpenFreeMap](https://openfreemap.org/): OpenStreetMap data, full
         * street detail, no API key and no signup.
         *
         * The default because a fork should see a real map on first run without
         * registering with anyone. It is donation-funded rather than free-as-in-
         * contract, so a deployment that comes to depend on it should either
         * donate or self-host -- the tiles and styles are open, and OpenFreeMap
         * publishes instructions for running your own. Attribution is required
         * and MapLibre renders it from the style.
         *
         * The cheapest durable answer is a
         * [Protomaps](https://protomaps.com/) `.pmtiles` extract on object
         * storage behind a CDN: one file, no tile server, no per-request billing.
         */
        const val OPEN_FREE_MAP_LIBERTY: String = "https://tiles.openfreemap.org/styles/liberty"

        /** Gray-and-white and low-contrast, so status markers carry the color. */
        const val OPEN_FREE_MAP_POSITRON: String = "https://tiles.openfreemap.org/styles/positron"

        /** MapLibre's demo world map. No street detail; useful only as a fallback. */
        const val MAPLIBRE_DEMO: String = "https://demotiles.maplibre.org/style.json"
    }
}

/**
 * One published sign-in for a demo deployment.
 *
 * [label] says what the account is for -- "Manager", "Admin" -- so a visitor
 * can pick the role they want to try rather than guessing from the address.
 */
data class DemoLogin(
    val label: String,
    val email: String,
    val password: String,
)

/**
 * The profile a fork gets before it writes its own: no departments, no
 * neighborhoods, no city system to hand off to.
 *
 * Deliberately runnable rather than throwing -- a fresh clone should start,
 * show an empty issue list and let you submit, so the first thing a new
 * contributor sees is the app rather than a stack trace.
 */
object UnconfiguredCityProfile : CityProfile {
    override val displayName: String = "your city"
    override val departments: List<Department> = emptyList()
    override val neighborhoods: List<Neighborhood> = emptyList()
}
