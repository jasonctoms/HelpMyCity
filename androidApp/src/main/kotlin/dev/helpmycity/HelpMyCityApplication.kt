package dev.helpmycity

import android.app.Application
import dev.helpmycity.di.initKoin
import dev.helpmycity.cityconfig.ConfiguredBackend
import dev.helpmycity.cityconfig.ConfiguredCityProfile
import org.koin.android.ext.koin.androidContext

class HelpMyCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The fork seam. This is which city the build serves and where its data
        // lives, and nothing else in :shared cares about either one.
        initKoin(
            city = ConfiguredCityProfile,
            backend = ConfiguredBackend,
        ) {
            // Room needs a Context on Android, and Koin carries it over to the shared module.
            androidContext(this@HelpMyCityApplication)
        }
    }
}
