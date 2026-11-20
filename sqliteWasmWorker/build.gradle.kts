/**
 * The web worker that gives Room a SQLite driver in the browser.
 *
 * `WebWorkerSQLiteDriver` talks to a worker over a small message protocol, but
 * androidx does not publish a worker that implements it. So `worker/worker.js` is
 * that implementation. It is backed by SQLite's official WASM build and keeps the
 * database in the Origin Private File System. It is packaged as a local npm
 * module so Kotlin's npm support hands it to webpack, which then resolves
 * `new URL("sqlite-wasm-worker/worker.js", import.meta.url)` into a real asset.
 *
 * It is adapted from the androidx reference sample at
 * https://github.com/danysantiago/room-web-demo.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // ES modules and not UMD, because the worker gets loaded through
    // `import.meta.url`, which only exists in an ES module.
    js {
        browser()
        useEsModules()
    }

    sourceSets {
        jsMain.dependencies {
            api(libs.sqlite.web)
            implementation(
                npm("sqlite-wasm-worker", layout.projectDirectory.dir("worker").asFile)
            )
        }
    }
}
