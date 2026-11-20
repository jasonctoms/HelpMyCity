// Serve MapLibre's worker from this origin, and main.kt points maplibre-compose at it.
// The default is a blob: module worker, which WebKit will not start on a
// cross-origin-isolated page, so iOS Safari just drew no tiles. The worker ships
// inside maplibre-compose with the engine patches it needs and no sibling imports.
;(function (config) {
  const fs = require('fs')
  const worker = require.resolve('maplibre-compose/maplibre-gl/worker.mjs')

  config.plugins = config.plugins || []
  config.plugins.push({
    apply(compiler) {
      const { RawSource } = compiler.webpack.sources
      compiler.hooks.thisCompilation.tap('MapLibreWorker', (compilation) => {
        compilation.hooks.processAssets.tap(
          { name: 'MapLibreWorker', stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL },
          () => {
            compilation.emitAsset('maplibre-gl-worker.mjs', new RawSource(fs.readFileSync(worker)))
          },
        )
      })
    },
  })
})(config)
