package dev.helpmycity.ui

import dev.helpmycity.data.seed.ReferenceDataSeeder
import dev.helpmycity.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * One-time startup work. Seed the reference data, then start the sync engine.
 * This is idempotent, because every platform entry point might run it, and
 * Android runs it again after process death.
 */
@Single
class AppBootstrapper(
    private val seeder: ReferenceDataSeeder,
    private val syncEngine: SyncEngine,
) {
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            // Seed first, so the sync engine's watcher sees a populated store.
            seeder.seed()
            syncEngine.start(scope)
        }
    }
}
