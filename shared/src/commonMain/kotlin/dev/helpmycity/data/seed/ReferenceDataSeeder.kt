package dev.helpmycity.data.seed

import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.deployment.CityProfile
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Writes the [CityProfile]'s reference data at startup.
 *
 * Departments and neighborhoods are seeded because the app is unusable without
 * them and, with no backend, nothing else serves them. They carry stable ids
 * from the profile, so re-seeding is idempotent and a later backend pull merges
 * onto the same rows.
 *
 * Departments are written only into an empty store, so an admin's edits
 * survive a restart. Neighborhoods have no editor, so they are replaced from
 * the profile on every start: a city that redraws its boundaries ships the new
 * set in an update, and installs pick it up without clearing their data.
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
    suspend fun seed() {
        if (departments.count() == 0) departments.upsertAll(city.departments)
        val current = city.neighborhoods.map { it.id }.toSet()
        neighborhoods.observeAll().first()
            .filter { it.id !in current }
            .forEach { neighborhoods.delete(it.id) }
        neighborhoods.upsertAll(city.neighborhoods)
    }
}
