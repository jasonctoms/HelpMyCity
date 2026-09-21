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
 * Both values are safe to ship in a client build. The publishable key is a
 * public identifier, not a secret: what it may read and write is decided by the
 * row-level security policies in `schema.sql`, not by holding the key. Never
 * put a secret or `service_role` key here -- those bypass every policy.
 */
data class SupabaseConfig(
    /** Project URL, e.g. `https://abcdefgh.supabase.co`. */
    val url: String,
    /** Project publishable key, `sb_publishable_...`. */
    val publishableKey: String,
    /** Storage bucket issue photos are uploaded to. Must exist and be public. */
    val photoBucket: String = "issue-photos",
) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()

    companion object {
        /** Placeholder so the type can be constructed before a project exists. */
        val Unconfigured: SupabaseConfig = SupabaseConfig(url = "", publishableKey = "")
    }
}

/**
 * Oceanside's backend: Postgres, auth and object storage from one Supabase
 * project.
 *
 * This is the whole cost of choosing a backend, and it is a sample: one
 * provider, wired up completely, so a fork can read what a `BackendProvider`
 * amounts to before writing its own. The four APIs below are the only code that
 * knows Supabase exists; `:shared` is agnostic to all of it, and a fork on
 * Firebase or a self-hosted box writes a class of this shape in its own module
 * and touches nothing else.
 *
 * `supabase/` at the repository root holds the SQL this expects -- schema and
 * row-level security -- alongside the seed data and nightly reset that belong to
 * the public demo and to nothing else.
 *
 * Given an [SupabaseConfig.Unconfigured] config, every part reports
 * [dev.helpmycity.data.remote.RemoteResult.NotConfigured] and
 * sign-in falls back to [dev.helpmycity.data.auth.MockAuthService],
 * so selecting this backend before the project exists behaves exactly like
 * running with no backend at all.
 *
 * @param client supply one only to point several backends at the same session,
 *   or to install extra Supabase plugins.
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
