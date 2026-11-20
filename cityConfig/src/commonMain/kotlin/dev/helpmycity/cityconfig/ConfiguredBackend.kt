package dev.helpmycity.cityconfig

import dev.helpmycity.cityconfig.supabase.SupabaseBackend
import dev.helpmycity.cityconfig.supabase.SupabaseConfig
import dev.helpmycity.cityconfig.supabase.SupabaseSecrets
import dev.helpmycity.deployment.BackendProvider

/**
 * Where this build's data lives. This is the other half of the fork seam, and it
 * gets paired with [ConfiguredCityProfile] at each platform entry point.
 *
 * Supabase is what this deployment runs on. The `supabase` package next to this
 * file is the repo's worked example of a [BackendProvider], not the shape every
 * fork has to follow. A fork on another provider returns its own implementation
 * from here and deletes that package. `supabase/README.md` walks through setting
 * up the project behind _this_ build, which is the public demo.
 *
 * The project details come from the build instead of being written here, so the
 * same source can produce either a local-only build or a hosted one (see
 * `supabase.url` and `supabase.publishableKey` in `cityConfig/build.gradle.kts`).
 * With neither one set, which is what a fresh clone gets, this is an unconfigured
 * backend. Reads and writes stay in the local database, the sync queue builds up
 * without draining, and sign-in falls back to `MockAuthService`.
 *
 * It is lazy so that a build with no project configured never creates a client.
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
