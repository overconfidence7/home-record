// Maestro script: move the trial of the company EMAIL owns back DAYS days (default 15) on the local backend
// (wrangler dev), so its 14-day trial has ended (walkthrough F17, AC-F56). POST /dev/age-company exists only
// when the Worker runs with ENVIRONMENT=dev. output.companyAged says whether it worked; the calling flow then
// relaunches Field Log, which re-reads /me and must show the Owner FL-Paywall.
var base = (typeof QA_BACKEND !== 'undefined' && QA_BACKEND) ? QA_BACKEND : 'http://127.0.0.1:8787';
var days = (typeof DAYS !== 'undefined' && DAYS) ? parseInt(DAYS, 10) : 15;
output.companyAged = false;
try {
  var r = http.post(base + '/dev/age-company', {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, days: days }),
  });
  output.companyAged = r.ok;
  if (!r.ok) { console.log('dev age-company answered ' + r.status); }
} catch (e) {
  console.log('dev age-company failed: ' + e);
}
