package dev.helpmycity.cityconfig.supabase

import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.data.remote.IssueBackendApi
import dev.helpmycity.data.remote.PhotoBackendApi
import dev.helpmycity.data.remote.ReferenceDataBackendApi
import dev.helpmycity.deployment.BackendProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Where this deployment's Supabase project lives.
 *
 * Both values are safe to ship in a client build. The publishable key is a public
 * identifier and not a secret; what it can read and write is decided by the
 * row-level security policies in `schema.sql`, not by having the key. Never put a
 * secret or `service_role` key here, because those skip every policy.
 */
data class SupabaseConfig(
    /** Project URL, e.g. `https://abcdefgh.supabase.co`. */
    val url: String,
    /** Project publishable key, `sb_publishable_...`. */
    val publishableKey: String,
    /** The Storage bucket issue photos get uploaded to. It has to exist and be public. */
    val photoBucket: String = "issue-photos",
) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()

    companion object {
        /** Placeholder so the type can be constructed before a project exists. */
        val Unconfigured: SupabaseConfig = SupabaseConfig(url = "", publishableKey = "")
    }
}

/**
 * Oceanside's backend, which is Postgres, auth, and object storage from one
 * Supabase project.
 *
 * This is everything it takes to pick a backend, and it is meant as a sample. It
 * is one provider, wired up completely, so a fork can see what a
 * `BackendProvider` actually involves before writing its own. The four APIs below
 * are the only code that knows Supabase exists. `:shared` does not care about any
 * of it, and a fork on Firebase or a self-hosted box would write a class shaped
 * like this in its own module and not touch anything else.
 *
 * `supabase/` at the root of the repo has the SQL this expects (schema and
 * row-level security), next to the seed data and nightly reset, which only belong
 * to the public demo.
 *
 * With an [SupabaseConfig.Unconfigured] config, every part reports
 * [dev.helpmycity.data.remote.RemoteResult.NotConfigured] and sign-in falls back
 * to [dev.helpmycity.data.auth.MockAuthService], so picking this backend before
 * the project exists behaves exactly like running with no backend at all.
 *
 * @param client only pass one in if you want several backends pointed at the same
 *   session, or need to install extra Supabase plugins.
 */
class SupabaseBackend(
    val config: SupabaseConfig = SupabaseConfig.Unconfigured,
    client: SupabaseClient? = if (config.isConfigured) createClient(config) else null,
) : BackendProvider {

    override val issues: IssueBackendApi = SupabaseIssueBackendApi(client)
    override val referenceData: ReferenceDataBackendApi = SupabaseReferenceDataBackendApi(client)
    override val photos: PhotoBackendApi = SupabasePhotoBackendApi(client, config.photoBucket)
    override val auth: AuthService = SupabaseAuthService(client)

    companion object {
        fun createClient(config: SupabaseConfig): SupabaseClient =
            createSupabaseClient(config.url, config.publishableKey) {
                install(Postgrest)
                install(Auth)
                install(Storage)
            }
    }
}
