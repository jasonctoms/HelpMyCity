package dev.helpmycity.worker

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/**
 * Spawns the SQLite WASM worker and wraps it in a driver Room can use.
 *
 * Databases opened through this driver are stored in the Origin Private File
 * System, so they survive a page reload. OPFS needs a cross-origin-isolated
 * page -- see `webApp/webpack.config.d/webpack.config.js` for the dev server,
 * and set the same COOP/COEP headers wherever the app is hosted.
 *
 * No `{ type: "module" }` on the worker: webpack recognises the
 * `new Worker(new URL(...))` form and bundles worker.js -- imports and all --
 * into a classic worker script.
 */
fun createSqliteWasmWorkerDriver(): WebWorkerSQLiteDriver =
    WebWorkerSQLiteDriver(
        Worker(js("""new URL("sqlite-wasm-worker/worker.js", import.meta.url)"""))
    )
