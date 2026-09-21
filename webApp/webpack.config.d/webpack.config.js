// SQLite's WASM build stores the database in the Origin Private File System,
// which requires the page to be cross-origin isolated. Without these two
// headers the worker fails to open the database at startup.
//
// Whatever hosts the built app in production has to send the same two headers.
;(function (config) {
  config.devServer = config.devServer || {}
  config.devServer.headers = [
    { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
    { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
  ]
})(config)
