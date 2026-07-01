/*
 * Shared analytics scope control. A <select data-scope-select> navigates to its tab scoped to the
 * chosen flag, preserving the current module. One implementation for every analytics tab so the
 * flag control can't drift (replaces the per-page Load pill bar).
 */
(function () {
	document.querySelectorAll('select[data-scope-select]').forEach(function (sel) {
		sel.addEventListener('change', function () {
			var base = sel.getAttribute('data-base');
			var module = sel.getAttribute('data-module');
			var url = base + '?flag=' + encodeURIComponent(sel.value);
			if (module) { url += '&module=' + encodeURIComponent(module); }
			window.location = url;
		});
	});
})();
