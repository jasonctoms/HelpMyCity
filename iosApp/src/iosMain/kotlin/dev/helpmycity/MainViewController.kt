package dev.helpmycity

import androidx.compose.ui.window.ComposeUIViewController
import dev.helpmycity.di.initKoin
import dev.helpmycity.cityconfig.ConfiguredBackend
import dev.helpmycity.cityconfig.ConfiguredCityProfile

fun MainViewController() = ComposeUIViewController {
    // Where this build picks its city and backend. initKoin() is idempotent,
    // so SwiftUI creating the controller more than once is safe.
    initKoin(city = ConfiguredCityProfile, backend = ConfiguredBackend)
    App()
}
