// Resolve chunks and .wasm files relative to the page. The default ('auto') uses the
// directory of the last <script> on the page, which is wrong any time the host injects
// one of its own (and Cloudflare Web Analytics does).
;(function (config) {
  config.output = config.output || {}
  config.output.publicPath = ''
})(config)
