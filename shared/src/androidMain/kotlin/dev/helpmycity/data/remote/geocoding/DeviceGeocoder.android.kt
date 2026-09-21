package dev.helpmycity.data.remote.geocoding

import dev.jordond.compass.geocoder.PlatformGeocoder
import dev.jordond.compass.geocoder.mobile.MobilePlatformGeocoder

actual fun deviceGeocoder(): PlatformGeocoder = MobilePlatformGeocoder()
