package dev.helpmycity.ui

import kotlin.math.abs
import kotlin.math.round

/** Decimal places kept when a latitude or longitude is shown to someone. */
private const val COORDINATE_DECIMALS = 4

private const val COORDINATE_SCALE = 10_000.0

/**
 * A latitude or longitude, trimmed to something a person can read.
 *
 * Four decimal places is about 11 m. That is enough to pick out a street corner,
 * and it is honest about how precise a finger on a phone map really is. This is
 * done by hand because Kotlin does not have a common-code number formatter.
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
