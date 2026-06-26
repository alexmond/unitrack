// k6 UI load test for UniTrack — hammers the render-heavy dashboard pages so a paired jvmlens
// monitor (or just k6's own percentiles) shows how they hold up under concurrency. The end-of-test
// summary (k6 run --summary-export=summary.json) feeds UniTrack's own perf-ingest (dogfood).
//
// Config (env): BASE_URL, UNITRACK_USER + UNITRACK_PASS (form login for closed mode),
//               PROJECT_ID, RUN_ID, VUS, DURATION.
// Run via ../perf/run-perf.sh (handles local-k6-or-container + the upload).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const USER = __ENV.UNITRACK_USER || '';
const PASS = __ENV.UNITRACK_PASS || '';
const PROJECT_ID = __ENV.PROJECT_ID || '';
const RUN_ID = __ENV.RUN_ID || '';

export const options = {
	vus: Number(__ENV.VUS || 5),
	duration: __ENV.DURATION || '30s',
	// Emit p(99) too — UniTrack's K6JsonParser reads it.
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
	thresholds: {
		http_req_failed: ['rate<0.05'],
		http_req_duration: ['p(95)<2000'],
	},
};

// Spring Security form login (closed mode). k6 keeps a per-VU cookie jar, so the JSESSIONID from
// here rides along on the page GETs below. CSRF is a hidden field Spring renders into the form.
function login() {
	const page = http.get(`${BASE}/login`);
	const m = page.body && page.body.match(/name="_csrf"[^>]*value="([^"]+)"/);
	const res = http.post(`${BASE}/login`, { username: USER, password: PASS, _csrf: m ? m[1] : '' });
	check(res, { 'login succeeded': (r) => !r.url.endsWith('/login?error') });
}

// module scope = per-VU in k6, so each VU logs in once and reuses its session across iterations.
let loggedIn = false;

export default function () {
	if (USER && !loggedIn) {
		login();
		loggedIn = true;
	}

	const pages = [
		['home', `${BASE}/`],
		['projects', `${BASE}/projects`],
	];
	if (PROJECT_ID) {
		pages.push(['project', `${BASE}/projects/${PROJECT_ID}`]);
		pages.push(['coverage', `${BASE}/projects/${PROJECT_ID}/coverage`]);
		pages.push(['performance', `${BASE}/projects/${PROJECT_ID}/performance`]);
		pages.push(['clusters', `${BASE}/projects/${PROJECT_ID}/clusters`]);
	}
	if (RUN_ID) {
		pages.push(['run', `${BASE}/runs/${RUN_ID}`]);
	}

	for (const [name, url] of pages) {
		const r = http.get(url);
		check(r, {
			[`${name} 200`]: (res) => res.status === 200,
			// a redirect back to /login means auth lapsed — fail loudly rather than load-test the login page
			[`${name} authed`]: (res) => !res.url.endsWith('/login'),
		});
	}
	sleep(1);
}

// Write the summary in the shape UniTrack's K6JsonParser reads (metrics.<name>.values.<stat>) —
// that's exactly handleSummary's `data` format, unlike the flat `--summary-export` schema.
export function handleSummary(data) {
	const out = __ENV.SUMMARY_OUT || 'summary.json';
	return {
		stdout: textSummary(data, { indent: ' ', enableColors: true }),
		[out]: JSON.stringify(data, null, 2),
	};
}
