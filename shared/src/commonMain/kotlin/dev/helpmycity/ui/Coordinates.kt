package dev.helpmycity.ui

import kotlin.math.abs
import kotlin.math.round

/** Decimal places kept when a latitude or longitude is shown to someone. */
private const val COORDINATE_DECIMALS = 4

private const val COORDINATE_SCALE = 10_000.0

/**
 * A latitude or longitude, trimmed to something a person can read.
 *
 * Four decimal places is about 11 m: enough to identify a street corner, and
 * an honest claim about the precision of a finger on a phone map. Done by hand
 * because Kotlin has no common-code number formatter.
 */
fun formatCoordinate(value: Double): String {
    val scaled = round(abs(value) * COORDINATE_SCALE).toLong()
    val whole = scaled / COORDINATE_SCALE.toLong()
    val fraction = (scaled % COORDINATE_SCALE.toLong())
        .toString()
        .padStart(COORDINATE_DECIMALS, '0')
    val sign = if (value < 0) "-" else ""
    return "$sign$whole.$fraction"
}
