package dev.helpmycity.ui.issues

import dev.helpmycity.domain.model.GeoPoint
import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import dev.jordond.compass.geocoder.Geocoder
import dev.jordond.compass.geocoder.NotSupportedPlatformGeocoder
import dev.jordond.compass.geocoder.PlatformGeocoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two rules the report form and the edit form share: a pin does not
 * overwrite a person's words, and typing does not move a pin they placed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationFieldTest {

    private val pier = GeoPoint(latitude = 33.1941, longitude = -117.3846)
    private val pierAddress = "Pacific Street, Seaside, California, 92054"

    private fun place(point: GeoPoint) = Place(
        coordinates = Coordinates(point.latitude, point.longitude),
        name = "Harbor Pier",
        street = "Pacific Street",
        isoCountryCode = "US",
        country = "United States",
        postalCode = "92054",
        administrativeArea = "California",
        subAdministrativeArea = "San Diego County",
        locality = null,
        subLocality = "Seaside",
        thoroughfare = "Pacific Street",
        subThoroughfare = null,
    )

    /** Answers with [answer] whatever it is asked, so a moved pin shows up in the state. */
    private inner class FixedGeocoder(private val answer: GeoPoint?) : PlatformGeocoder {
        override fun isAvailable(): Boolean = true
        override suspend fun forward(address: String): List<Coordinates> =
            listOfNotNull(answer?.let { Coordinates(it.latitude, it.longitude) })

        override suspend fun reverse(latitude: Double, longitude: Double): List<Place> =
            listOfNotNull(answer?.let { place(GeoPoint(latitude, longitude)) })
    }

    /**
     * The test scheduler has to reach all the way down: Compass runs lookups on
     * `Dispatchers.Default` unless told otherwise, and virtual time cannot
     * advance a real thread pool. The field's own scope is a plain one on the
     * same scheduler rather than `backgroundScope`, whose coroutines
     * `advanceUntilIdle` does not run.
     */
    private fun TestScope.locationField(platform: PlatformGeocoder): LocationField {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LocationField(Geocoder(platform, dispatcher), CoroutineScope(dispatcher))
    }

    private fun TestScope.locationField(answer: GeoPoint?) = locationField(FixedGeocoder(answer))

    @Test
    fun `a dropped pin fills an empty location field`() = runTest {
        val field = locationField(pier)

        field.onPointChange(pier)
        advanceUntilIdle()

        assertEquals(pierAddress, field.state.value.description)
        assertEquals(pierAddress, field.state.value.geocodedAddress)
        assertFalse(field.state.value.isPointApproximate)
    }

    @Test
    fun `a dropped pin leaves the reporter's own words alone`() = runTest {
        val field = locationField(pier)

        field.onDescriptionChange("Libby Lake Park frontage")
        field.onPointChange(pier)
        advanceUntilIdle()

        assertEquals("Libby Lake Park frontage", field.state.value.description)
        // Still recorded: the issue carries both what they wrote and where it is.
        assertEquals(pierAddress, field.state.value.geocodedAddress)
    }

    @Test
    fun `typing a location places an approximate pin`() = runTest {
        val field = locationField(pier)

        field.onDescriptionChange("Harbor Pier")
        advanceUntilIdle()

        assertEquals(pier, field.state.value.point)
        assertTrue(field.state.value.isPointApproximate)
        assertEquals("Harbor Pier", field.state.value.description)
        assertEquals(pierAddress, field.state.value.geocodedAddress)
    }

    @Test
    fun `typing a location does not move a pin dropped by hand`() = runTest {
        val elsewhere = GeoPoint(latitude = 33.2000, longitude = -117.3000)
        val field = locationField(pier)

        field.onPointChange(elsewhere)
        advanceUntilIdle()
        field.onDescriptionChange("Harbor Pier")
        advanceUntilIdle()

        assertEquals(elsewhere, field.state.value.point)
        assertFalse(field.state.value.isPointApproximate)
    }

    @Test
    fun `a location the geocoder does not know says so`() = runTest {
        val field = locationField(answer = null)

        field.onDescriptionChange("Nowhere at all")
        advanceUntilIdle()

        assertNull(field.state.value.point)
        assertEquals(GeocodingStatus.NoMatch, field.state.value.status)
    }

    @Test
    fun `an unreachable geocoder asks for patience rather than for different words`() = runTest {
        val offline = object : PlatformGeocoder {
            override fun isAvailable(): Boolean = true
            override suspend fun forward(address: String): List<Coordinates> =
                error("no route to host")

            override suspend fun reverse(latitude: Double, longitude: Double): List<Place> =
                error("no route to host")
        }
        val field = locationField(offline)

        field.onDescriptionChange("Harbor Pier")
        advanceUntilIdle()

        assertNull(field.state.value.point)
        assertEquals(GeocodingStatus.Unavailable, field.state.value.status)
    }

    @Test
    fun `without a geocoder the form stays a pin picker`() = runTest {
        val field = locationField(NotSupportedPlatformGeocoder)

        field.onDescriptionChange("Harbor Pier")
        field.onPointChange(pier)
        advanceUntilIdle()

        assertEquals(pier, field.state.value.point)
        assertNull(field.state.value.geocodedAddress)
        assertEquals(GeocodingStatus.Idle, field.state.value.status)
    }

    @Test
    fun `an issue opened for editing is not geocoded on arrival`() = runTest {
        val field = locationField(pier)

        field.start(
            description = "N. River Rd (Montecito -> Redondo)",
            point = null,
            geocodedAddress = null,
        )
        advanceUntilIdle()

        // The manager has typed nothing, so nothing was looked up and no pin
        // appeared under an issue that never had one.
        assertNull(field.state.value.point)
        assertEquals("N. River Rd (Montecito -> Redondo)", field.state.value.description)
        assertEquals(GeocodingStatus.Idle, field.state.value.status)
    }
}
