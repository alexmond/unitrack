/*
 * Shared analytics scope control. The <select data-scope="flag|branch"> dropdowns in a .scope-bar
 * navigate to their tab scoped to the chosen flag+branch, preserving the current module and the
 * other dimension. One implementation for every analytics tab so the scope control can't drift.
 */
(function () {
	document.querySelectorAll('.scope-bar').forEach(function (bar) {
		var base = bar.getAttribute('data-base');
		var module = bar.getAttribute('data-module');
		function navigate() {
			var flag = bar.querySelector('select[data-scope="flag"]');
			var branch = bar.querySelector('select[data-scope="branch"]');
			var params = [];
			if (flag && flag.value) { params.push('flag=' + encodeURIComponent(flag.value)); }
			if (branch && branch.value) { params.push('branch=' + encodeURIComponent(branch.value)); }
			if (module) { params.push('module=' + encodeURIComponent(module)); }
			window.location = base + (params.length ? ('?' + params.join('&')) : '');
		}
		bar.querySelectorAll('select[data-scope]').forEach(function (sel) {
			sel.addEventListener('change', navigate);
		});
	});
})();
