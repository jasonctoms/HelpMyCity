import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

/**
 * Oceanside, CA on Supabase. This is the one module a fork edits.
 *
 * It holds both halves of what makes the app _this_ city's app: the
 * `CityProfile` (departments, neighborhoods, and the My Oceanside hand-off) and
 * the `BackendProvider` Oceanside runs on (`supabase/`). Nothing under `:shared`
 * depends on it, and if you drop it you still have a working (if unconfigured)
 * app.
 *
 * Supabase is what this deployment picked, and it is the repo's one worked
 * example, but it is not a requirement. A fork that implements `BackendProvider`
 * against something else just replaces the `supabase` package and the settings
 * below with its own.
 *
 * There is no Compose, no Room, and no Koin compiler plugin here, so replacing
 * this module means filling in some data classes and a connection string instead
 * of having to learn the build.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Where this build's Supabase project details come from, most specific first: a
 * CI environment variable, then `-P` on the command line, then
 * `local.properties`, then a default. This is specific to this backend, and
 * another provider's module can get whatever it needs however it wants.
 *
 * Blank is a supported answer, and it is what a fresh clone gets. `SupabaseConfig`
 * reports itself as unconfigured, every API returns `NotConfigured`, and sign-in
 * falls back to `MockAuthService`, so the app runs local-only exactly like it
 * does with no backend at all.
 *
 * None of this is secret. The publishable key is a public identifier, and what it
 * can read and write is decided by the row-level security policies in
 * `schema.sql`. It lives in `local.properties` to keep it out of version control
 * and easy to swap per environment, not because knowing it gets you anything.
 */
val localProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText.map { text ->
    Properties().apply { load(StringReader(text)) }
}

fun supabaseSetting(key: String, environmentVariable: String, default: String) =
    providers.environmentVariable(environmentVariable)
        .orElse(providers.gradleProperty(key))
        .orElse(localProperties.map { it.getProperty(key).orEmpty() })
        .map(String::trim)
        .filter(String::isNotEmpty)
        .orElse(default)

val supabaseUrl = supabaseSetting("supabase.url", "SUPABASE_URL", "")
val supabasePublishableKey =
    supabaseSetting("supabase.publishableKey", "SUPABASE_PUBLISHABLE_KEY", "")
val supabasePhotoBucket =
    supabaseSetting("supabase.photoBucket", "SUPABASE_PHOTO_BUCKET", "issue-photos")

val generateSupabaseSecrets = tasks.register("generateSupabaseSecrets") {
    description = "Write the configured Supabase project details into the build."
    val url = supabaseUrl
    val publishableKey = supabasePublishableKey
    val photoBucket = supabasePhotoBucket
    val outputDir = layout.buildDirectory.dir("generated/supabase/commonMain/kotlin")
    inputs.property("url", url)
    inputs.property("publishableKey", publishableKey)
    inputs.property("photoBucket", photoBucket)
    outputs.dir(outputDir)
    doLast {
        fun literal(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val file = outputDir.get()
            .file("dev/helpmycity/cityconfig/supabase/SupabaseSecrets.kt")
            .asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            // Generated from the `supabase.*` build settings. Do not edit.
            package dev.helpmycity.cityconfig.supabase

            internal object SupabaseSecrets {
                const val URL: String = "${literal(url.get())}"
                const val PUBLISHABLE_KEY: String = "${literal(publishableKey.get())}"
                const val PHOTO_BUCKET: String = "${literal(photoBucket.get())}"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    // JS instead of Wasm on the web, to match `:shared` (see "Why the web target is JS"
    // in ARCHITECTURE.md).
    // ES modules for the same reason `:shared` uses them, since GL JS 6 is ESM-only.
    js {
        useEsModules()
        browser()
    }

    android {
        namespace = "dev.helpmycity.cityconfig"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseSecrets)
        }

        commonMain.dependencies {
            // The only dependency a deployment module needs.
            api(project(":shared"))

            // supabase-kt brings its own Ktor client, and each target supplies the engine below.
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.storage)
        }

        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jsMain.dependencies { implementation(libs.ktor.client.js) }
    }
}
