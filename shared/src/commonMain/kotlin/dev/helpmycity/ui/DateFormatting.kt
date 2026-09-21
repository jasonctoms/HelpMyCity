package dev.helpmycity.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Short date rendering for list rows and history entries.
 *
 * Hand-rolled because Kotlin has no common-code locale formatter, and an
 * unambiguous `2026-08-09 14:32` reads the same in English and Spanish.
 *
 * Uses `kotlinx.datetime.Instant` rather than `kotlin.time.Instant`: the
 * interop between the two arrived in kotlinx-datetime 0.7, and this project is
 * pinned to 0.6.2 for supabase-kt -- see `gradle/libs.versions.toml`.
 */
fun formatTimestamp(epochMillis: Long, includeTime: Boolean = false): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${local.year}-${local.monthNumber.pad()}-${local.dayOfMonth.pad()}"
    return if (includeTime) "$date ${local.hour.pad()}:${local.minute.pad()}" else date
}

private fun Int.pad(): String = if (this < 10) "0$this" else toString()
