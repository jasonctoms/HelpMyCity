package dev.helpmycity.cityconfig

import dev.helpmycity.cityconfig.supabase.SupabaseBackend
import dev.helpmycity.cityconfig.supabase.SupabaseConfig
import dev.helpmycity.cityconfig.supabase.SupabaseSecrets
import dev.helpmycity.deployment.BackendProvider

/**
 * Where this build's data lives. The other half of the fork seam, paired with
 * [ConfiguredCityProfile] at each platform entry point.
 *
 * Supabase is what this deployment runs on, and the `supabase` package beside
 * this file is the repository's worked example of a [BackendProvider] rather
 * than the shape every fork has to take -- a fork on another provider returns
 * its own implementation from here and deletes that package. `supabase/README.md`
 * documents standing up the project behind *this* build, the public demo.
 *
 * The project details are supplied by the build rather than written here, so
 * the same source produces a local-only build and a hosted one -- see
 * `supabase.url` and `supabase.publishableKey` in `cityConfig/build.gradle.kts`. With
 * neither set, which is what a fresh clone gets, this is an unconfigured
 * backend: reads and writes stay in the local database, the sync queue builds
 * without draining, and sign-in falls back to `MockAuthService`.
 *
 * Lazy so that a build with no project configured never constructs a client.
 */
val ConfiguredBackend: BackendProvider by lazy {
    SupabaseBackend(
        SupabaseConfig(
            url = SupabaseSecrets.URL,
            publishableKey = SupabaseSecrets.PUBLISHABLE_KEY,
            photoBucket = SupabaseSecrets.PHOTO_BUCKET,
        )
    )
}
