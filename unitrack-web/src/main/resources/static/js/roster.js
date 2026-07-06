/*
 * Shared analytics-table behaviour, so every analytics tab's tables behave identically:
 *
 *  - Sortable tables: any <table class="sortable"> — click a header to sort (numeric columns
 *    by value, via each cell's data-sort or its text); an aria-sort caret marks the column.
 *  - Breakdown drill: any <tr class="mod-row" data-href="…"> navigates on row click (links
 *    inside the row still work on their own).
 *  - Roster filter + collapse: an <input data-roster-search="<tbodyId>"> filters that tbody's
 *    rows by their data-search text. If rows carry data-attention, the default view shows only
 *    attention rows and an optional <button data-roster-toggle="<tbodyId>"> reveals the rest;
 *    searching always reaches every row. An optional [data-roster-empty="<tbodyId>"] shows when
 *    nothing matches.
 */
(function () {
	function num(v) { return parseFloat(String(v).replace(/[,%\s]|ms/g, '')); }

	function wireSortable(table) {
		if (!table.tHead || !table.tBodies.length) { return; }
		var headers = Array.prototype.slice.call(table.tHead.rows[0].cells);
		headers.forEach(function (th, col) {
			th.addEventListener('click', function () {
				var body = table.tBodies[0];
				var trs = Array.prototype.slice.call(body.rows);
				var asc = th.getAttribute('aria-sort') !== 'ascending';
				function key(tr) {
					var cell = tr.cells[col];
					if (!cell) { return ''; }
					var ds = cell.getAttribute('data-sort');
					return (ds != null) ? ds : cell.textContent.trim();
				}
				var numeric = trs.every(function (tr) {
					var k = key(tr);
					return k === '' || !isNaN(num(k));
				});
				trs.sort(function (a, b) {
					var ka = key(a), kb = key(b);
					if (numeric) { return asc ? (num(ka) || 0) - (num(kb) || 0) : (num(kb) || 0) - (num(ka) || 0); }
					return asc ? ka.localeCompare(kb) : kb.localeCompare(ka);
				});
				trs.forEach(function (tr) { body.appendChild(tr); });
				headers.forEach(function (h) { h.removeAttribute('aria-sort'); });
				th.setAttribute('aria-sort', asc ? 'ascending' : 'descending');
			});
		});
	}

	function wireRoster(box) {
		var bodyEl = document.getElementById(box.getAttribute('data-roster-search'));
		if (!bodyEl) { return; }
		var rows = Array.prototype.slice.call(bodyEl.querySelectorAll('tr'));
		var none = document.querySelector('[data-roster-empty="' + bodyEl.id + '"]');
		var toggle = document.querySelector('[data-roster-toggle="' + bodyEl.id + '"]');
		var hasAttention = rows.some(function (tr) { return tr.hasAttribute('data-attention'); });
		var hidden = rows.filter(function (tr) { return tr.getAttribute('data-attention') === '0'; });
		var total = rows.length;
		var showAll = false;

		function apply() {
			var q = box.value.trim().toLowerCase();
			var searching = q.length > 0;
			var shown = 0;
			rows.forEach(function (tr) {
				var match = !searching || (tr.getAttribute('data-search') || '').indexOf(q) !== -1;
				var att = !hasAttention || tr.getAttribute('data-attention') === '1';
				var vis = match && (searching || showAll || att);
				tr.style.display = vis ? '' : 'none';
				if (vis) { shown++; }
			});
			if (none) { none.style.display = shown === 0 ? '' : 'none'; }
			if (toggle) { toggle.style.display = (searching || hidden.length === 0) ? 'none' : ''; }
		}
		function syncToggle() {
			toggle.setAttribute('aria-expanded', showAll ? 'true' : 'false');
			var label = showAll
				? (toggle.getAttribute('data-collapse-label') || 'Show less')
				: (toggle.getAttribute('data-expand-label') || ('Show all ' + total));
			toggle.textContent = label.replace('{n}', total);
		}
		box.addEventListener('input', apply);
		if (toggle) {
			syncToggle();
			toggle.addEventListener('click', function () { showAll = !showAll; syncToggle(); apply(); });
		}
		apply();
	}

	document.querySelectorAll('table.sortable').forEach(wireSortable);
	document.querySelectorAll('tr.mod-row[data-href]').forEach(function (row) {
		row.addEventListener('click', function (e) {
			if (e.target.closest('a')) { return; }
			window.location = row.getAttribute('data-href');
		});
	});
	document.querySelectorAll('input[data-roster-search]').forEach(wireRoster);
})();
