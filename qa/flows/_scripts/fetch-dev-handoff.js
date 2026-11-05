// Maestro script: ask the local backend (wrangler dev) for the token of the newest handoff link sent to EMAIL
// (Field Log "Send to homeowner", POST /handoff). GET /dev/last-handoff exists only when the Worker runs with
// ENVIRONMENT=dev; on a deployed backend it returns 404 and output.handoffToken stays empty. The calling flow
// asserts the token is not empty, so a handoff that never reached the backend fails the flow.
var base = (typeof QA_BACKEND !== 'undefined' && QA_BACKEND) ? QA_BACKEND : 'http://127.0.0.1:8787';
output.handoffToken = '';
try {
  var r = http.get(base + '/dev/last-handoff?email=' + encodeURIComponent(EMAIL));
  if (r.ok) {
    var body = json(r.body);
    if (body && body.token) { output.handoffToken = String(body.token); }
  } else {
    console.log('dev handoff lookup answered ' + r.status);
  }
} catch (e) {
  console.log('dev handoff lookup failed: ' + e);
}
