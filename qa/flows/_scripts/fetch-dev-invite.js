// Maestro script: ask the local backend (wrangler dev) for the newest home invite link the owner OWNER_EMAIL made
// (AC-H16), so a flow can open it as the invited person without reading the share sheet. Only exists when the
// Worker runs with ENVIRONMENT=dev (PLAN Q005); otherwise output.inviteToken stays empty and the flow's assert fails.
var base = (typeof QA_BACKEND !== 'undefined' && QA_BACKEND) ? QA_BACKEND : 'http://127.0.0.1:8787';
output.inviteToken = '';
try {
  var r = http.get(base + '/dev/last-invite?email=' + encodeURIComponent(OWNER_EMAIL));
  if (r.ok) {
    var body = json(r.body);
    if (body && body.token) { output.inviteToken = String(body.token); }
  }
} catch (e) {
  console.log('dev invite lookup failed: ' + e);
}
