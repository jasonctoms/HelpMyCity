// Serve MapLibre's worker from this origin; main.kt points maplibre-compose at it.
// Its default, a jsDelivr URL, is wrapped in a blob: module worker, which WebKit
// will not start on a cross-origin-isolated page, so iOS Safari drew no tiles.
// The worker imports maplibre-gl-shared.mjs by relative path, so both ship.
;(function (config) {
  const fs = require('fs')
  const path = require('path')
  const dist = path.dirname(require.resolve('maplibre-gl/dist/maplibre-gl-worker.mjs'))
  const files = ['maplibre-gl-worker.mjs', 'maplibre-gl-shared.mjs']

  config.plugins = config.plugins || []
  config.plugins.push({
    apply(compiler) {
      const { RawSource } = compiler.webpack.sources
      compiler.hooks.thisCompilation.tap('MapLibreWorker', (compilation) => {
        compilation.hooks.processAssets.tap(
          { name: 'MapLibreWorker', stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL },
          () => {
            for (const file of files) {
              compilation.emitAsset(file, new RawSource(fs.readFileSync(path.join(dist, file))))
            }
          },
        )
      })
    },
  })
})(config)
