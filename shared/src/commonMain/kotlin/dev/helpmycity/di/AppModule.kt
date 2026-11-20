package dev.helpmycity.di

import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.ReferenceDataBackendApi
import dev.helpmycity.data.remote.external.ExternalRequestGateway
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.Deployment
import dev.jordond.compass.geocoder.Geocoder
import dev.helpmycity.domain.util.IdGenerator
import dev.helpmycity.domain.util.SystemTimeProvider
import dev.helpmycity.domain.util.TimeProvider
import dev.helpmycity.domain.util.UuidIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * The common object graph.
 *
 * Repositories, view models, the sync engine, and the seeder get picked up by
 * [ComponentScan] from their annotations, so adding a class does not mean editing
 * this file. What is left here is the fork seam. Every binding below reads out of
 * [Deployment], so picking a city or a backend happens at the platform entry point
 * and not in here.
 */
@Module
@ComponentScan("dev.helpmycity")
class AppModule {

    @Single
    fun idGenerator(): IdGenerator = UuidIdGenerator

    @Single
    fun timeProvider(): TimeProvider = SystemTimeProvider

    /**
     * Lives as long as the process does, for the singletons that need to keep
     * watching something (like [dev.helpmycity.data.session.DefaultUserSession]).
     * This is not a screen's scope. A session that stopped tracking who was signed
     * in whenever a screen went away would be worse than not tracking it at all.
     */
    @Single
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- The fork seam -----------------------------------------------------
    // See deployment/CityProfile.kt and deployment/BackendProvider.kt.

    @Single
    fun cityProfile(): CityProfile = Deployment.city

    @Single
    fun externalRequestGateway(): ExternalRequestGateway = Deployment.city.externalRequestSystem

    @Single
    fun geocoder(): Geocoder = Deployment.city.geocoder

    @Single
    fun issueBackendApi(): IssueBackendApi = Deployment.backend.issues

    @Single
    fun referenceDataBackendApi(): ReferenceDataBackendApi = Deployment.backend.referenceData

    @Single
    fun photoBackendApi(): PhotoBackendApi = Deployment.backend.photos

    @Single
    fun authService(): AuthService = Deployment.backend.auth
}
