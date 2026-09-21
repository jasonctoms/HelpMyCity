package dev.helpmycity.ui

import dev.helpmycity.data.seed.ReferenceDataSeeder
import dev.helpmycity.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * One-time startup work: seed reference data, then start the sync engine.
 * Idempotent, because every platform entry point may run it and Android will run
 * it again after process death.
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
            // Seed first: the sync engine's watcher should see a populated store.
            seeder.seedIfEmpty()
            syncEngine.start(scope)
        }
    }
}
