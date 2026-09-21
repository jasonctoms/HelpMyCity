plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Kotlin/JS rather than Kotlin/Wasm: maplibre-compose has no wasmJs artifact, and a
    // JS build also runs on browsers without WasmGC. See "Why the web target is JS" in
    // ARCHITECTURE.md.
    // ES modules for the same reason `:shared` uses them: maplibre-gl 6 is ESM-only. This is the
    // module that emits the bundle, so index.html loads it with type="module".
    js {
        useEsModules()
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            // The city this build serves; see main.kt.
            implementation(project(":cityConfig"))

            implementation(libs.compose.ui)
        }
        jsMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlin.bom))
            implementation(libs.maplibre.compose)
        }
    }
}