/**
 * The iOS composition root, and the module Xcode builds the `Shared` framework
 * from. It is the iOS equivalent of `:androidApp` and `:webApp`.
 *
 * It exists so the city and backend get picked here, next to the other two entry
 * points, instead of inside `:shared`. `:shared` can't make that choice, because
 * it is not allowed to depend on `cityConfig/`.
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
            // Swift does `import Shared`, so keep this stable.
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
