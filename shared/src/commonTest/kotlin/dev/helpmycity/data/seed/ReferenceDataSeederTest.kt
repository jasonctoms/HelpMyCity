package dev.helpmycity.data.seed

import dev.helpmycity.data.local.InMemoryDepartmentLocalDataSource
import dev.helpmycity.data.local.InMemoryNeighborhoodLocalDataSource
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Neighborhood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceDataSeederTest {

    private object TestCity : CityProfile {
        override val displayName = "Testville"
        override val departments = listOf(Department(id = "dept-1", name = "Public Works"))
        override val neighborhoods = listOf(Neighborhood(id = "nbhd-1", name = "Northside"))
    }

    private object CitylessProfile : CityProfile {
        override val displayName = "Nowhere"
        override val departments = emptyList<Department>()
        override val neighborhoods = emptyList<Neighborhood>()
    }

    private val departments = InMemoryDepartmentLocalDataSource()
    private val neighborhoods = InMemoryNeighborhoodLocalDataSource()

    private fun seeder(city: CityProfile = TestCity) = ReferenceDataSeeder(
        city = city,
        departments = departments,
        neighborhoods = neighborhoods,
    )

    @Test
    fun referenceDataIsWrittenOnFirstRun() = runTest {
        seeder().seed()

        assertEquals(1, departments.count())
        assertEquals(1, neighborhoods.count())
    }

    @Test
    fun aProfileWithNothingToSeedWritesNothing() = runTest {
        seeder(CitylessProfile).seed()

        assertEquals(0, departments.count())
        assertEquals(0, neighborhoods.count())
    }

    @Test
    fun reseedingChangesNothing() = runTest {
        seeder().seed()
        val seededDepartments = departments.observeAll().first()
        val seededNeighborhoods = neighborhoods.observeAll().first()

        seeder().seed()

        assertEquals(seededDepartments, departments.observeAll().first())
        assertEquals(seededNeighborhoods, neighborhoods.observeAll().first())
    }

    /**
     * The guard on a populated store is "is it empty", not "does this id
     * exist", so an admin who renamed or deleted a seeded department does not
     * get it back on the next start.
     */
    @Test
    fun anEditedReferenceRowSurvivesTheNextStart() = runTest {
        seeder().seed()
        val renamed = departments.observeAll().first()
            .single()
            .copy(name = "Streets and Sidewalks")
        departments.upsertAll(listOf(renamed))

        seeder().seed()

        assertEquals(listOf(renamed), departments.observeAll().first())
    }

    @Test
    fun neighborhoodsFollowTheProfile() = runTest {
        neighborhoods.upsertAll(listOf(Neighborhood(id = "nbhd-old", name = "Retired")))

        seeder().seed()

        assertEquals(TestCity.neighborhoods, neighborhoods.observeAll().first())
    }
}
