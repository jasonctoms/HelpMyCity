package dev.helpmycity.di

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.helpmycity.data.local.LocalStore
import dev.helpmycity.data.local.database.HelpMyCityDatabase
import dev.helpmycity.data.local.database.RoomLocalStore
import dev.helpmycity.data.local.database.buildHelpMyCityDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // androidContext() comes from the Context handed to startKoin by
    // HelpMyCityApplication, so nothing here has to reach for a global.
    single<LocalStore> {
        RoomLocalStore(
            getDatabaseBuilder(androidContext())
                .buildHelpMyCityDatabase(BundledSQLiteDriver(), Dispatchers.IO)
        )
    }
}

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<HelpMyCityDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath(HelpMyCityDatabase.FILE_NAME)
    return Room.databaseBuilder<HelpMyCityDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
}
