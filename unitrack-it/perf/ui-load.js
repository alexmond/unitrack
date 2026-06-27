// k6 UI load test for UniTrack — hammers the render-heavy dashboard pages so a paired jvmlens
// monitor (or just k6's own percentiles) shows how they hold up under concurrency. The end-of-test
// summary (handleSummary -> summary.json) feeds UniTrack's own perf-ingest (dogfood).
//
// Config (env): BASE_URL, UNITRACK_USER + UNITRACK_PASS (form login for closed mode),
//               PROJECT_ID, RUN_ID, VUS, DURATION.
// Run via ../perf/run-perf.sh (handles local-k6-or-container + the upload).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// Per-page latency (k6's default summary aggregates all URLs; these break it down so you can see
// WHICH page is slow). Declared in init context (required by k6); the second arg marks them as time.
const PAGE_DUR = {
	home: new Trend('page_home', true),
	project: new Trend('page_project', true),
	coverage: new Trend('page_coverage', true),
	performance: new Trend('page_performance', true),
	clusters: new Trend('page_clusters', true),
	run: new Trend('page_run', true),
};

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

// Log in ONCE (Spring Security form login: CSRF hidden field + creds) and hand the resulting
// JSESSIONID to every VU. This avoids paying bcrypt on every iteration AND survives k6 resetting
// its per-VU cookie jar between iterations (which otherwise drops the session -> pages 302 /login).
export function setup() {
	if (!USER) {
		return {};
	}
	const page = http.get(`${BASE}/login`);
	const m = page.body && page.body.match(/name="_csrf"[^>]*value="([^"]+)"/);
	const res = http.post(`${BASE}/login`, { username: USER, password: PASS, _csrf: m ? m[1] : '' });
	check(res, { 'login succeeded': (r) => !r.url.endsWith('/login?error') });
	const jar = http.cookieJar().cookiesForURL(`${BASE}/`);
	const sid = (jar.JSESSIONID && jar.JSESSIONID[0]) || null;
	if (USER && !sid) {
		console.warn('login yielded no JSESSIONID — pages will redirect to /login');
	}
	return { sid };
}

export default function (data) {
	// Re-assert the shared session at the top of each iteration (the jar is per-iteration in k6).
	if (data && data.sid) {
		http.cookieJar().set(BASE, 'JSESSIONID', data.sid);
	}

	const pages = [
		['home', `${BASE}/`], // the dashboard board (there is no /projects list route)
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
		const r = http.get(url, { tags: { page: name } });
		if (PAGE_DUR[name]) {
			PAGE_DUR[name].add(r.timings.duration);
		}
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
