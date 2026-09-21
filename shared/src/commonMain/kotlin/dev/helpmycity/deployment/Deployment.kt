package dev.helpmycity.deployment

/**
 * Holds the two choices a fork makes, for the object graph to read back.
 *
 * `di/AppModule.kt` binds [CityProfile], [BackendProvider] and everything they
 * expose out of this holder. The indirection is necessary because the Koin
 * compiler plugin validates the graph inside `:shared` at compile time, so
 * every binding has to be declared there -- while the values come from modules
 * `:shared` must not depend on.
 *
 * Set once, at the platform entry point, by
 * [dev.helpmycity.di.initKoin]. App code injects [CityProfile] or
 * [BackendProvider] rather than reading this.
 */
object Deployment {

    var city: CityProfile = UnconfiguredCityProfile
        private set

    var backend: BackendProvider = LocalOnlyBackend
        private set

    internal fun install(city: CityProfile, backend: BackendProvider) {
        this.city = city
        this.backend = backend
    }
}
