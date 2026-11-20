package dev.helpmycity.di

import dev.helpmycity.deployment.BackendProvider
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.Deployment
import dev.helpmycity.deployment.LocalOnlyBackend
import dev.helpmycity.deployment.UnconfiguredCityProfile
import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinApplication as KoinApplicationRoot
import org.koin.plugin.module.dsl.startKoin

/**
 * Root of the object graph. The Koin compiler plugin validates the whole graph at
 * this entry point at compile time, so a missing binding fails the build instead
 * of crashing the screen that needed it.
 */
@KoinApplicationRoot(modules = [AppModule::class])
class HelpMyCityKoinApplication

/**
 * Called once per platform entry point (Android `Application`, iOS
 * `MainViewController`, and web `main`). It is safe to call more than once; the
 * second call gets ignored, so hot reload and Android activity recreation do not
 * blow up.
 *
 * [city] and [backend] are the whole fork seam. Both default to something that
 * runs with zero configuration, so a fresh clone of this repo starts without
 * picking anything. `:cityConfig` has a working implementation of each.
 *
 * [configure] runs before the modules are loaded, which is what lets Android
 * pass its `Context` in via `androidContext(...)`.
 */
fun initKoin(
    city: CityProfile = UnconfiguredCityProfile,
    backend: BackendProvider = LocalOnlyBackend,
    configure: KoinApplication.() -> Unit = {},
) {
    if (koinStarted) return
    koinStarted = true
    // This has to happen before startKoin, because AppModule's bindings read out of the holder.
    Deployment.install(city, backend)
    startKoin<HelpMyCityKoinApplication> {
        configure()
        modules(dataModule, platformModule())
    }
}

private var koinStarted = false
