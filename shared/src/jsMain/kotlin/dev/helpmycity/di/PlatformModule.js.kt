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
 * The database name is an OPFS path rather than a filesystem path -- the worker
 * opens it with SQLite's `OpfsDb`, so it persists across reloads like it does on
 * Android and iOS.
 *
 * No query coroutine context is set: the web driver is asynchronous already and
 * the browser is single-threaded, so Room's default is the right one here.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<HelpMyCityDatabase> =
    Room.databaseBuilder<HelpMyCityDatabase>(name = HelpMyCityDatabase.FILE_NAME)
