package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * A neighborhood a manager is assigned to.
 *
 * [boundaryGeoJson] is a GeoJSON `Polygon` or `MultiPolygon` geometry in WGS 84,
 * and it gets drawn as an outline on the issue map. It is optional, since issues
 * are tagged with a neighborhood by hand and not by where their pin lands. A
 * neighborhood does not line up 1:1 with a council district, precinct, or census
 * tract, so those stay as separate fields on [IssueLocation] instead of being
 * derived from this record.
 *
 * Each deployment has its own set of neighborhoods (see
 * [dev.helpmycity.deployment.CityProfile.neighborhoods]).
 */
@Serializable
data class Neighborhood(
    val id: String,
    val name: String,
    val councilDistrict: String? = null,
    val boundaryGeoJson: String? = null,
    val sync: SyncMetadata = SyncMetadata.LocalOnly,
)
