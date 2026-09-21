package dev.helpmycity.domain.model

import kotlinx.serialization.Serializable

/**
 * A city department an issue can be routed to.
 *
 * The set of departments is per-deployment: see
 * [dev.helpmycity.deployment.CityProfile.departments].
 */
@Serializable
data class Department(
    val id: String,
    val name: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    /** Categories this department typically handles, used to suggest a default. */
    val handlesCategories: List<IssueCategory> = emptyList(),
    val sync: SyncMetadata = SyncMetadata.LocalOnly,
)
