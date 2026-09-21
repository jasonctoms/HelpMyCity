package dev.helpmycity.di

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.helpmycity.data.local.LocalStore
import dev.helpmycity.data.local.database.HelpMyCityDatabase
import dev.helpmycity.data.local.database.RoomLocalStore
import dev.helpmycity.data.local.database.buildHelpMyCityDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun platformModule(): Module = module {
    single<LocalStore> {
        RoomLocalStore(
            // Dispatchers.IO is JVM-only; Default is the Native equivalent thread pool.
            getDatabaseBuilder().buildHelpMyCityDatabase(BundledSQLiteDriver(), Dispatchers.Default)
        )
    }
}

fun getDatabaseBuilder(): RoomDatabase.Builder<HelpMyCityDatabase> =
    Room.databaseBuilder<HelpMyCityDatabase>(
        name = "${documentDirectory()}/${HelpMyCityDatabase.FILE_NAME}",
    )

/** Documents, not Caches: issues queued for sync must survive a low-storage purge. */
@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path) {
        "Could not resolve the iOS documents directory for the database."
    }
}
