package dev.helpmycity.domain.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ids are generated on the client, not the server: an offline-first app has to
 * be able to create a record with a stable primary key while it has no network,
 * and a v4 UUID is collision-safe enough to merge straight into the backend.
 */
fun interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

/**
 * Injected rather than called statically so tests can pin "now" and assert on
 * timestamps and sync ordering.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

@OptIn(ExperimentalTime::class)
object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
