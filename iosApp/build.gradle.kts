/**
 * The iOS composition root, and the module Xcode builds the `Shared` framework
 * from -- the peer of `:androidApp` and `:webApp`.
 *
 * It exists so the choice of city and backend is made here, next to the other
 * two entry points, rather than inside `:shared`. `:shared` cannot make that
 * choice: it must not depend on `cityConfig/`.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // Swift does `import Shared`; keep this stable.
            baseName = "Shared"
            isStatic = true
            // Re-exported so Swift can reach the app's own types if it needs to.
            export(project(":shared"))
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(project(":shared"))

            // The city this build serves; see MainViewController.kt.
            implementation(project(":cityConfig"))

            implementation(libs.compose.ui)
        }
    }
}
