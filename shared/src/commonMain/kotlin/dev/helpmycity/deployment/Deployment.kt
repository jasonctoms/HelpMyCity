package dev.helpmycity.deployment

/**
 * Holds the two choices a fork makes, so the object graph can read them back.
 *
 * `di/AppModule.kt` binds [CityProfile], [BackendProvider], and everything they
 * expose out of this holder. The extra step is needed because the Koin compiler
 * plugin validates the graph inside `:shared` at compile time, so every binding
 * has to be declared there, but the values come from modules that `:shared` is not
 * allowed to depend on.
 *
 * It gets set once, at the platform entry point, by [dev.helpmycity.di.initKoin].
 * App code should inject [CityProfile] or [BackendProvider] instead of reading this.
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
