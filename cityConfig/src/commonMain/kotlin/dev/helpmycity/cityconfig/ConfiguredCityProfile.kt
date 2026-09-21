package dev.helpmycity.cityconfig

import dev.helpmycity.data.remote.external.ExternalRequestGateway
import dev.helpmycity.data.remote.geocoding.GeoBoundingBox
import dev.helpmycity.data.remote.geocoding.NominatimPlatformGeocoder
import dev.helpmycity.data.remote.geocoding.deviceGeocoder
import dev.helpmycity.data.remote.geocoding.orElse
import dev.jordond.compass.geocoder.Geocoder
import dev.helpmycity.deployment.CityBranding
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.MapSettings
import dev.helpmycity.deployment.DemoLogin
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.GeoPoint
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.Neighborhood

/**
 * The city this build serves. Oceanside, CA, as configured here.
 *
 * This is the whole of what makes HelpMyCity one city's -- a list of
 * departments, a list of neighborhoods, how it looks, where its map opens, and
 * the city's own request system. Everything else in the repo is city-agnostic,
 * and the three composition roots name only this object, never the city.
 *
 * **Forking:** keep the shape, replace the contents. Ids are stored on issues
 * and merged against on a later backend pull, so pick stable ones up front and
 * do not renumber them afterwards.
 */
object ConfiguredCityProfile : CityProfile {

    override val displayName: String = "Oceanside"

    override val externalRequestSystem: ExternalRequestGateway = CityRequestGateway

    /**
     * Sunset from the Oceanside pier, by way of Visit Oceanside, under the
     * city's wordmark -- one flat color on transparency, so the header draws it
     * in white rather than on a plate.
     *
     * Both are hotlinked, which is fine for a demo and not for a deployment:
     * host copies you have the rights to. Note the logo is not served from the
     * city's own site, which sends no CORS headers and so loads on Android and
     * iOS but not in a browser, where image bytes are fetched from script.
     * Whatever a fork points at has to allow cross-origin reads.
     */
    override val branding: CityBranding = CityBranding(
        headerImageUrl = "https://visitoceanside.org/wp-content/uploads/2023/01/" +
            "Pier-Sunset-Timelapse-Nov2020-10-scaled-1.jpg",
        logoUrl = "https://thecleanenergyalliance.org/wp-content/uploads/2022/08/city-of-oceanside-logo.png",
        logoIsSingleColor = true,
        tagline = "Report it. Track it. Get it fixed. Full transparency.",
    )

    /**
     * Downtown Oceanside, near the pier.
     *
     * [MapSettings.styleUrl] is left at the default OpenFreeMap basemap. Before
     * this is the map residents actually use, read the note on
     * [MapSettings.Companion.OPEN_FREE_MAP_LIBERTY]: it is donation-funded, and
     * a city leaning on it should donate or self-host.
     */
    override val map: MapSettings = MapSettings(
        center = GeoPoint(latitude = 33.1959, longitude = -117.3795),
        defaultZoom = 13.0,
    )

    /**
     * The phone's own geocoder where there is one, and OpenStreetMap's public
     * Nominatim in a browser, which has none. The Nominatim half is bounded to
     * the city limits, so "Mission Ave" resolves to Oceanside's rather than to
     * one three states away; the device geocoders take no such hint, and are
     * trusted to know where the phone is.
     *
     * Read the note on [NominatimPlatformGeocoder] before this serves real
     * traffic: the public instance is donated capacity, and the contact details
     * below are what an admin would use to reach whoever is spending it. Android
     * and iOS never touch it.
     */
    override val geocoder: Geocoder = Geocoder(
        deviceGeocoder() orElse NominatimPlatformGeocoder(
            userAgent = "HelpMyCity/0.1 (Oceanside, CA)",
            contactEmail = "publicworks@oceansideca.org",
            viewBox = GeoBoundingBox(
                southWest = GeoPoint(latitude = 33.1495, longitude = -117.4290),
                northEast = GeoPoint(latitude = 33.2960, longitude = -117.2160),
            ),
        )
    )

    override val departments: List<Department> = listOf(
        Department(
            id = "dept-public-works",
            name = "Public Works",
            contactEmail = "publicworks@oceansideca.org",
            handlesCategories = listOf(
                IssueCategory.ROAD_SURFACE,
                IssueCategory.SIDEWALK,
                IssueCategory.DRAINAGE,
                IssueCategory.TRASH_DUMPING,
            ),
        ),
        Department(
            id = "dept-traffic-calming",
            name = "Traffic Calming",
            handlesCategories = listOf(
                IssueCategory.TRAFFIC_SAFETY,
                IssueCategory.SIGNAGE,
                IssueCategory.ADA_ACCESS,
            ),
        ),
        Department(
            id = "dept-water-utilities",
            name = "Water Utilities",
            handlesCategories = listOf(IssueCategory.WATER_UTILITIES, IssueCategory.DRAINAGE),
        ),
        Department(
            id = "dept-parks",
            name = "Parks & Recreation",
            handlesCategories = listOf(IssueCategory.PARK_MAINTENANCE, IssueCategory.GRAFFITI),
        ),
        Department(
            id = "dept-street-lighting",
            name = "Street Lighting",
            handlesCategories = listOf(IssueCategory.STREET_LIGHTING),
        ),
    )

    override val neighborhoods: List<Neighborhood> = listOf(
        Neighborhood(id = "nbhd-libby-lake", name = "Libby Lake", councilDistrict = "District 1"),
        Neighborhood(id = "nbhd-crown-heights", name = "Crown Heights", councilDistrict = "District 2"),
        Neighborhood(id = "nbhd-eastside", name = "Eastside", councilDistrict = "District 2"),
        Neighborhood(id = "nbhd-mesa-margarita", name = "Mesa Margarita", councilDistrict = "District 3"),
        Neighborhood(id = "nbhd-fire-mountain", name = "Fire Mountain", councilDistrict = "District 4"),
    )

    /**
     * The three shared sign-ins this demo publishes on its sign-in screen.
     *
     * Real accounts in the configured Supabase project, created there by hand;
     * nothing in this repo creates them and nothing treats these passwords as
     * secrets, because the point is that anyone may use them. Their roles come
     * from each account's `app_metadata`, not from the address -- step 5 of
     * `supabase/README.md` is how this demo granted them.
     *
     * A city's own profile has no override: these belong to the demo.
     */
    override val demoLogins: List<DemoLogin> = listOf(
        DemoLogin(label = "Admin", email = "admin@helpmycity.dev", password = "demo-admin-pass"),
        DemoLogin(label = "Manager", email = "manager@helpmycity.dev", password = "demo-manager-pass"),
        DemoLogin(label = "Resident", email = "resident@helpmycity.dev", password = "demo-resident-pass"),
    )
}
