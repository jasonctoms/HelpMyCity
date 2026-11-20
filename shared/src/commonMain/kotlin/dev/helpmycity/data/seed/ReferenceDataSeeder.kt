package dev.helpmycity.data.seed

import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.deployment.CityProfile
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Writes the [CityProfile]'s reference data at startup.
 *
 * Departments and neighborhoods get seeded because the app is pretty useless
 * without them, and with no backend, nothing else is going to provide them. They
 * have stable ids from the profile, so seeding again is idempotent, and a later
 * backend pull merges onto the same rows.
 *
 * Departments are only written into an empty store, so an admin's edits survive
 * a restart. Neighborhoods do not have an editor, so they get replaced from the
 * profile on every start. If a city redraws its boundaries, it ships the new set
 * in an update, and installs pick it up without clearing their data.
 *
 * Issues are intentionally **not** seeded here. A locally written issue gets
 * queued for upload, so seeding one would make every install push its own copy of
 * the same sample to a shared backend. A deployment that wants example reports
 * should put them in the backend, where they only get written once (see
 * `supabase/seed.sql`).
 *
 * The city supplies the contents, and this class only decides _when_ to write
 * them. Nothing city-specific belongs in this file.
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
