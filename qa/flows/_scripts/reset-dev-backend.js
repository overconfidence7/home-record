// Maestro script: wipe the local backend (wrangler dev) so a flow starts with no accounts, companies or
// codes, and every Field Log flow can be rerun on the same backend. POST /dev/reset exists only when the
// Worker runs with ENVIRONMENT=dev; a flow calls this once, before its first sign-in, never between the
// accounts it signs in (F02's crew joins the owner's company). output.backendReset says whether it worked;
// the flow's own sign-in fails if the backend is not there.
var base = (typeof QA_BACKEND !== 'undefined' && QA_BACKEND) ? QA_BACKEND : 'http://127.0.0.1:8787';
output.backendReset = false;
try {
  var r = http.post(base + '/dev/reset', { headers: { 'Content-Type': 'application/json' }, body: '{}' });
  output.backendReset = r.ok;
  if (!r.ok) { console.log('dev reset answered ' + r.status); }
} catch (e) {
  console.log('dev reset failed: ' + e);
}
