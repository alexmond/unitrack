/*
 * Live updates client (#146). Opens the /api/v1/events SSE stream, reflects connection state
 * in the topbar "● live" indicator, and re-broadcasts each run event as a DOM CustomEvent
 * ("unitrack:run") that page scripts (#147 cards, #148 runs row) listen for. EventSource
 * reconnects on its own; we just mirror open/error into the indicator.
 */
(function () {
    function dot() { return document.getElementById('live-dot'); }

    function setState(live) {
        var d = dot();
        if (!d) { return; }
        d.classList.toggle('on', live);
        d.setAttribute('title', live ? 'Live — connected' : 'Live — reconnecting…');
        d.setAttribute('aria-label', live ? 'Live updates connected' : 'Live updates reconnecting');
    }

    function connect() {
        if (typeof window.EventSource === 'undefined') { return; }
        var es = new EventSource('/api/v1/events');
        es.addEventListener('connected', function () { setState(true); });
        es.onopen = function () { setState(true); };
        es.onerror = function () { setState(false); }; // browser auto-reconnects; onopen re-fires
        es.addEventListener('run', function (ev) {
            var data;
            try { data = JSON.parse(ev.data); } catch (e) { return; }
            document.dispatchEvent(new CustomEvent('unitrack:run', { detail: data }));
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        setState(false);
        connect();
    });
})();
