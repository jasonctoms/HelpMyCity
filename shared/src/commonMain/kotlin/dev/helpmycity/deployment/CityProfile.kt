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
 * Everything about _one city_ that this app has no way of knowing ahead of time.
 *
 * This is one half of the fork seam ([BackendProvider] is the other half). No code
 * outside of a city profile should name a city, a department, a neighborhood, or a
 * municipal website. If a fork has to edit a file inside `:shared` just to run in its
 * own town, that is a bug in this interface and not in the fork.
 *
 * `:cityConfig` is the worked example. A new city edits or replaces that module and
 * passes its profile to `initKoin(city = ...)`.
 */
interface CityProfile {

    /** The city this deployment serves, the way residents would say it. */
    val displayName: String

    /**
     * Departments that issues can be routed to. Ids have to be stable, because they
     * are stored on every issue and later backend pulls merge onto the same rows.
     */
    val departments: List<Department>

    /** Neighborhoods used to tag issues. Same deal with stable ids. */
    val neighborhoods: List<Neighborhood>

    /**
     * Shared sign-ins to show on the sign-in screen, for a deployment that is a
     * public demo.
     *
     * This is empty by default, and empty is the right answer for a real city; the
     * notice only shows up when the list has something in it. These are real accounts
     * on whatever identity provider the deployment uses. Nothing here creates them,
     * and nothing here treats a password as a secret, since the whole point is that
     * anyone can use them.
     */
    val demoLogins: List<DemoLogin> get() = emptyList()

    /**
     * The city's own service-request system, if the app is supposed to hand issues
     * off to one. Defaults to [NoExternalRequestGateway], which hides the hand-off UI
     * completely.
     */
    val externalRequestSystem: ExternalRequestGateway get() = NoExternalRequestGateway

    /** Where the map starts and which basemap it draws. See [MapSettings]. */
    val map: MapSettings get() = MapSettings()

    /** How the app takes on this city's look. See [CityBranding]. */
    val branding: CityBranding get() = CityBranding()

    /**
     * Address lookup for the report and edit forms, as a
     * [Compass](https://github.com/jordond/compass) geocoder. Defaults to
     * [NoGeocoding], which leaves the pin picker as the only way to put an issue on
     * the map.
     *
     * It is off by default because every geocoder is running on somebody's metered or
     * donated capacity, and a deployment should decide for itself to spend it. Compass
     * supplies the device geocoders and the keyed web ones. See
     * [dev.helpmycity.data.remote.geocoding.deviceGeocoder] and
     * [dev.helpmycity.data.remote.geocoding.NominatimPlatformGeocoder] for the combo
     * that does not need a signup on any target.
     */
    val geocoder: Geocoder get() = NoGeocoding
}

/**
 * The look of a city profile, as opposed to its data.
 *
 * The app header is the one place a resident should be able to tell at a glance whose
 * app this is. A photo of somewhere they recognize does that a lot better than a color
 * ever could, so a deployment supplies one instead of trying to theme its way there.
 *
 * @param headerImageUrl a wide photo drawn behind the header. It gets cropped to fill
 *   and darkened toward the bottom (where the wordmark and tabs sit), so pick
 *   something that reads at a glance and keeps its subject away from the bottom edge.
 *   Null falls back to a flat themed header. It is fetched over the network on every
 *   platform, so host it somewhere you control, or at least somewhere that will not
 *   mind the traffic.
 * @param logoUrl the city's own logo, drawn in the header in place of its name. Null
 *   falls back to the name in plain type. It is fetched over the network just like
 *   [headerImageUrl], and on the web that fetch is subject to CORS. A city's CMS
 *   usually serves logos to `<img>` tags but not to scripts, so host the copy this app
 *   loads somewhere that allows cross-origin reads.
 * @param logoIsSingleColor true when [logoUrl] is one flat color on a transparent
 *   background, which lets the header draw it in white right on top of the photo.
 *   False (the safe default, and what a multi-color seal needs) puts it on a light
 *   plate instead.
 * @param tagline one line under the city name saying what this deployment is for.
 *   Null leaves the wordmark by itself.
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
 * There are two things about a map that city-agnostic code can't know: where to point
 * the camera, and whose tiles to draw. Both of them live here instead of in `ui/map`,
 * for the same reason departments do.
 *
 * @param styleUrl a [MapLibre style](https://maplibre.org/maplibre-style-spec/) URL.
 *   See [OPEN_FREE_MAP_LIBERTY] (the default) for what to think about before a real
 *   deployment relies on it. Whatever you pick, nothing here is safe for secrets; a
 *   style URL ships to the client, so a provider key should only go in it if that key
 *   is domain-restricted.
 * @param center where the camera starts when no issue has coordinates yet. Null means
 *   "wherever the issues are", and falls back to a world view on an empty database.
 * @param defaultZoom the starting zoom for [center]. Roughly, 10 is a county, 12 is a
 *   city, and 15 is a few blocks.
 * @param boundarySource where the outlines from [Neighborhood.boundaryGeoJson] came
 *   from, shown under the map. Null shows nothing, so set it whenever any neighborhood
 *   has a boundary.
 */
data class MapSettings(
    val styleUrl: String = OPEN_FREE_MAP_LIBERTY,
    val center: GeoPoint? = null,
    val defaultZoom: Double = 12.0,
    val boundarySource: String? = null,
) {
    companion object {
        /**
         * [OpenFreeMap](https://openfreemap.org/), which is OpenStreetMap data with
         * full street detail, no API key, and no signup.
         *
         * It is the default because a fork should get a real map on its first run
         * without having to register with anybody. It is funded by donations though,
         * not free in the contract sense, so a deployment that ends up depending on
         * it should either donate or self-host. The tiles and styles are open, and
         * OpenFreeMap publishes instructions for running your own. Attribution is
         * required, and MapLibre renders it from the style.
         *
         * The cheapest long-term option is a [Protomaps](https://protomaps.com/)
         * `.pmtiles` extract on object storage behind a CDN. That is one file, no tile
         * server, and no per-request billing.
         */
        const val OPEN_FREE_MAP_LIBERTY: String = "https://tiles.openfreemap.org/styles/liberty"

        /** Gray, white, and low-contrast, so the status markers are where the color is. */
        const val OPEN_FREE_MAP_POSITRON: String = "https://tiles.openfreemap.org/styles/positron"

        /** MapLibre's demo world map. It has no street detail, so it is only really useful as a fallback. */
        const val MAPLIBRE_DEMO: String = "https://demotiles.maplibre.org/style.json"
    }
}

/**
 * One published sign-in for a demo deployment.
 *
 * [label] says what the account is for ("Manager", "Admin"), so a visitor can pick
 * the role they want to try instead of guessing from the email address.
 */
data class DemoLogin(
    val label: String,
    val email: String,
    val password: String,
)

/**
 * The profile a fork gets before it writes its own. No departments, no neighborhoods,
 * and no city system to hand off to.
 *
 * This runs instead of throwing on purpose. A fresh clone should start up, show an
 * empty issue list, and let you submit something, so the first thing a new contributor
 * sees is the app and not a stack trace.
 */
object UnconfiguredCityProfile : CityProfile {
    override val displayName: String = "your city"
    override val departments: List<Department> = emptyList()
    override val neighborhoods: List<Neighborhood> = emptyList()
}
