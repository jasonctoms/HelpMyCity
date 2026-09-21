package dev.helpmycity

import android.app.Application
import dev.helpmycity.di.initKoin
import dev.helpmycity.cityconfig.ConfiguredBackend
import dev.helpmycity.cityconfig.ConfiguredCityProfile
import org.koin.android.ext.koin.androidContext

class HelpMyCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The fork seam: which city this build serves, and where its data
        // lives. Everything else in :shared is agnostic to both.
        initKoin(
            city = ConfiguredCityProfile,
            backend = ConfiguredBackend,
        ) {
            // Room needs a Context on Android; Koin carries it to the shared module.
            androidContext(this@HelpMyCityApplication)
        }
    }
}
