package dev.helpmycity

import androidx.compose.ui.window.ComposeUIViewController
import dev.helpmycity.di.initKoin
import dev.helpmycity.cityconfig.ConfiguredBackend
import dev.helpmycity.cityconfig.ConfiguredCityProfile

fun MainViewController() = ComposeUIViewController {
    // This is where the build picks its city and backend. initKoin() is
    // idempotent, so it is fine if SwiftUI creates the controller more than once.
    initKoin(city = ConfiguredCityProfile, backend = ConfiguredBackend)
    App()
}
