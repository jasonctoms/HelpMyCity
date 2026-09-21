package dev.helpmycity.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinatesTest {

    @Test
    fun `trims the long tail a map tap produces`() {
        assertEquals("33.1957", formatCoordinate(33.19566058836561))
        assertEquals("-117.3791", formatCoordinate(-117.37906974610792))
    }

    @Test
    fun `keeps four places for whole and short values`() {
        assertEquals("33.0000", formatCoordinate(33.0))
        assertEquals("-117.3000", formatCoordinate(-117.3))
        assertEquals("0.0000", formatCoordinate(0.0))
    }

    /** A borrowed leading digit is the easy thing to get wrong building this by hand. */
    @Test
    fun `pads fractions that lose leading zeros`() {
        assertEquals("33.0001", formatCoordinate(33.00009))
        assertEquals("33.0100", formatCoordinate(33.01))
    }

    @Test
    fun `rounds rather than truncates`() {
        assertEquals("33.1235", formatCoordinate(33.12345678))
        assertEquals("-117.1235", formatCoordinate(-117.12345678))
    }
}
