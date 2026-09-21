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
        // Emitted next to the bundle by webpack.config.d/maplibreWorker.js.
        installMapLibreCompose(workerUrl = "maplibre-gl-worker.mjs")
        ComposeViewport(document.body!!) {
            App()
        }
    }
}
