package dev.helpmycity.ui

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Short date rendering for list rows and history entries.
 *
 * This is hand-rolled because Kotlin does not have a common-code locale
 * formatter, and an unambiguous `2026-08-09 14:32` reads the same in English and
 * Spanish anyway.
 */
@OptIn(ExperimentalTime::class)
fun formatTimestamp(epochMillis: Long, includeTime: Boolean = false): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${local.year}-${local.month.number.pad()}-${local.day.pad()}"
    return if (includeTime) "$date ${local.hour.pad()}:${local.minute.pad()}" else date
}

private fun Int.pad(): String = if (this < 10) "0$this" else toString()
