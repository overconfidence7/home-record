// Maestro script: ask the local backend (wrangler dev) for the last sign-in code it issued to
// EMAIL. The endpoint exists only when the Worker runs with ENVIRONMENT=dev (PLAN Q005); on a
// deployed backend it returns 404 and output.devCode stays empty; every calling flow asserts the
// code is not empty, so a backend that never answered fails the flow instead of skipping sign-in.
var base = (typeof QA_BACKEND !== 'undefined' && QA_BACKEND) ? QA_BACKEND : 'http://127.0.0.1:8787';
output.devCode = '';
try {
  var r = http.get(base + '/dev/last-code?email=' + encodeURIComponent(EMAIL));
  if (r.ok) {
    var body = json(r.body);
    if (body && body.code) { output.devCode = String(body.code); }
  }
} catch (e) {
  console.log('dev code lookup failed: ' + e);
}
