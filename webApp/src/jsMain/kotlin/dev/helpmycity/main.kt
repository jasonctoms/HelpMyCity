package dev.helpmycity

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.helpmycity.di.initKoin
import dev.helpmycity.cityconfig.ConfiguredBackend
import dev.helpmycity.cityconfig.ConfiguredCityProfile
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady
import org.maplibre.compose.browser.installMapLibreCompose

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin(city = ConfiguredCityProfile, backend = ConfiguredBackend)
    onWasmReady {
        installMapLibreCompose()
        ComposeViewport(document.body!!) {
            App()
        }
    }
}
