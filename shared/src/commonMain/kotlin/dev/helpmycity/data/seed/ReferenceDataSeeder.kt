package dev.helpmycity.data.seed

import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.deployment.CityProfile
import org.koin.core.annotation.Single

/**
 * Writes the [CityProfile]'s reference data on first run.
 *
 * Departments and neighborhoods are seeded because the app is unusable without
 * them and, with no backend, nothing else serves them. They carry stable ids
 * from the profile, so re-seeding is idempotent and a later backend pull merges
 * onto the same rows.
 *
 * Issues are deliberately **not** seeded here. A locally written issue is
 * queued for upload, so seeding one would have every install push its own copy
 * of the same sample to a shared backend. A deployment that wants example
 * reports puts them in the backend, where they are written once -- see
 * `supabase/seed.sql`.
 *
 * The city supplies the contents; this class only decides *when* to write them.
 * Nothing city-specific belongs in this file.
 */
@Single
class ReferenceDataSeeder(
    private val city: CityProfile,
    private val departments: DepartmentLocalDataSource,
    private val neighborhoods: NeighborhoodLocalDataSource,
) {
    suspend fun seedIfEmpty() {
        if (departments.count() == 0) departments.upsertAll(city.departments)
        if (neighborhoods.count() == 0) neighborhoods.upsertAll(city.neighborhoods)
    }
}
