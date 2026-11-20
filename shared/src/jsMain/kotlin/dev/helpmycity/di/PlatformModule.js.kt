package dev.helpmycity.di

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.helpmycity.data.local.LocalStore
import dev.helpmycity.data.local.database.HelpMyCityDatabase
import dev.helpmycity.data.local.database.RoomLocalStore
import dev.helpmycity.data.local.database.buildHelpMyCityDatabase
import dev.helpmycity.worker.createSqliteWasmWorkerDriver
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<LocalStore> {
        RoomLocalStore(
            getDatabaseBuilder().buildHelpMyCityDatabase(createSqliteWasmWorkerDriver())
        )
    }
}

/**
 * The database name is an OPFS path, not a filesystem path. The worker opens it
 * with SQLite's `OpfsDb`, so it persists across reloads just like it does on
 * Android and iOS.
 *
 * There is no query coroutine context set. The web driver is already async and
 * the browser is single-threaded, so Room's default is the right one here.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<HelpMyCityDatabase> =
    Room.databaseBuilder<HelpMyCityDatabase>(name = HelpMyCityDatabase.FILE_NAME)
