/*
 * Shared analytics trend chart. One implementation for every analytics tab (Tests, Test
 * timing, …) so the chart behaviour can't drift between pages.
 *
 *   window.__trendChart(canvasId, toggleId, cfg)
 *
 * cfg = {
 *   labels:  ['abc123', …],          // x labels (short SHA) per run, oldest→newest
 *   runIds:  [1, 2, …],              // run id per point (for click-through)
 *   times:   [epochMs, …],           // timestamp per point (for the "Over time" axis)
 *   series:  [ { label, color, data:[…], axis:'y'|'y2' } … ],
 *   overlaySeries: <index>|null,     // series whose >0 values mark a "regressed" streak
 *   yTitle:  'tests',  y2Title: 'seconds',
 *   yMin: 0, yMax: null,
 *   compareBase: '/compare', runBase: '/runs'
 * }
 *
 * Point click → Compare (this run vs the previous); the first point → the run itself.
 * The time/run toggle (buttons with data-mode inside toggleId) flips the x scale.
 */
(function () {
	function fmtDate(ms) {
		return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
	}
	function fmtDateTime(ms) {
		return new Date(ms).toLocaleString(undefined,
			{ month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
	}

	window.__trendChart = function (canvasId, toggleId, cfg) {
		var canvas = document.getElementById(canvasId);
		if (!canvas || !window.Chart || !window.__chart) { return; }
		var labels = cfg.labels || [];
		var runIds = cfg.runIds || [];
		var times = cfg.times || [];
		var series = cfg.series || [];
		if (!labels.length || !series.length) { return; }

		var mode = 'time';
		function xAt(i) { return mode === 'time' ? times[i] : i; }
		function pts(arr) { return arr.map(function (v, i) { return { x: xAt(i), y: v }; }); }

		var overlay = (cfg.overlaySeries != null && series[cfg.overlaySeries]) ? series[cfg.overlaySeries].data : null;
		function isRed(i) { return overlay && overlay[i] > 0; }
		function trailingRedOnset() {
			if (!overlay) { return -1; }
			var n = overlay.length;
			if (n === 0 || !isRed(n - 1)) { return -1; }
			var i = n - 1;
			while (i - 1 >= 0 && isRed(i - 1)) { i--; }
			return i;
		}

		var compareBase = cfg.compareBase || '/compare';
		var runBase = cfg.runBase || '/runs';
		function openCompare(chart, evt) {
			var hit = chart.getElementsAtEventForMode(evt, 'nearest', { intersect: false }, true);
			if (!hit.length) { return; }
			var i = hit[0].index;
			var id = runIds[i];
			if (id == null) { return; }
			var prev = (i > 0) ? runIds[i - 1] : null;
			window.location = (prev != null) ? (compareBase + '?base=' + prev + '&head=' + id) : (runBase + '/' + id);
		}

		var css = getComputedStyle(document.documentElement);
		var muted = css.getPropertyValue('--muted').trim() || '#8b98a5';
		var text = css.getPropertyValue('--text').trim() || '#e6edf3';
		var grid = css.getPropertyValue('--border').trim() || '#2c343d';

		function xTick(v) {
			if (mode === 'time') { return fmtDate(v); }
			var i = Math.round(v);
			return (i >= 0 && i < labels.length) ? labels[i] : '';
		}
		function xBounds() {
			if (mode !== 'time') { return { min: undefined, max: undefined }; }
			var ts = times.filter(function (t) { return t != null; });
			if (!ts.length) { return { min: undefined, max: undefined }; }
			var t0 = Math.min.apply(null, ts), tN = Math.max.apply(null, ts), span = tN - t0;
			var pad = span > 0 ? Math.max(span * 0.02, 3600000) : 43200000;
			return { min: t0 - pad, max: tN + pad };
		}
		var initBounds = xBounds();

		var healthOverlay = {
			id: 'healthOverlay',
			beforeDatasetsDraw: function (chart) {
				var oi = trailingRedOnset();
				if (oi < 0) { return; }
				var ctx = chart.ctx, area = chart.chartArea, px = chart.scales.x.getPixelForValue(xAt(oi));
				ctx.save();
				ctx.fillStyle = 'rgba(229,83,75,0.08)';
				ctx.fillRect(px, area.top, area.right - px, area.bottom - area.top);
				ctx.restore();
			},
			afterDatasetsDraw: function (chart) {
				var oi = trailingRedOnset();
				if (oi < 0) { return; }
				var ctx = chart.ctx, area = chart.chartArea, px = chart.scales.x.getPixelForValue(xAt(oi));
				ctx.save();
				ctx.strokeStyle = '#e5534b'; ctx.lineWidth = 1.5; ctx.setLineDash([4, 3]);
				ctx.beginPath(); ctx.moveTo(px, area.top); ctx.lineTo(px, area.bottom); ctx.stroke();
				ctx.setLineDash([]);
				ctx.fillStyle = '#e5534b'; ctx.font = '11px sans-serif'; ctx.textAlign = 'left';
				ctx.fillText('regressed', Math.min(px + 4, area.right - 62), area.top + 12);
				ctx.restore();
			}
		};

		var usesY2 = series.some(function (s) { return s.axis === 'y2'; });
		var datasets = series.map(function (s) {
			return {
				label: s.label, data: pts(s.data), borderColor: s.color, backgroundColor: 'transparent',
				yAxisID: s.axis === 'y2' ? 'y2' : 'y', fill: false, tension: .25, spanGaps: true,
				pointRadius: 2, borderWidth: 2, borderDash: s.axis === 'y2' ? [5, 3] : []
			};
		});

		var scales = {
			y: {
				min: (cfg.yMin != null ? cfg.yMin : 0), max: cfg.yMax,
				ticks: { color: muted, precision: 0 }, grid: { color: grid },
				title: { display: !!cfg.yTitle, text: cfg.yTitle || '', color: muted }
			},
			x: {
				type: 'linear', offset: true, min: initBounds.min, max: initBounds.max,
				ticks: {
					color: muted, autoSkip: true, maxRotation: 0, maxTicksLimit: 8,
					callback: function (v) { return xTick(v); }
				},
				grid: { color: grid }
			}
		};
		if (usesY2) {
			scales.y2 = {
				position: 'right', min: 0, ticks: { color: muted, precision: 0 },
				grid: { drawOnChartArea: false },
				title: { display: !!cfg.y2Title, text: cfg.y2Title || '', color: muted }
			};
		}

		var chart = window.__chart(canvas, {
			type: 'line',
			plugins: overlay ? [healthOverlay] : [],
			data: { datasets: datasets },
			options: {
				onClick: function (e, els, c) { openCompare(c, e); },
				onHover: function (e, els) { e.native.target.style.cursor = els.length ? 'pointer' : 'default'; },
				parsing: false,
				scales: scales,
				plugins: {
					legend: { display: series.length > 1, labels: { color: text } },
					tooltip: {
						callbacks: {
							title: function (items) {
								var i = items[0].dataIndex, sha = labels[i];
								var when = (times[i] != null) ? fmtDateTime(times[i]) : null;
								if (!when) { return sha; }
								return (sha && sha !== '—') ? (sha + ' · ' + when) : when;
							}
						}
					}
				}
			}
		});

		function redraw() {
			chart.data.datasets.forEach(function (ds, idx) { ds.data = pts(series[idx].data); });
			var b = xBounds();
			chart.options.scales.x.min = b.min;
			chart.options.scales.x.max = b.max;
			chart.update();
		}
		var toggle = toggleId ? document.getElementById(toggleId) : null;
		if (toggle) {
			toggle.addEventListener('click', function (e) {
				var btn = e.target.closest('button[data-mode]');
				if (!btn) { return; }
				mode = btn.getAttribute('data-mode');
				toggle.querySelectorAll('button[data-mode]').forEach(function (b) {
					b.classList.toggle('active', b.getAttribute('data-mode') === mode);
				});
				redraw();
			});
		}
	};
})();
