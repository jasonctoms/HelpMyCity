package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * A neighborhood a manager is assigned to.
 *
 * [boundaryGeoJson] is a GeoJSON `Polygon` or `MultiPolygon` geometry in
 * WGS 84, drawn as an outline on the issue map. It is optional: issues are
 * tagged with a neighborhood by hand, not by where their pin falls. A
 * neighborhood does not map 1:1 to a council district, precinct or census
 * tract, so those stay independent fields on [IssueLocation] rather than being
 * derived from this record.
 *
 * The set of neighborhoods is per-deployment: see
 * [dev.helpmycity.deployment.CityProfile.neighborhoods].
 */
@Serializable
data class Neighborhood(
    val id: String,
    val name: String,
    val councilDistrict: String? = null,
    val boundaryGeoJson: String? = null,
    val sync: SyncMetadata = SyncMetadata.LocalOnly,
)
