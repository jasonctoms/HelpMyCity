// Resolve chunks and .wasm files relative to the page. The default, 'auto', takes the
// directory of the last <script> on the page, which is wrong whenever the host injects
// one of its own (Cloudflare Web Analytics does).
;(function (config) {
  config.output = config.output || {}
  config.output.publicPath = ''
})(config)
