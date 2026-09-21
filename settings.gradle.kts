rootProject.name = "HelpMyCity"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// The forkable core, plus the three platform composition roots.
include(":shared")
include(":sqliteWasmWorker")
include(":androidApp")
include(":iosApp")
include(":webApp")

// The city this repo is configured for. A fork edits or replaces this module.
include(":cityConfig")
